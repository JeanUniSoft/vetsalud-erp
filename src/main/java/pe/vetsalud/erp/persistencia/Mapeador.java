package pe.vetsalud.erp.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Convierte la fila actual de un ResultSet en un objeto del modelo. */
@FunctionalInterface
public interface Mapeador<T> {
    T mapear(ResultSet rs) throws SQLException;
}
