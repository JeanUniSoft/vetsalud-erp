package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import pe.vetsalud.erp.dao.RecordatorioDao;
import pe.vetsalud.erp.modelo.Recordatorio;
import pe.vetsalud.erp.modelo.RecordatorioResumen;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link RecordatorioDao}. Nunca lee motivo, diagnóstico ni receta (RF-37). */
public class RecordatorioDaoJdbc implements RecordatorioDao {

    static final String SELECT = """
            SELECT r.id_recordatorio, r.id_cliente, cl.nombres || coalesce(' ' || cl.apellidos, '') AS cliente,
                   cl.correo, cl.acepta_recordatorios, cl.activo AS cliente_activo, r.id_cita, r.id_consulta,
                   m.id_mascota, m.nombre AS mascota, m.estado AS estado_mascota, ci.inicio AS inicio_cita,
                   ci.estado::text AS estado_cita, co.fecha_control, r.canal, r.programado_para, r.estado,
                   r.enviado_en
            FROM recordatorio r
            JOIN cliente cl ON cl.id_cliente = r.id_cliente
            LEFT JOIN cita ci ON ci.id_cita = r.id_cita
            LEFT JOIN consulta co ON co.id_consulta = r.id_consulta
            JOIN mascota m ON m.id_mascota = coalesce(ci.id_mascota, co.id_mascota)
            """;

    private final Db db;

    public RecordatorioDaoJdbc(Db db) {
        this.db = db;
    }

    static Recordatorio mapear(ResultSet rs) throws SQLException {
        return new Recordatorio(rs.getLong("id_recordatorio"), rs.getInt("id_cliente"), rs.getString("cliente"),
                rs.getString("correo"), rs.getBoolean("acepta_recordatorios"), rs.getBoolean("cliente_activo"),
                Jdbc.largo(rs, "id_cita"), Jdbc.largo(rs, "id_consulta"), rs.getInt("id_mascota"),
                rs.getString("mascota"), rs.getString("estado_mascota"), Jdbc.fechaHora(rs, "inicio_cita"),
                rs.getString("estado_cita"), Jdbc.fecha(rs, "fecha_control"), rs.getString("canal"),
                Jdbc.fechaHora(rs, "programado_para"), rs.getString("estado"), Jdbc.fechaHora(rs, "enviado_en"));
    }

    @Override
    public List<Recordatorio> listar(String estado, int limite) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                 WHERE (?::text IS NULL OR r.estado = ?::text)
                 ORDER BY r.programado_para DESC, r.id_recordatorio DESC LIMIT ?
                """, RecordatorioDaoJdbc::mapear, estado, estado, limite));
    }

    @Override
    public RecordatorioResumen resumen() throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, """
                SELECT count(*) FILTER (WHERE estado = 'PENDIENTE' AND programado_para <= now()) AS vencidos,
                       count(*) FILTER (WHERE estado = 'PENDIENTE' AND programado_para > now())  AS futuros,
                       count(*) FILTER (WHERE estado = 'ENVIADO')   AS enviados,
                       count(*) FILTER (WHERE estado = 'FALLIDO')   AS fallidos,
                       count(*) FILTER (WHERE estado = 'CANCELADO') AS cancelados
                FROM recordatorio
                """, rs -> new RecordatorioResumen(rs.getInt("vencidos"), rs.getInt("futuros"), rs.getInt("enviados"),
                        rs.getInt("fallidos"), rs.getInt("cancelados"))).orElseThrow());
    }

    @Override
    public List<Recordatorio> pendientes(boolean incluirFuturos, int limite) throws SQLException {
        // ix_recordatorio_cola (índice parcial WHERE estado = 'PENDIENTE') acelera esta consulta
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                 WHERE r.estado = 'PENDIENTE' AND (?::boolean OR r.programado_para <= now())
                 ORDER BY r.programado_para, r.id_recordatorio LIMIT ?
                """, RecordatorioDaoJdbc::mapear, incluirFuturos, limite));
    }

    @Override
    public boolean cambiarEstado(long idRecordatorio, String estadoActual, String estadoNuevo, LocalDateTime fecha,
                                 Integer idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                UPDATE recordatorio SET estado = ?, enviado_en = ?::timestamptz
                WHERE id_recordatorio = ? AND estado = ?
                """, estadoNuevo, fecha, idRecordatorio, estadoActual)) == 1;
    }

    @Override
    public int programarCitas(int horasAntes, Integer idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                INSERT INTO recordatorio (id_cliente, id_cita, programado_para)
                SELECT m.id_cliente, c.id_cita, greatest(c.inicio - make_interval(hours => ?::int), now())
                FROM cita c JOIN mascota m ON m.id_mascota = c.id_mascota
                WHERE c.inicio > now() AND c.estado IN ('PENDIENTE', 'CONFIRMADA') AND m.estado = 'ACTIVA'
                  AND NOT EXISTS (SELECT 1 FROM recordatorio r WHERE r.id_cita = c.id_cita)
                """, horasAntes));
    }

    @Override
    public int programarControles(LocalTime hora, Integer idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                INSERT INTO recordatorio (id_cliente, id_consulta, programado_para)
                SELECT m.id_cliente, co.id_consulta, greatest((co.fecha_control - 1) + ?::time, now())
                FROM consulta co JOIN mascota m ON m.id_mascota = co.id_mascota
                WHERE co.fecha_control > CURRENT_DATE AND co.estado = 'FIRMADA' AND m.estado = 'ACTIVA'
                  AND NOT EXISTS (SELECT 1 FROM recordatorio r WHERE r.id_consulta = co.id_consulta)
                """, hora.toString()));
    }
}
