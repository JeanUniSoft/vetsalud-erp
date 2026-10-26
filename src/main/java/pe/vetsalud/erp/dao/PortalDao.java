package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.modelo.PortalCita;
import pe.vetsalud.erp.modelo.PortalControl;
import pe.vetsalud.erp.modelo.Recordatorio;

/**
 * Consultas del portal del cliente (P11). TODAS filtran por el cliente propietario en el SQL:
 * el cliente nunca obtiene datos de mascotas ajenas aunque manipule la URL.
 */
public interface PortalDao {

    List<Mascota> mascotas(int idCliente) throws SQLException;

    /** La mascota solo si pertenece al cliente; vacío en otro caso. */
    Optional<Mascota> mascota(int idCliente, int idMascota) throws SQLException;

    /** Citas desde el día indicado (todas las mascotas del cliente o solo una). */
    List<PortalCita> citasDesde(int idCliente, Integer idMascota, LocalDate desde, int limite) throws SQLException;

    /** Controles de consultas firmadas con fecha_control desde el día indicado. */
    List<PortalControl> controlesDesde(int idCliente, Integer idMascota, LocalDate desde) throws SQLException;

    List<Recordatorio> recordatorios(int idCliente, int limite) throws SQLException;
}
