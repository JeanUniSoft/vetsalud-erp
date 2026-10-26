package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;

import pe.vetsalud.erp.modelo.Alerta;

/** Alertas internas por rol (RF-38). */
public interface AlertaDao {

    void crear(String rolDestino, String tipo, String mensaje) throws SQLException;

    List<Alerta> pendientesPorRol(String rol) throws SQLException;

    /** Últimas alertas (pendientes y atendidas) para el administrador. */
    List<Alerta> recientes(int limite) throws SQLException;

    /** Marca la alerta como atendida si va dirigida al rol (o si es administrador). */
    boolean atender(long idAlerta, int idUsuario, String rol) throws SQLException;
}
