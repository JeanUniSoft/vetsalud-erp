package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import pe.vetsalud.erp.dao.RecordatorioDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.modelo.Recordatorio;
import pe.vetsalud.erp.modelo.RecordatorioResumen;
import pe.vetsalud.erp.notificacion.NotificadorCorreo;
import pe.vetsalud.erp.notificacion.NotificadorException;

/**
 * RF-37: recordatorios de citas y controles por correo, solo con consentimiento (cliente.acepta_recordatorios) y
 * dirección válida. Cada intento deja estado y fecha en la tabla y el destinatario enmascarado en la bitácora,
 * sin datos clínicos. El canal de envío es una interfaz ({@link NotificadorCorreo}): principios O y D de SOLID.
 *
 * <ul>
 *   <li>ENVIADO: se entregó al notificador (enviado_en = fecha del envío).</li>
 *   <li>FALLIDO: sin consentimiento, sin correo válido o el canal lo rechazó (enviado_en = fecha del intento).</li>
 *   <li>CANCELADO: el evento ya no aplica (cita cancelada/atendida/pasada, mascota no activa, cliente inactivo).</li>
 * </ul>
 */
public class RecordatorioService {

    public static final int LIMITE_LISTA = 100;
    public static final int LIMITE_LOTE = 200;
    public static final int HORAS_ANTES_CITA = 24;
    public static final LocalTime HORA_CONTROL = LocalTime.of(9, 0);

    private static final Logger LOG = LoggerFactory.getLogger(RecordatorioService.class);
    private static final Set<String> ESTADOS = ImmutableSet.of(Recordatorio.PENDIENTE, Recordatorio.ENVIADO,
            Recordatorio.FALLIDO, Recordatorio.CANCELADO);
    private static final Set<String> CITA_VIGENTE = ImmutableSet.of("PENDIENTE", "CONFIRMADA");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Resultado de evaluar un recordatorio antes de enviarlo. */
    public enum Decision {
        ENVIAR(Recordatorio.ENVIADO, "enviado"),
        SIN_CONSENTIMIENTO(Recordatorio.FALLIDO, "el cliente no autorizó recordatorios"),
        SIN_CORREO(Recordatorio.FALLIDO, "el cliente no tiene un correo válido"),
        CANAL_RECHAZO(Recordatorio.FALLIDO, "el canal de correo rechazó el envío"),
        CLIENTE_INACTIVO(Recordatorio.CANCELADO, "el cliente está inactivo"),
        MASCOTA_NO_ACTIVA(Recordatorio.CANCELADO, "la mascota ya no está activa"),
        CITA_NO_VIGENTE(Recordatorio.CANCELADO, "la cita ya no está pendiente ni confirmada"),
        EVENTO_PASADO(Recordatorio.CANCELADO, "la fecha del evento ya pasó");

        private final String estado;
        private final String motivo;

        Decision(String estado, String motivo) {
            this.estado = estado;
            this.motivo = motivo;
        }

        public String estado() {
            return estado;
        }

        public String motivo() {
            return motivo;
        }
    }

    /** Totales de una corrida de la cola. */
    public record Resultado(int enviados, int fallidos, int cancelados, int omitidos) {
        public int procesados() {
            return enviados + fallidos + cancelados;
        }

        public String mensaje() {
            if (procesados() == 0) {
                return "No había recordatorios por procesar.";
            }
            return "Cola procesada: " + enviados + " enviado(s), " + fallidos + " fallido(s) y " + cancelados
                    + " cancelado(s).";
        }
    }

    /** Recordatorios creados para citas y controles que aún no tenían uno. */
    public record Programacion(int citas, int controles) {
        public String mensaje() {
            return citas + controles == 0
                    ? "Todas las próximas citas y controles ya tenían su recordatorio."
                    : "Se programaron " + citas + " recordatorio(s) de cita y " + controles + " de control.";
        }
    }

    private final RecordatorioDao dao;
    private final NotificadorCorreo notificador;
    private final Clock reloj;

    public RecordatorioService(RecordatorioDao dao, NotificadorCorreo notificador) {
        this(dao, notificador, Clock.systemDefaultZone());
    }

    public RecordatorioService(RecordatorioDao dao, NotificadorCorreo notificador, Clock reloj) {
        this.dao = Preconditions.checkNotNull(dao);
        this.notificador = Preconditions.checkNotNull(notificador);
        this.reloj = Preconditions.checkNotNull(reloj);
    }

    public List<Recordatorio> listar(String estado) throws SQLException {
        String filtro = StringUtils.upperCase(StringUtils.trimToNull(estado));
        if (filtro != null && !ESTADOS.contains(filtro)) {
            throw new ReglaNegocioException("Estado de recordatorio no válido.");
        }
        return dao.listar(filtro, LIMITE_LISTA);
    }

    public RecordatorioResumen resumen() throws SQLException {
        return dao.resumen();
    }

    /** Crea los recordatorios que falten: citas 24 h antes y controles el día anterior a las 09:00. */
    public Programacion programar(Integer idUsuario, String usuario) throws SQLException {
        int citas = dao.programarCitas(HORAS_ANTES_CITA, idUsuario);
        int controles = dao.programarControles(HORA_CONTROL, idUsuario);
        if (citas + controles > 0) {
            Auditoria.registrar("RECORDATORIO_PROGRAMAR", "recordatorio",
                    "citas=" + citas + " controles=" + controles + " por=" + usuario);
        }
        return new Programacion(citas, controles);
    }

