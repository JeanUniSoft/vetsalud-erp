package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.Servicio;

/** Catálogo de servicios de la clínica (duración y tarifa). */
public interface ServicioDao {

    List<Servicio> activos() throws SQLException;

    Optional<Servicio> buscarPorId(int idServicio) throws SQLException;
}
