package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;

import pe.vetsalud.erp.dao.CitaDao;
import pe.vetsalud.erp.dao.MascotaClinicaDao;
import pe.vetsalud.erp.dao.ProfesionalDao;
import pe.vetsalud.erp.dao.ServicioDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.AgendaDia;
import pe.vetsalud.erp.modelo.AgendaFiltro;
import pe.vetsalud.erp.modelo.AgendaVista;
import pe.vetsalud.erp.modelo.CambioEstadoCita;
import pe.vetsalud.erp.modelo.Cita;
import pe.vetsalud.erp.modelo.CitaSolicitud;
import pe.vetsalud.erp.modelo.MascotaClinica;
import pe.vetsalud.erp.modelo.Profesional;
import pe.vetsalud.erp.modelo.Servicio;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * UC-05 Gestionar la agenda (RF-09 a RF-12; RN-03 sin solapamientos).
 * Reglas: horario de atención, duración por servicio, profesional veterinario activo, mascota activa,
 * transiciones de estado válidas y motivo obligatorio para cancelar o reprogramar.
 * La BD vuelve a validar (ex_cita_sin_solape, trigger fn_cita_estado): defensa en profundidad.
 */
public class CitaService {

    public static final LocalTime APERTURA = LocalTime.of(8, 0);
    public static final LocalTime CIERRE = LocalTime.of(20, 0);
    /** Margen para registrar en el turno en curso a quien llega sin cita previa. */
    public static final int TOLERANCIA_MIN = 15;
    public static final int MESES_ANTICIPACION = 12;
    public static final int LIMITE_BUSQUEDA = 30;
    public static final int MAX_MOTIVO = 200;
    public static final int MAX_MOTIVO_CAMBIO = 100;
    public static final String PERMISO = "CITA_GESTIONAR";

    public static final List<String> ESTADOS = ImmutableList.of("PENDIENTE", "CONFIRMADA", "EN_ESPERA",
            "EN_ATENCION", "ATENDIDA", "CANCELADA", "NO_ASISTIO");

    /** RF-12: las mismas transiciones que valida el trigger fn_cita_estado (03_logica.sql). */
    private static final ImmutableSetMultimap<String, String> TRANSICIONES =
            ImmutableSetMultimap.<String, String>builder()
                    .putAll("PENDIENTE", "CONFIRMADA", "CANCELADA")
                    .putAll("CONFIRMADA", "EN_ESPERA", "NO_ASISTIO", "CANCELADA")
                    .putAll("EN_ESPERA", "EN_ATENCION", "CANCELADA")
                    .put("EN_ATENCION", "ATENDIDA")
                    .build();

    /** Estados finales que exigen motivo (RF-10). */
    public static final Set<String> CON_MOTIVO = ImmutableSet.of("CANCELADA", "NO_ASISTIO");

    /** Texto del botón que lleva a cada estado. */
    public static final Map<String, String> ACCIONES = ImmutableMap.<String, String>builder()
            .put("CONFIRMADA", "Confirmar").put("EN_ESPERA", "Registrar llegada")
            .put("EN_ATENCION", "Pasar a atención").put("ATENDIDA", "Marcar atendida")
            .put("NO_ASISTIO", "No asistió").put("CANCELADA", "Cancelar").build();

    private static final Map<String, String> NOMBRES = ImmutableMap.<String, String>builder()
            .put("PENDIENTE", "Pendiente").put("CONFIRMADA", "Confirmada").put("EN_ESPERA", "En espera")
            .put("EN_ATENCION", "En atención").put("ATENDIDA", "Atendida").put("CANCELADA", "Cancelada")
            .put("NO_ASISTIO", "No asistió").build();

    private static final String[] DIAS = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final CitaDao dao;
    private final ServicioDao servicioDao;
    private final ProfesionalDao profesionalDao;
    private final MascotaClinicaDao mascotaDao;
    private final Clock reloj;

