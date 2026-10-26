package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.UsuarioAdminDao;
import pe.vetsalud.erp.modelo.Opcion;
import pe.vetsalud.erp.modelo.UsuarioAdmin;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link UsuarioAdminDao}. Cada escritura va en una transacción con el id del
 * administrador: el trigger tg_aud_usuario registra en auditoria el antes/después (sin clave_hash).
 */
public class UsuarioAdminDaoJdbc implements UsuarioAdminDao {

    private static final String SELECT = """
            SELECT u.id_usuario, r.nombre AS rol, u.nombres, u.apellidos, u.usuario, u.correo, u.colegiatura_cmvp,
                   u.intentos_fallidos, u.bloqueado, u.activo, u.debe_cambiar_clave, u.ultimo_acceso, u.creado_en,
                   c.num_documento AS documento_cliente,
                   c.nombres || coalesce(' ' || c.apellidos, '') AS cliente
            FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
            LEFT JOIN cliente_usuario cu ON cu.id_usuario = u.id_usuario
            LEFT JOIN cliente c ON c.id_cliente = cu.id_cliente
            """;

    private final Db db;

    public UsuarioAdminDaoJdbc(Db db) {
        this.db = Preconditions.checkNotNull(db);
    }

    static UsuarioAdmin mapear(ResultSet rs) throws SQLException {
        return new UsuarioAdmin(rs.getInt("id_usuario"), rs.getString("rol"), rs.getString("nombres"),
                rs.getString("apellidos"), rs.getString("usuario"), rs.getString("correo"),
                rs.getString("colegiatura_cmvp"), rs.getInt("intentos_fallidos"), rs.getBoolean("bloqueado"),
                rs.getBoolean("activo"), rs.getBoolean("debe_cambiar_clave"), Jdbc.fechaHora(rs, "ultimo_acceso"),
                Jdbc.fechaHora(rs, "creado_en"), rs.getString("documento_cliente"), rs.getString("cliente"));
    }

