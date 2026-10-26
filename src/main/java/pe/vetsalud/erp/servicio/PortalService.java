package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.PortalDao;
import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.modelo.PortalCita;
import pe.vetsalud.erp.modelo.PortalControl;
import pe.vetsalud.erp.modelo.Recordatorio;

/**
 * P11 · Portal del cliente (UC-15; RF-09 consulta de citas, RF-19 controles, RF-37 recordatorios).
 * El identificador de cliente SIEMPRE viene de la sesión autenticada (nunca de un parámetro de la URL) y
 * cada consulta del DAO filtra por él: un cliente solo ve sus mascotas. Información autorizada: datos básicos,
 * citas, controles y recordatorios; nunca anamnesis, diagnósticos, recetas ni alertas internas.
 */
public class PortalService {

    public static final int LIMITE_CITAS = 20;
    public static final int LIMITE_RECORDATORIOS = 20;

    private final PortalDao dao;
    private final Clock reloj;

    public PortalService(PortalDao dao) {
        this(dao, Clock.systemDefaultZone());
    }

    public PortalService(PortalDao dao, Clock reloj) {
        this.dao = Preconditions.checkNotNull(dao);
        this.reloj = Preconditions.checkNotNull(reloj);
    }

    public List<Mascota> mascotas(Integer idCliente) throws SQLException {
        return dao.mascotas(cliente(idCliente));
    }

    /** La mascota solo si pertenece al cliente de la sesión; vacío si no existe o es ajena. */
    public Optional<Mascota> mascota(Integer idCliente, int idMascota) throws SQLException {
        if (idCliente == null) {
            return Optional.empty();
        }
        return dao.mascota(idCliente, idMascota);
    }

    /** Citas de hoy en adelante (todas las mascotas del cliente o una sola). */
    public List<PortalCita> proximasCitas(Integer idCliente, Integer idMascota) throws SQLException {
        return dao.citasDesde(cliente(idCliente), idMascota, LocalDate.now(reloj), LIMITE_CITAS);
    }

    /** Controles programados de hoy en adelante, solo de consultas firmadas (información validada). */
    public List<PortalControl> controles(Integer idCliente, Integer idMascota) throws SQLException {
        return dao.controlesDesde(cliente(idCliente), idMascota, LocalDate.now(reloj));
    }

    public List<Recordatorio> recordatorios(Integer idCliente) throws SQLException {
        return dao.recordatorios(cliente(idCliente), LIMITE_RECORDATORIOS);
    }

    private static int cliente(Integer idCliente) {
        if (idCliente == null) {
            throw new ReglaNegocioException("Su cuenta no está vinculada a una ficha de cliente. "
                    + "Comuníquese con la clínica para activarla.");
        }
        return idCliente;
    }
}