    public CitaService(CitaDao dao, ServicioDao servicioDao, ProfesionalDao profesionalDao,
                       MascotaClinicaDao mascotaDao, Clock reloj) {
        this.dao = Preconditions.checkNotNull(dao);
        this.servicioDao = Preconditions.checkNotNull(servicioDao);
        this.profesionalDao = Preconditions.checkNotNull(profesionalDao);
        this.mascotaDao = Preconditions.checkNotNull(mascotaDao);
        this.reloj = Preconditions.checkNotNull(reloj);
    }

    public CitaService(CitaDao dao, ServicioDao servicioDao, ProfesionalDao profesionalDao, MascotaClinicaDao mascotaDao) {
        this(dao, servicioDao, profesionalDao, mascotaDao, Clock.systemDefaultZone());
    }

    // ------------------------------------------------------------------ reglas de estado (RF-12)

    /** Estados a los que puede pasar una cita desde {@code estado} (vacío si es final). */
    public static Set<String> transicionesDesde(String estado) {
        return TRANSICIONES.get(StringUtils.defaultString(estado));
    }

    public static boolean esTransicionValida(String actual, String nuevo) {
        return transicionesDesde(actual).contains(nuevo);
    }

    public static String nombreEstado(String estado) {
        return NOMBRES.getOrDefault(estado, estado);
    }

    // ------------------------------------------------------------------ agenda (RF-11, RF-19)

    /** El veterinario abre su propia agenda; recepción y administración ven la de toda la clínica. */
    public Integer veterinarioPorDefecto(UsuarioSesion usuario) {
        return Profesional.VETERINARIO.equals(usuario.rol()) ? usuario.idUsuario() : null;
    }

