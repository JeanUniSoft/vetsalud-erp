package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.AgendaFiltro;
import pe.vetsalud.erp.modelo.CambioEstadoCita;
import pe.vetsalud.erp.modelo.Cita;
import pe.vetsalud.erp.modelo.CitaSolicitud;
import pe.vetsalud.erp.modelo.ControlProgramado;

/** Acceso a datos de la agenda (RF-09 a RF-12, RF-19). */
public interface CitaDao {

    /** Citas con inicio en [desde, hasta) que cumplen los filtros (profesional, servicio y estado). */
    List<Cita> agenda(LocalDateTime desde, LocalDateTime hasta, AgendaFiltro filtro) throws SQLException;

    Optional<Cita> buscarPorId(long idCita) throws SQLException;

    /**
     * Primera cita vigente (no cancelada ni «no asistió») del veterinario o de la mascota que se cruza con
     * [inicio, fin). {@code exceptoIdCita} excluye la propia cita al reprogramar.
     */
    Optional<Cita> cruce(Integer idVeterinario, Integer idMascota, LocalDateTime inicio, LocalDateTime fin,
                         Long exceptoIdCita) throws SQLException;

    long insertar(CitaSolicitud solicitud, LocalDateTime fin, int idUsuario) throws SQLException;

    /**
     * Cambia el estado solo si la cita sigue en {@code estadoActual} (control de concurrencia).
     * El trigger fn_cita_estado valida la transición y registra el historial con usuario y fecha.
     *
     * @param motivo texto que reemplaza cita.motivo (null: lo conserva)
     * @return false si la cita ya no estaba en {@code estadoActual}
     */
    boolean cambiarEstado(long idCita, String estadoActual, String estadoNuevo, String motivo, int idUsuario)
            throws SQLException;

    /**
     * Mueve la cita a otro horario o profesional y deja la constancia en cita_estado_hist (misma transacción).
     *
     * @return false si la cita ya no era reprogramable
     */
    boolean reprogramar(long idCita, LocalDateTime inicio, LocalDateTime fin, int idVeterinario, String estado,
                        String constancia, int idUsuario) throws SQLException;

    List<CambioEstadoCita> historial(long idCita) throws SQLException;

    /** Controles (consulta.fecha_control) entre dos fechas, opcionalmente de un veterinario (RF-19). */
    List<ControlProgramado> controles(LocalDate desde, LocalDate hasta, Integer idVeterinario) throws SQLException;
}
