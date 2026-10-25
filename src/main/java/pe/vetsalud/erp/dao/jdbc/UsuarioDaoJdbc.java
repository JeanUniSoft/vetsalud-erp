package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import pe.vetsalud.erp.dao.UsuarioDao;
import pe.vetsalud.erp.modelo.Usuario;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link UsuarioDao} sobre PostgreSQL. */
public class UsuarioDaoJdbc implements UsuarioDao {

    private static final String SELECT = """
            SELECT u.id_usuario, r.nombre AS rol, u.nombres, u.apellidos, u.usuario, u.correo, u.clave_hash,
                   u.colegiatura_cmvp, u.intentos_fallidos, u.bloqueado, u.activo, u.debe_cambiar_clave,
                   u.ultimo_acceso
            FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
            """;

    private final Db db;

    public UsuarioDaoJdbc(Db db) {
        this.db = db;
    }

    static Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(rs.getInt("id_usuario"), rs.getString("rol"), rs.getString("nombres"),
                rs.getString("apellidos"), rs.getString("usuario"), rs.getString("correo"),
                rs.getString("clave_hash"), rs.getString("colegiatura_cmvp"), rs.getInt("intentos_fallidos"),
                rs.getBoolean("bloqueado"), rs.getBoolean("activo"), rs.getBoolean("debe_cambiar_clave"),
                Jdbc.fechaHora(rs, "ultimo_acceso"));
    }

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE lower(u.usuario) = lower(?)",
                UsuarioDaoJdbc::mapear, usuario));
    }

    @Override
    public Optional<Usuario> buscarPorId(int idUsuario) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE u.id_usuario = ?", UsuarioDaoJdbc::mapear, idUsuario));
    }

    @Override
    public Optional<Usuario> buscarPorUsuarioOCorreo(String dato) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn,
                SELECT + " WHERE lower(u.usuario) = lower(?) OR lower(u.correo) = lower(?)",
                UsuarioDaoJdbc::mapear, dato, dato));
    }

    @Override
    public Set<String> permisosDeRol(String rol) throws SQLException {
        return db.consultar(cn -> new HashSet<>(Jdbc.lista(cn, """
                SELECT p.codigo FROM permiso p
                JOIN rol_permiso rp ON rp.id_permiso = p.id_permiso
                JOIN rol r ON r.id_rol = rp.id_rol
                WHERE r.nombre = ? AND r.activo
                """, rs -> rs.getString(1), rol)));
    }

    @Override
    public Optional<Integer> clienteVinculado(int idUsuario) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, "SELECT id_cliente FROM cliente_usuario WHERE id_usuario = ?",
                rs -> rs.getInt(1), idUsuario));
    }

    @Override
    public void registrarIntento(Integer idUsuario, String usuarioIngresado, String ip, boolean exitoso)
            throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                INSERT INTO acceso_log (id_usuario, usuario_ingresado, ip_equipo, exitoso)
                VALUES (?, ?, ?::inet, ?)
                """, idUsuario, usuarioIngresado, ip, exitoso));
    }

    @Override
    public void registrarFallo(int idUsuario, int intentos, boolean bloquear) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn,
                "UPDATE usuario SET intentos_fallidos = ?, bloqueado = ? WHERE id_usuario = ?",
                intentos, bloquear, idUsuario));
    }

    @Override
    public void registrarIngresoExitoso(int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn,
                "UPDATE usuario SET intentos_fallidos = 0, ultimo_acceso = now() WHERE id_usuario = ?", idUsuario));
    }

    @Override
    public void cambiarClave(int idUsuario, String nuevoHash, boolean debeCambiar) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                UPDATE usuario SET clave_hash = ?, debe_cambiar_clave = ?, clave_actualizada_en = now(),
                                   intentos_fallidos = 0, bloqueado = false
                WHERE id_usuario = ?
                """, nuevoHash, debeCambiar, idUsuario));
    }
}
