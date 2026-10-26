package pe.vetsalud.erp.persistencia;

import java.sql.Connection;
import java.sql.SQLException;

/** Bloque de trabajo que recibe una conexión abierta (dentro o fuera de una transacción). */
@FunctionalInterface
public interface FuncionSql<T> {
    T aplicar(Connection cn) throws SQLException;
}