    /**
     * Procesa la cola: por defecto solo los recordatorios vencidos (programado_para &lt;= ahora); con
     * {@code incluirFuturos} se envía toda la cola pendiente ("procesar ahora").
     * Antes de enviar, el recordatorio se "reclama" pasando de PENDIENTE a ENVIADO con una actualización
     * condicional: si dos procesos corren a la vez, solo uno lo envía.
     */
    public Resultado procesarPendientes(boolean incluirFuturos, Integer idUsuario, String usuario) throws SQLException {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        int enviados = 0;
        int fallidos = 0;
        int cancelados = 0;
        int omitidos = 0;
        for (Recordatorio r : dao.pendientes(incluirFuturos, LIMITE_LOTE)) {
            Decision d = decidir(r, ahora);
            if (d != Decision.ENVIAR) {
                LocalDateTime fecha = Recordatorio.CANCELADO.equals(d.estado()) ? null : ahora;
                if (dao.cambiarEstado(r.idRecordatorio(), Recordatorio.PENDIENTE, d.estado(), fecha, idUsuario)) {
                    if (Recordatorio.FALLIDO.equals(d.estado())) {
                        fallidos++;
                    } else {
                        cancelados++;
                    }
                    registrar(r, d, usuario);
                } else {
                    omitidos++;
                }
                continue;
            }
            if (!dao.cambiarEstado(r.idRecordatorio(), Recordatorio.PENDIENTE, Recordatorio.ENVIADO, ahora, idUsuario)) {
                omitidos++; // otro proceso ya lo tomó
                continue;
            }
            try {
                notificador.enviar(redactar(r));
                enviados++;
                registrar(r, Decision.ENVIAR, usuario);
            } catch (NotificadorException | RuntimeException e) {
                LOG.warn("Falló el envío del recordatorio {}: {}", r.idRecordatorio(), e.getMessage());
                dao.cambiarEstado(r.idRecordatorio(), Recordatorio.ENVIADO, Recordatorio.FALLIDO, ahora, idUsuario);
                fallidos++;
                registrar(r, Decision.CANAL_RECHAZO, usuario);
            }
        }
        return new Resultado(enviados, fallidos, cancelados, omitidos);
    }

    /** Reglas de envío (RF-37): primero si el evento sigue vigente, luego consentimiento y correo. */
    Decision decidir(Recordatorio r, LocalDateTime ahora) {
        if (!Mascota.ACTIVA.equals(r.estadoMascota())) {
            return Decision.MASCOTA_NO_ACTIVA;
        }
        if (r.esCita() && !CITA_VIGENTE.contains(r.estadoCita())) {
            return Decision.CITA_NO_VIGENTE;
        }
        LocalDateTime evento = r.fechaEvento();
        boolean pasado = r.esCita()
                ? evento == null || evento.isBefore(ahora)
                : evento == null || evento.toLocalDate().isBefore(ahora.toLocalDate());
        if (pasado) {
            return Decision.EVENTO_PASADO;
        }
        if (!r.clienteActivo()) {
            return Decision.CLIENTE_INACTIVO;
        }
        if (!r.aceptaRecordatorios()) {
            return Decision.SIN_CONSENTIMIENTO;
        }
        if (!correoValido(r.correo())) {
            return Decision.SIN_CORREO;
        }
        return Decision.ENVIAR;
    }

    /** Texto genérico: fecha, hora y nombre de la mascota. Nunca motivo, servicio clínico, diagnóstico ni receta. */
    NotificadorCorreo.Mensaje redactar(Recordatorio r) {
        String asunto;
        String texto;
        if (r.esCita()) {
            asunto = "Recordatorio de cita · VetSalud";
            texto = "Estimado(a) cliente: le recordamos que " + r.mascota() + " tiene una cita en la Clínica "
                    + "Veterinaria VetSalud Integral el " + r.inicioCita().format(FECHA) + " a las "
                    + r.inicioCita().format(HORA) + ". Si no podrá asistir, comuníquese con nosotros para reprogramarla.";
        } else {
            asunto = "Recordatorio de control · VetSalud";
            texto = "Estimado(a) cliente: le recordamos que " + r.mascota() + " tiene un control programado en la "
                    + "Clínica Veterinaria VetSalud Integral el " + r.fechaControl().format(FECHA)
                    + ". Comuníquese con nosotros para confirmar la hora de atención.";
        }
        return new NotificadorCorreo.Mensaje(r.correo(), asunto, texto);
    }

    private static boolean correoValido(String correo) {
        if (StringUtils.isBlank(correo)) {
            return false;
        }
        try {
            Validaciones.correo(correo.trim());
            return true;
        } catch (ReglaNegocioException e) {
            return false;
        }
    }

    private static void registrar(Recordatorio r, Decision d, String usuario) {
        Auditoria.registrar("RECORDATORIO_" + d.estado(), "recordatorio", "id=" + r.idRecordatorio()
                + " destinatario=" + r.destinatarioEnmascarado() + " motivo=\"" + d.motivo() + "\" por=" + usuario);
    }
}