    public AgendaVista agenda(AgendaFiltro entrada) throws SQLException {
        AgendaFiltro f = normalizar(entrada);
        LocalDate desde = f.semanal() ? f.fecha().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : f.fecha();
        int dias = f.semanal() ? 7 : 1;
        LocalDate hasta = desde.plusDays(dias - 1L);

        List<Cita> citas = dao.agenda(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay(), f).stream()
                .map(this::conTransiciones).toList();

        ImmutableListMultimap<LocalDate, Cita> porDia = Multimaps.index(citas, c -> c.inicio().toLocalDate());
        LocalDate hoy = LocalDate.now(reloj);
        List<AgendaDia> columnas = new ArrayList<>();
        for (int i = 0; i < dias; i++) {
            LocalDate d = desde.plusDays(i);
            columnas.add(new AgendaDia(d, DIAS[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth(),
                    d.equals(hoy), porDia.get(d)));
        }

        Map<String, Long> conteo = new LinkedHashMap<>();
        for (String estado : ESTADOS) {
            long n = citas.stream().filter(c -> c.estado().equals(estado)).count();
            if (n > 0) {
                conteo.put(estado, n);
            }
        }
        return new AgendaVista(f, desde, hasta, desde.minusDays(dias), desde.plusDays(dias), citas, columnas,
                dao.controles(desde, hasta, f.idVeterinario()), conteo);
    }

    /** Fecha por defecto hoy, vista diaria y estado solo si es uno de los válidos. */
    AgendaFiltro normalizar(AgendaFiltro f) {
        LocalDate fecha = f.fecha() == null ? LocalDate.now(reloj) : f.fecha();
        String vista = AgendaFiltro.SEMANA.equals(f.vista()) ? AgendaFiltro.SEMANA : AgendaFiltro.DIA;
        String estado = ESTADOS.contains(f.estado()) ? f.estado() : null;
        return new AgendaFiltro(fecha, vista, f.idVeterinario(), f.idServicio(), estado);
    }

    /** Citas vigentes del día de todos los profesionales: ayuda a elegir un horario libre (RF-09). */
    public List<Cita> ocupacion(LocalDate dia) throws SQLException {
        LocalDate d = dia == null ? LocalDate.now(reloj) : dia;
        return dao.agenda(d.atStartOfDay(), d.plusDays(1).atStartOfDay(), new AgendaFiltro(d, AgendaFiltro.DIA, null, null, null))
                .stream().filter(c -> !c.liberada())
                .sorted(Comparator.comparing(Cita::veterinario).thenComparing(Cita::inicio)).toList();
    }

    public Cita obtener(long idCita) throws SQLException {
        return dao.buscarPorId(idCita).map(this::conTransiciones)
                .orElseThrow(() -> new ReglaNegocioException("La cita N.° " + idCita + " no existe."));
    }

    public List<CambioEstadoCita> historial(long idCita) throws SQLException {
        return dao.historial(idCita);
    }

    public List<Servicio> servicios() throws SQLException {
        return servicioDao.activos();
    }

    public List<Profesional> veterinarios() throws SQLException {
        return profesionalDao.veterinarios();
    }

    public List<MascotaClinica> buscarMascotas(String texto) throws SQLException {
        return mascotaDao.buscar(StringUtils.trimToNull(texto), LIMITE_BUSQUEDA);
    }

    public MascotaClinica mascota(int idMascota) throws SQLException {
        return mascotaDao.buscarPorId(idMascota)
                .orElseThrow(() -> new ReglaNegocioException("La mascota seleccionada no existe."));
    }

    // ------------------------------------------------------------------ programar (RF-09)

    public Cita programar(CitaSolicitud s, UsuarioSesion u) throws SQLException {
        exigirGestion(u);
        if (s.idMascota() == null) {
            throw new ReglaNegocioException("Seleccione la mascota.");
        }
        MascotaClinica mascota = mascota(s.idMascota());
        if (!mascota.activa()) {
            throw new ReglaNegocioException("La mascota «" + mascota.nombre() + "» no está activa; no se le pueden programar citas.");
        }
        Servicio servicio = servicioActivo(s.idServicio());
        Profesional vet = veterinario(s.idVeterinario());
        LocalDateTime inicio = validarInicio(s.inicio());
        LocalDateTime fin = calcularFin(inicio, servicio);
        validarHorario(inicio, fin, servicio);
        String motivo = StringUtils.trimToNull(s.motivo());
        Validaciones.longitudMaxima(motivo, MAX_MOTIVO, "Motivo");
        verificarDisponibilidad(vet, mascota.idMascota(), mascota.nombre(), inicio, fin, null);

        long id = dao.insertar(new CitaSolicitud(mascota.idMascota(), servicio.idServicio(), vet.idUsuario(), inicio, motivo),
                fin, u.idUsuario());
        Auditoria.registrar("CITA_PROGRAMAR", "cita", "id=" + id + " mascota=" + mascota.idMascota()
                + " vet=" + vet.idUsuario() + " inicio=" + inicio + " por=" + u.usuario());
        return obtener(id);
    }

    /** RF-09: la duración de la cita es la del servicio. */
    public static LocalDateTime calcularFin(LocalDateTime inicio, Servicio servicio) {
        return inicio.plusMinutes(servicio.duracionMin());
    }

    LocalDateTime validarInicio(LocalDateTime inicio) {
        if (inicio == null) {
            throw new ReglaNegocioException("Indique la fecha y la hora de la cita.");
        }
        LocalDateTime i = inicio.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (i.isBefore(ahora.minusMinutes(TOLERANCIA_MIN))) {
            throw new ReglaNegocioException("No se puede programar una cita en el pasado (" + i.format(FECHA_HORA)
                    + "). Elija una fecha y hora a partir de ahora.");
        }
        if (i.isAfter(ahora.plusMonths(MESES_ANTICIPACION))) {
            throw new ReglaNegocioException("La agenda admite citas con hasta " + MESES_ANTICIPACION + " meses de anticipación.");
        }
        return i;
    }

    static void validarHorario(LocalDateTime inicio, LocalDateTime fin, Servicio servicio) {
        boolean dentro = !inicio.toLocalTime().isBefore(APERTURA) && fin.toLocalDate().equals(inicio.toLocalDate())
                && !fin.toLocalTime().isAfter(CIERRE);
        if (!dentro) {
            throw new ReglaNegocioException("La cita debe quedar dentro del horario de atención (" + APERTURA.format(HORA)
                    + " a " + CIERRE.format(HORA) + "): «" + servicio.nombre() + "» dura " + servicio.duracionMin()
                    + " min y empezaría a las " + inicio.format(HORA) + ".");
        }
    }

    private void verificarDisponibilidad(Profesional vet, int idMascota, String nombreMascota, LocalDateTime inicio,
                                         LocalDateTime fin, Long exceptoIdCita) throws SQLException {
        Optional<Cita> delVet = dao.cruce(vet.idUsuario(), null, inicio, fin, exceptoIdCita);
        if (delVet.isPresent()) {
            Cita c = delVet.get();
            throw new ReglaNegocioException(vet.etiqueta() + " ya tiene una cita de " + c.inicio().format(HORA) + " a "
                    + c.fin().format(HORA) + " (" + c.mascota() + "). Elija otro horario (RN-03).");
        }
        Optional<Cita> deMascota = dao.cruce(null, idMascota, inicio, fin, exceptoIdCita);
        if (deMascota.isPresent()) {
            Cita c = deMascota.get();
            throw new ReglaNegocioException(nombreMascota + " ya tiene una cita de " + c.inicio().format(HORA) + " a "
                    + c.fin().format(HORA) + " con " + c.veterinario() + ".");
        }
    }

    // ------------------------------------------------------------------ reprogramar y cambiar estado (RF-10, RF-12)

    public Cita reprogramar(long idCita, LocalDateTime nuevoInicio, Integer idVeterinario, String motivo, UsuarioSesion u)
            throws SQLException {
        exigirGestion(u);
        Cita c = obtener(idCita);
        if (!c.reprogramable()) {
            throw new ReglaNegocioException("Solo se reprograma una cita pendiente o confirmada; esta cita está «"
                    + nombreEstado(c.estado()) + "» (RF-10).");
        }
        String m = Validaciones.requerido(motivo, "Motivo de la reprogramación");
        Validaciones.longitudMaxima(m, MAX_MOTIVO_CAMBIO, "Motivo de la reprogramación");
        Servicio servicio = servicioDao.buscarPorId(c.idServicio())
                .orElseThrow(() -> new ReglaNegocioException("El servicio de la cita ya no existe."));
        Profesional vet = veterinario(idVeterinario == null ? c.idVeterinario() : idVeterinario);
        LocalDateTime inicio = validarInicio(nuevoInicio);
        LocalDateTime fin = calcularFin(inicio, servicio);
        validarHorario(inicio, fin, servicio);
        if (inicio.equals(c.inicio()) && vet.idUsuario() == c.idVeterinario()) {
            throw new ReglaNegocioException("La nueva fecha, hora y profesional son iguales a los actuales.");
        }
        verificarDisponibilidad(vet, c.idMascota(), c.mascota(), inicio, fin, idCita);

        String constancia = StringUtils.abbreviate("Reprogramada del " + c.inicio().format(FECHA_HORA) + " al "
                + inicio.format(FECHA_HORA) + (vet.idUsuario() != c.idVeterinario() ? " con " + vet.etiqueta() : "")
                + ". Motivo: " + m, MAX_MOTIVO);
        if (!dao.reprogramar(idCita, inicio, fin, vet.idUsuario(), c.estado(), constancia, u.idUsuario())) {
            throw new ReglaNegocioException("La cita cambió de estado mientras tanto; recargue la página.");
        }
        Auditoria.registrar("CITA_REPROGRAMAR", "cita", "id=" + idCita + " de=" + c.inicio() + " a=" + inicio
                + " vet=" + vet.idUsuario() + " por=" + u.usuario());
        return obtener(idCita);
    }

    public Cita cambiarEstado(long idCita, String nuevo, String motivo, UsuarioSesion u) throws SQLException {
        exigirGestion(u);
        Cita c = obtener(idCita);
        if (!esTransicionValida(c.estado(), nuevo)) {
            Set<String> posibles = transicionesDesde(c.estado());
            throw new ReglaNegocioException("Transición no permitida: «" + nombreEstado(c.estado()) + "» → «"
                    + nombreEstado(nuevo) + "». " + (posibles.isEmpty() ? "La cita ya está cerrada."
                    : "Desde este estado solo puede pasar a: " + String.join(", ", posibles.stream().map(CitaService::nombreEstado).toList()) + ".")
                    + " (RF-12)");
        }
        String motivoFinal = null;
        if (CON_MOTIVO.contains(nuevo)) {
            String m = Validaciones.requerido(motivo, "Motivo");
            Validaciones.longitudMaxima(m, MAX_MOTIVO_CAMBIO, "Motivo");
            motivoFinal = anotarMotivo(c.motivo(), nombreEstado(nuevo) + ": " + m);
        }
        if ("ATENDIDA".equals(nuevo) && "ABIERTA".equals(c.estadoConsulta())) {
            throw new ReglaNegocioException("La cita tiene la consulta N.° " + c.idConsulta()
                    + " abierta: pasará a «Atendida» automáticamente al firmarla (RF-18).");
        }
        if (!dao.cambiarEstado(idCita, c.estado(), nuevo, motivoFinal, u.idUsuario())) {
            throw new ReglaNegocioException("La cita cambió de estado mientras tanto; recargue la página.");
        }
        Auditoria.registrar("CITA_ESTADO", "cita", "id=" + idCita + " de=" + c.estado() + " a=" + nuevo + " por=" + u.usuario());
        return obtener(idCita);
    }

    /** Agrega la nota de cancelación al motivo sin superar los 200 caracteres de cita.motivo (la nota se conserva entera). */
    static String anotarMotivo(String original, String nota) {
        String o = StringUtils.trimToNull(original);
        if (o == null) {
            return StringUtils.abbreviate(nota, MAX_MOTIVO);
        }
        String separador = " · ";
        int espacio = MAX_MOTIVO - nota.length() - separador.length();
        return espacio < 10 ? StringUtils.abbreviate(nota, MAX_MOTIVO)
                : StringUtils.abbreviate(o, espacio) + separador + nota;
    }

    // ------------------------------------------------------------------ apoyo

    /** Acciones que ofrece la vista: «Atendida» se omite si la consulta sigue abierta (se marca al firmarla). */
    private Cita conTransiciones(Cita c) {
        return c.conTransiciones(transicionesDesde(c.estado()).stream()
                .filter(t -> !("ATENDIDA".equals(t) && "ABIERTA".equals(c.estadoConsulta()))).toList());
    }

    private static void exigirGestion(UsuarioSesion u) {
        if (!u.puede(PERMISO)) {
            throw new ReglaNegocioException("Su rol no tiene el permiso " + PERMISO + " para modificar la agenda.");
        }
    }

    private Servicio servicioActivo(Integer idServicio) throws SQLException {
        if (idServicio == null) {
            throw new ReglaNegocioException("Seleccione el servicio.");
        }
        return servicioDao.buscarPorId(idServicio).filter(Servicio::activo)
                .orElseThrow(() -> new ReglaNegocioException("El servicio seleccionado no existe o no está activo."));
    }

    private Profesional veterinario(Integer idVeterinario) throws SQLException {
        if (idVeterinario == null) {
            throw new ReglaNegocioException("Seleccione el profesional que atenderá la cita.");
        }
        return profesionalDao.buscarPorId(idVeterinario).filter(p -> p.esVeterinario() && p.activo())
                .orElseThrow(() -> new ReglaNegocioException("El profesional debe ser un médico veterinario activo (RF-09)."));
    }
}
