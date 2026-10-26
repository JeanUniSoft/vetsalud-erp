package pe.vetsalud.erp.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.CajaDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.ResumenTurno;
import pe.vetsalud.erp.modelo.TurnoCaja;
import pe.vetsalud.erp.seguridad.PoliticaAcceso;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * UC-12 Abrir y cerrar caja (RF-30, RN-10): un solo turno abierto por cajero, resumen por medio de pago,
 * devoluciones (ventas anuladas), efectivo esperado, contado y diferencia justificada.
 */
public class CajaService {

    public static final String PERMISO = "CAJA_CERRAR";
    private static final BigDecimal MAXIMO_FONDO = new BigDecimal("5000");

    private final CajaDao dao;

    public CajaService(CajaDao dao) {
        this.dao = Preconditions.checkNotNull(dao);
    }

    public Optional<TurnoCaja> turnoAbierto(UsuarioSesion u) throws SQLException {
        return dao.turnoAbierto(u.idUsuario());
    }

    /** El cajero solo consulta sus turnos; el administrador, todos. */
    public TurnoCaja obtener(int idTurno, UsuarioSesion u) throws SQLException {
        TurnoCaja t = dao.buscarPorId(idTurno).orElseThrow(() -> new ReglaNegocioException("El turno de caja no existe."));
        if (!esAdministrador(u) && t.idCajero() != u.idUsuario()) {
            throw new ReglaNegocioException("Solo puede consultar sus propios turnos de caja.");
        }
        return t;
    }

    public ResumenTurno resumen(int idTurno, UsuarioSesion u) throws SQLException {
        obtener(idTurno, u);
        return dao.resumen(idTurno);
    }

    public int abrir(BigDecimal montoInicial, UsuarioSesion u) throws SQLException {
        exigirPermiso(u);
        if (montoInicial == null) {
            throw new ReglaNegocioException("Indique el monto inicial (fondo de caja) con el que abre el turno.");
        }
        if (montoInicial.signum() < 0 || montoInicial.compareTo(MAXIMO_FONDO) > 0) {
            throw new ReglaNegocioException("El monto inicial debe estar entre S/ 0.00 y S/ 5,000.00.");
        }
        if (dao.turnoAbierto(u.idUsuario()).isPresent()) {
            throw new ReglaNegocioException("Ya tiene un turno de caja abierto: ciérrelo antes de abrir otro (RN-10).");
        }
        int id = dao.abrir(u.idUsuario(), montoInicial.setScale(2, RoundingMode.HALF_UP), u.idUsuario());
        Auditoria.registrar("CAJA_ABRIR", "caja_turno", "id=" + id + " inicial=" + montoInicial.toPlainString()
                + " por=" + u.usuario());
        return id;
    }

    /**
     * Cierra el turno con el efectivo contado. Si no coincide con el esperado exige observación (RN-10).
     *
     * @return diferencia (contado − esperado): positiva si sobra, negativa si falta
     */
    public BigDecimal cerrar(int idTurno, BigDecimal efectivoContado, String observacion, UsuarioSesion u)
            throws SQLException {
        exigirPermiso(u);
        TurnoCaja t = obtener(idTurno, u);
        if (!t.abierto()) {
            throw new ReglaNegocioException("El turno de caja ya está cerrado.");
        }
        if (efectivoContado == null || efectivoContado.signum() < 0) {
            throw new ReglaNegocioException("Indique el efectivo contado en caja (S/ 0.00 o más).");
        }
        ResumenTurno r = dao.resumen(idTurno);
        if (r.ventasPendientes() > 0) {
            throw new ReglaNegocioException("Hay " + r.ventasPendientes() + " venta(s) pendiente(s) en el turno: "
                    + "confírmelas o descártelas antes de cerrar la caja.");
        }
        BigDecimal contado = efectivoContado.setScale(2, RoundingMode.HALF_UP);
        String obs = StringUtils.normalizeSpace(StringUtils.trimToNull(observacion));
        Validaciones.longitudMaxima(obs, 300, "Observación");
        BigDecimal diferencia = contado.subtract(r.efectivoEsperado());
        if (diferencia.signum() != 0 && obs == null) {
            throw new ReglaNegocioException("El efectivo contado (S/ " + contado.toPlainString() + ") no coincide con el "
                    + "esperado (S/ " + r.efectivoEsperado().setScale(2, RoundingMode.HALF_UP).toPlainString()
                    + "): registre una observación que explique la diferencia de S/ "
                    + diferencia.abs().toPlainString() + " (RN-10).");
        }
        BigDecimal resultado = dao.cerrar(idTurno, contado, obs, u.idUsuario());
        Auditoria.registrar("CAJA_CERRAR", "caja_turno", "id=" + idTurno + " contado=" + contado.toPlainString()
                + " diferencia=" + resultado.toPlainString() + " por=" + u.usuario());
        return resultado;
    }

    public List<TurnoCaja> historial(LocalDate desde, LocalDate hasta, UsuarioSesion u) throws SQLException {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException("La fecha «desde» no puede ser posterior a «hasta».");
        }
        return dao.historial(esAdministrador(u) ? null : u.idUsuario(), desde, hasta, 200);
    }

    /** Turnos abiertos de todos los cajeros (supervisión del administrador). */
    public List<TurnoCaja> abiertos(UsuarioSesion u) throws SQLException {
        return esAdministrador(u) ? dao.abiertos() : List.of();
    }

    public static boolean esAdministrador(UsuarioSesion u) {
        return PoliticaAcceso.ADMINISTRADOR.equals(u.rol());
    }

    private static void exigirPermiso(UsuarioSesion u) {
        if (!u.puede(PERMISO)) {
            throw new ReglaNegocioException("Su rol no tiene el permiso " + PERMISO + " para abrir o cerrar caja.");
        }
    }
}
