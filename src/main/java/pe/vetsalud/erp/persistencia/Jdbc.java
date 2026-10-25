package pe.vetsalud.erp.persistencia;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utilidades JDBC: SIEMPRE con PreparedStatement y parámetros enlazados (prevención de inyección SQL).
 * Los tipos enumerados de PostgreSQL se envían como texto y se convierten en el SQL (?::estado_cita).
 */
public final class Jdbc {

    private Jdbc() {
    }

    public static <T> List<T> lista(Connection cn, String sql, Mapeador<T> mapeador, Object... params)
            throws SQLException {
        try (PreparedStatement ps = preparar(cn, sql, params); ResultSet rs = ps.executeQuery()) {
            List<T> filas = new ArrayList<>();
            while (rs.next()) {
                filas.add(mapeador.mapear(rs));
            }
            return filas;
        }
    }

    public static <T> Optional<T> uno(Connection cn, String sql, Mapeador<T> mapeador, Object... params)
            throws SQLException {
        try (PreparedStatement ps = preparar(cn, sql, params); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.ofNullable(mapeador.mapear(rs)) : Optional.empty();
        }
    }

    /** INSERT/UPDATE/DELETE; devuelve las filas afectadas. */
    public static int ejecutar(Connection cn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = preparar(cn, sql, params)) {
            return ps.executeUpdate();
        }
    }

    /** INSERT ... RETURNING id: devuelve la clave generada. */
    public static long insertar(Connection cn, String sqlConReturning, Object... params) throws SQLException {
        try (PreparedStatement ps = preparar(cn, sqlConReturning, params); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("La inserción no devolvió la clave generada");
            }
            return rs.getLong(1);
        }
    }

    /** Ejecuta una consulta que devuelve un único valor (COUNT, SUM, función). */
    public static <T> T escalar(Connection cn, String sql, Class<T> tipo, Object... params) throws SQLException {
        try (PreparedStatement ps = preparar(cn, sql, params); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getObject(1, tipo) : null;
        }
    }

    public static PreparedStatement preparar(Connection cn, String sql, Object... params) throws SQLException {
        PreparedStatement ps = cn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            enlazar(ps, i + 1, params[i]);
        }
        return ps;
    }

    private static void enlazar(PreparedStatement ps, int pos, Object valor) throws SQLException {
        if (valor == null) {
            ps.setNull(pos, Types.NULL);
        } else if (valor instanceof Enum<?> e) {
            ps.setString(pos, e.name());
        } else if (valor instanceof LocalDateTime ldt) {
            ps.setTimestamp(pos, Timestamp.valueOf(ldt));
        } else if (valor instanceof LocalDate || valor instanceof OffsetDateTime || valor instanceof BigDecimal) {
            ps.setObject(pos, valor);
        } else {
            ps.setObject(pos, valor);
        }
    }

    // ---- lectores que respetan NULL ----

    public static Integer entero(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    public static Long largo(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    public static LocalDate fecha(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDate.class);
    }

    /** Lee un TIMESTAMPTZ en la zona de la sesión de BD (America/Lima). */
    public static LocalDateTime fechaHora(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toLocalDateTime();
    }
}
