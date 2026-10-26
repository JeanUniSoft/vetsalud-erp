package pe.vetsalud.erp.persistencia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.config.ConexionBD;

/**
 * Punto único de acceso a la base de datos (patrón DAO + Unidad de Trabajo).
 * <ul>
 *   <li>{@link #consultar}: lecturas con autocommit.</li>
 *   <li>{@link #enTransaccion}: escrituras atómicas (RNF-08). Antes de operar indica a PostgreSQL
 *       qué usuario de la aplicación trabaja (vetsalud.id_usuario), dato que usan los triggers de
 *       auditoría y kardex (RF-04).</li>
 * </ul>
 */
public final class Db {

    private static volatile Db instancia;

    private final ConexionBD conexion;

    public Db(ConexionBD conexion) {
        this.conexion = Preconditions.checkNotNull(conexion, "conexion");
    }

    /** Instancia compartida por la aplicación web (se crea al primer uso). */
    public static Db get() {
        if (instancia == null) {
            synchronized (Db.class) {
                if (instancia == null) {
                    instancia = new Db(ConexionBD.desdeConfiguracion());
                }
            }
        }
        return instancia;
    }

    public <T> T consultar(FuncionSql<T> trabajo) throws SQLException {
        try (Connection cn = conexion.abrir()) {
            return trabajo.aplicar(cn);
        }
    }

    public <T> T enTransaccion(Integer idUsuario, FuncionSql<T> trabajo) throws SQLException {
        try (Connection cn = conexion.abrir()) {
            cn.setAutoCommit(false);
            try {
                if (idUsuario != null) {
                    try (PreparedStatement ps = cn.prepareStatement("SELECT set_config('vetsalud.id_usuario', ?, true)")) {
                        ps.setString(1, idUsuario.toString());
                        ps.execute();
                    }
                }
                T resultado = trabajo.aplicar(cn);
                cn.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                cn.rollback();
                throw e;
            }
        }
    }
}