    @Override
    public List<UsuarioAdmin> listar(String texto, String rol, String estado) throws SQLException {
        String t = StringUtils.trimToNull(texto);
        String patron = t == null ? null : "%" + t + "%";
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                WHERE (?::text IS NULL OR u.usuario ILIKE ?::text OR u.correo ILIKE ?::text
                       OR (u.nombres || ' ' || u.apellidos) ILIKE ?::text)
                  AND (?::text IS NULL OR r.nombre = ?::text)
                  AND (?::text IS NULL
                       OR (?::text = 'ACTIVOS' AND u.activo AND NOT u.bloqueado)
                       OR (?::text = 'INACTIVOS' AND NOT u.activo)
                       OR (?::text = 'BLOQUEADOS' AND u.bloqueado))
                ORDER BY u.activo DESC, r.nombre, u.apellidos, u.nombres
                """, UsuarioAdminDaoJdbc::mapear, patron, patron, patron, patron, rol, rol,
                estado, estado, estado, estado));
    }

    @Override
    public Optional<UsuarioAdmin> buscarPorId(int idUsuario) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE u.id_usuario = ?", UsuarioAdminDaoJdbc::mapear,
                idUsuario));
    }

    @Override
    public boolean existeUsuario(String usuario, Integer exceptoId) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn, """
                SELECT EXISTS (SELECT 1 FROM usuario WHERE lower(usuario) = lower(?)
                               AND (?::int IS NULL OR id_usuario <> ?::int))
                """, Boolean.class, usuario, exceptoId, exceptoId));
    }

    @Override
    public boolean existeCorreo(String correo, Integer exceptoId) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn, """
                SELECT EXISTS (SELECT 1 FROM usuario WHERE lower(correo) = lower(?)
                               AND (?::int IS NULL OR id_usuario <> ?::int))
                """, Boolean.class, correo, exceptoId, exceptoId));
    }

    @Override
    public List<String> roles() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, "SELECT nombre FROM rol WHERE activo ORDER BY id_rol",
                rs -> rs.getString(1)));
    }

    @Override
    public Optional<Opcion> clientePorDocumento(String numDocumento) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, """
                SELECT id_cliente, nombres || coalesce(' ' || apellidos, '') AS nombre
                FROM cliente WHERE num_documento = ? AND activo
                """, rs -> new Opcion(rs.getLong("id_cliente"), rs.getString("nombre")), numDocumento));
    }

    @Override
    public boolean clienteTieneUsuario(int idCliente, Integer exceptoUsuario) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn, """
                SELECT EXISTS (SELECT 1 FROM cliente_usuario WHERE id_cliente = ?
                               AND (?::int IS NULL OR id_usuario <> ?::int))
                """, Boolean.class, idCliente, exceptoUsuario, exceptoUsuario));
    }

    @Override
    public List<Opcion> clientesSinUsuario(int limite) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT c.id_cliente, c.num_documento || ' · ' || c.nombres || coalesce(' ' || c.apellidos, '') AS etiqueta
                FROM cliente c
                WHERE c.activo AND NOT EXISTS (SELECT 1 FROM cliente_usuario cu WHERE cu.id_cliente = c.id_cliente)
                ORDER BY c.nombres, c.apellidos LIMIT ?
                """, rs -> new Opcion(rs.getLong("id_cliente"), rs.getString("etiqueta")), limite));
    }

    @Override
    public int insertar(UsuarioAdmin d, String claveHash, Integer idCliente, int idActor) throws SQLException {
        return db.enTransaccion(idActor, cn -> {
            int id = (int) Jdbc.insertar(cn, """
                    INSERT INTO usuario (id_rol, nombres, apellidos, usuario, correo, clave_hash, colegiatura_cmvp,
                                         debe_cambiar_clave, clave_actualizada_en)
                    SELECT id_rol, ?, ?, ?, ?, ?, ?, true, now() FROM rol WHERE nombre = ? AND activo
                    RETURNING id_usuario
                    """, d.nombres(), d.apellidos(), d.usuario(), d.correo(), claveHash, d.colegiatura(), d.rol());
            if (idCliente != null) {
                Jdbc.ejecutar(cn, "INSERT INTO cliente_usuario (id_usuario, id_cliente) VALUES (?, ?)", id, idCliente);
            }
            return id;
        });
    }

    @Override
    public void actualizar(UsuarioAdmin d, Integer idCliente, int idActor) throws SQLException {
        db.enTransaccion(idActor, cn -> {
            int filas = Jdbc.ejecutar(cn, """
                    UPDATE usuario SET id_rol = (SELECT id_rol FROM rol WHERE nombre = ? AND activo),
                           nombres = ?, apellidos = ?, usuario = ?, correo = ?, colegiatura_cmvp = ?
                    WHERE id_usuario = ?
                    """, d.rol(), d.nombres(), d.apellidos(), d.usuario(), d.correo(), d.colegiatura(), d.idUsuario());
            if (filas != 1) {
                throw new SQLException("El usuario " + d.idUsuario() + " no existe");
            }
            if (idCliente != null) {
                // La app no tiene DELETE en cliente_usuario: el vínculo se crea o se reasigna con UPSERT
                Jdbc.ejecutar(cn, """
                        INSERT INTO cliente_usuario (id_usuario, id_cliente) VALUES (?, ?)
                        ON CONFLICT (id_usuario) DO UPDATE SET id_cliente = EXCLUDED.id_cliente
                        """, d.idUsuario(), idCliente);
            }
            return filas;
        });
    }

    @Override
    public void restablecerClave(int idUsuario, String claveHash, int idActor) throws SQLException {
        db.enTransaccion(idActor, cn -> Jdbc.ejecutar(cn, """
                UPDATE usuario SET clave_hash = ?, debe_cambiar_clave = true, clave_actualizada_en = now(),
                                   intentos_fallidos = 0, bloqueado = false
                WHERE id_usuario = ?
                """, claveHash, idUsuario));
    }

    @Override
    public void desbloquear(int idUsuario, int idActor) throws SQLException {
        db.enTransaccion(idActor, cn -> Jdbc.ejecutar(cn,
                "UPDATE usuario SET bloqueado = false, intentos_fallidos = 0 WHERE id_usuario = ?", idUsuario));
    }

    @Override
    public void cambiarEstado(int idUsuario, boolean activo, int idActor) throws SQLException {
        db.enTransaccion(idActor, cn -> Jdbc.ejecutar(cn, "UPDATE usuario SET activo = ? WHERE id_usuario = ?",
                activo, idUsuario));
    }
}
