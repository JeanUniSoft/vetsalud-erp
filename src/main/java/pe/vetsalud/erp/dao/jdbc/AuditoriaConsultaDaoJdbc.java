package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.AuditoriaConsultaDao;
import pe.vetsalud.erp.modelo.AccesoRegistro;
import pe.vetsalud.erp.modelo.AuditoriaRegistro;
import pe.vetsalud.erp.modelo.Opcion;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link AuditoriaConsultaDao}: solo SELECT con parámetros enlazados. */
public class AuditoriaConsultaDaoJdbc implements AuditoriaConsultaDao {

    private static final String SELECT = """
            SELECT a.id_auditoria, a.fecha_hora, a.id_usuario, u.usuario, a.tabla, a.id_registro, a.operacion,
                   CASE WHEN a.operacion = 'UPDATE'
                         AND (a.valores_despues ->> 'estado') IS DISTINCT FROM (a.valores_antes ->> 'estado')
                        THEN a.valores_despues ->> 'estado' END AS estado_nuevo,
                   CASE a.operacion
                        WHEN 'UPDATE' THEN (SELECT count(*) FROM jsonb_each(a.valores_despues) d
                                             WHERE d.key <> 'clave_hash'
                                               AND d.value IS DISTINCT FROM (a.valores_antes -> d.key))
                        WHEN 'INSERT' THEN (SELECT count(*) FROM jsonb_object_keys(a.valores_despues))
                        ELSE (SELECT count(*) FROM jsonb_object_keys(a.valores_antes)) END AS cambios
            FROM auditoria a LEFT JOIN usuario u ON u.id_usuario = a.id_usuario
            """;

    private final Db db;

    public AuditoriaConsultaDaoJdbc(Db db) {
        this.db = Preconditions.checkNotNull(db);
    }

    private static AuditoriaRegistro mapear(ResultSet rs) throws SQLException {
        return new AuditoriaRegistro(rs.getLong("id_auditoria"), Jdbc.fechaHora(rs, "fecha_hora"),
                Jdbc.entero(rs, "id_usuario"), rs.getString("usuario"), rs.getString("tabla"),
                rs.getLong("id_registro"), rs.getString("operacion"), rs.getString("estado_nuevo"),
                rs.getInt("cambios"));
    }

    @Override
    public List<AuditoriaRegistro> buscar(String tabla, String operacion, Integer idUsuario, LocalDate desde,
                                          LocalDate hasta, int limite) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                WHERE a.fecha_hora >= ? AND a.fecha_hora < ?
                  AND (?::text IS NULL OR a.tabla = ?::text)
                  AND (?::text IS NULL OR a.operacion = ?::text)
                  AND (?::int IS NULL OR a.id_usuario = ?::int)
                ORDER BY a.fecha_hora DESC, a.id_auditoria DESC
                LIMIT ?
                """, AuditoriaConsultaDaoJdbc::mapear, desde, hasta, tabla, tabla, operacion, operacion,
                idUsuario, idUsuario, limite));
    }

    @Override
    public Optional<AuditoriaRegistro> buscarPorId(long idAuditoria) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE a.id_auditoria = ?",
                AuditoriaConsultaDaoJdbc::mapear, idAuditoria));
    }

    @Override
    public Map<String, String> valores(long idAuditoria, boolean antes) throws SQLException {
        // Dos sentencias fijas (sin concatenar la columna): una para cada lado del cambio
        String sql = antes
                ? "SELECT e.key, e.value FROM auditoria a, jsonb_each_text(a.valores_antes) e WHERE a.id_auditoria = ? ORDER BY e.key"
                : "SELECT e.key, e.value FROM auditoria a, jsonb_each_text(a.valores_despues) e WHERE a.id_auditoria = ? ORDER BY e.key";
        Map<String, String> valores = new LinkedHashMap<>();
        db.consultar(cn -> Jdbc.lista(cn, sql, rs -> valores.put(rs.getString(1), rs.getString(2)), idAuditoria));
        return valores;
    }

    @Override
    public List<String> tablas() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, "SELECT DISTINCT tabla FROM auditoria ORDER BY tabla",
                rs -> rs.getString(1)));
    }

    @Override
    public List<Opcion> usuarios() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT id_usuario, usuario || ' · ' || nombres || ' ' || apellidos AS etiqueta
                FROM usuario ORDER BY usuario
                """, rs -> new Opcion(rs.getLong(1), rs.getString(2))));
    }

    @Override
    public List<AccesoRegistro> accesos(String usuario, Boolean exitoso, LocalDate desde, LocalDate hasta,
                                        int limite) throws SQLException {
        String u = StringUtils.trimToNull(usuario);
        String patron = u == null ? null : "%" + u + "%";
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT l.id_acceso, l.fecha_hora, l.usuario_ingresado, l.id_usuario,
                       us.nombres || ' ' || us.apellidos AS nombre, r.nombre AS rol, host(l.ip_equipo) AS ip, l.exitoso
                FROM acceso_log l
                LEFT JOIN usuario us ON us.id_usuario = l.id_usuario
                LEFT JOIN rol r ON r.id_rol = us.id_rol
                WHERE l.fecha_hora >= ? AND l.fecha_hora < ?
                  AND (?::text IS NULL OR l.usuario_ingresado ILIKE ?::text)
                  AND (?::boolean IS NULL OR l.exitoso = ?::boolean)
                ORDER BY l.fecha_hora DESC, l.id_acceso DESC
                LIMIT ?
                """, rs -> new AccesoRegistro(rs.getLong("id_acceso"), Jdbc.fechaHora(rs, "fecha_hora"),
                        rs.getString("usuario_ingresado"), Jdbc.entero(rs, "id_usuario"), rs.getString("nombre"),
                        rs.getString("rol"), rs.getString("ip"), rs.getBoolean("exitoso")),
                desde, hasta, patron, patron, exitoso, exitoso, limite));
    }
}
