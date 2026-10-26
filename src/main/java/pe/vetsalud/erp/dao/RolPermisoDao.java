package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;

import com.google.common.collect.SetMultimap;

import pe.vetsalud.erp.modelo.PermisoFila;
import pe.vetsalud.erp.modelo.RolResumen;

/** Matriz de permisos por rol (RF-02): tablas rol, permiso y rol_permiso. */
public interface RolPermisoDao {

    List<RolResumen> roles() throws SQLException;

    /** Permisos ordenados por módulo y código, con los roles que tienen cada uno. */
    List<PermisoFila> matriz() throws SQLException;

    /**
     * Aplica la diferencia en UNA transacción: inserta {@code agregar} y borra {@code quitar}
     * (claves: nombre del rol → códigos de permiso).
     */
    void aplicar(SetMultimap<String, String> agregar, SetMultimap<String, String> quitar, int idActor)
            throws SQLException;
}
