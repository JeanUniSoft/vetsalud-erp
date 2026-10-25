package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.Parametro;

/** Parámetros configurables del sistema (tabla parametro, RF-24). */
public interface ParametroDao {

    Optional<String> valor(String clave) throws SQLException;

    List<Parametro> todos() throws SQLException;

    void actualizar(String clave, String valor, int idUsuario) throws SQLException;
}
