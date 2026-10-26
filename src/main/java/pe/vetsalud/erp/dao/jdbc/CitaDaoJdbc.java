package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.dao.CitaDao;
import pe.vetsalud.erp.modelo.AgendaFiltro;
import pe.vetsalud.erp.modelo.CambioEstadoCita;
import pe.vetsalud.erp.modelo.Cita;
import pe.vetsalud.erp.modelo.CitaSolicitud;
import pe.vetsalud.erp.modelo.ControlProgramado;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link CitaDao}. El trigger fn_cita_estado valida las transiciones y llena
 * cita_estado_hist; la restricción ex_cita_sin_solape impide cruces del mismo veterinario (RN-03).
 */
public class CitaDaoJdbc implements CitaDao {

    private static final String SELECT = """
            SELECT c.id_cita, c.id_mascota, m.nombre AS mascota, e.nombre AS especie, m.alertas,
                   cl.id_cliente, cl.nombres || coalesce(' ' || cl.apellidos, '') AS cliente, cl.telefono,
                   c.id_servicio, s.nombre AS servicio, c.id_veterinario, v.nombres || ' ' || v.apellidos AS veterinario,
                   rp.usuario AS registrado_por, c.inicio, c.fin, c.estado::text AS estado, c.motivo, c.creado_en,
                   co.id_consulta, co.estado AS estado_consulta
            FROM cita c
            JOIN mascota m ON m.id_mascota = c.id_mascota
            JOIN raza r ON r.id_raza = m.id_raza
            JOIN especie e ON e.id_especie = r.id_especie
            JOIN cliente cl ON cl.id_cliente = m.id_cliente
            JOIN servicio s ON s.id_servicio = c.id_servicio
            JOIN usuario v ON v.id_usuario = c.id_veterinario
            JOIN usuario rp ON rp.id_usuario = c.id_registrado_por
            LEFT JOIN consulta co ON co.id_cita = c.id_cita
            """;

    private final Db db;

    public CitaDaoJdbc(Db db) {
        this.db = db;
    }

    static Cita mapear(ResultSet rs) throws SQLException {
        return new Cita(rs.getLong("id_cita"), rs.getInt("id_mascota"), rs.getString("mascota"), rs.getString("especie"),
                rs.getString("alertas"), rs.getInt("id_cliente"), rs.getString("cliente"), rs.getString("telefono"),
                rs.getInt("id_servicio"), rs.getString("servicio"), rs.getInt("id_veterinario"), rs.getString("veterinario"),
                rs.getString("registrado_por"), Jdbc.fechaHora(rs, "inicio"), Jdbc.fechaHora(rs, "fin"),
                rs.getString("estado"), rs.getString("motivo"), Jdbc.fechaHora(rs, "creado_en"),
                Jdbc.largo(rs, "id_consulta"), rs.getString("estado_consulta"), List.of());
    }

    @Override
    public List<Cita> agenda(LocalDateTime desde, LocalDateTime hasta, AgendaFiltro f) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                 WHERE c.inicio >= ? AND c.inicio < ?
                   AND (?::int IS NULL OR c.id_veterinario = ?::int)
                   AND (?::int IS NULL OR c.id_servicio = ?::int)
                   AND (?::text IS NULL OR c.estado = ?::estado_cita)
                 ORDER BY c.inicio, v.apellidos
                """, CitaDaoJdbc::mapear, desde, hasta, f.idVeterinario(), f.idVeterinario(),
                f.idServicio(), f.idServicio(), f.estado(), f.estado()));
    }

    @Override
    public Optional<Cita> buscarPorId(long idCita) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE c.id_cita = ?", CitaDaoJdbc::mapear, idCita));
    }

    @Override
    public Optional<Cita> cruce(Integer idVeterinario, Integer idMascota, LocalDateTime inicio, LocalDateTime fin,
                                Long exceptoIdCita) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + """
                 WHERE c.estado NOT IN ('CANCELADA', 'NO_ASISTIO')
                   AND (?::int IS NULL OR c.id_veterinario = ?::int)
                   AND (?::int IS NULL OR c.id_mascota = ?::int)
                   AND tstzrange(c.inicio, c.fin, '[)') && tstzrange(?::timestamptz, ?::timestamptz, '[)')
                   AND (?::bigint IS NULL OR c.id_cita <> ?::bigint)
                 ORDER BY c.inicio LIMIT 1
                """, CitaDaoJdbc::mapear, idVeterinario, idVeterinario, idMascota, idMascota, inicio, fin,
                exceptoIdCita, exceptoIdCita));
    }

    @Override
    public long insertar(CitaSolicitud s, LocalDateTime fin, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.insertar(cn, """
                INSERT INTO cita (id_mascota, id_servicio, id_veterinario, id_registrado_por, inicio, fin, motivo)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_cita
                """, s.idMascota(), s.idServicio(), s.idVeterinario(), idUsuario, s.inicio(), fin, s.motivo()));
    }

    @Override
    public boolean cambiarEstado(long idCita, String estadoActual, String estadoNuevo, String motivo, int idUsuario)
            throws SQLException {
        return db.enTransaccion(idUsuario, cn -> {
            // el trigger fn_cita_estado guarda este motivo en cita_estado_hist (db/10_motivo_cita.sql)
            Jdbc.escalar(cn, "SELECT set_config('vetsalud.motivo_cita', coalesce(?::text, ''), true)", String.class, motivo);
            return Jdbc.ejecutar(cn, """
                    UPDATE cita SET estado = ?::estado_cita, motivo = coalesce(?::varchar, motivo)
                    WHERE id_cita = ? AND estado = ?::estado_cita
                    """, estadoNuevo, motivo, idCita, estadoActual) == 1;
        });
    }

    @Override
    public boolean reprogramar(long idCita, LocalDateTime inicio, LocalDateTime fin, int idVeterinario, String estado,
                               String constancia, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> {
            int filas = Jdbc.ejecutar(cn, """
                    UPDATE cita SET inicio = ?, fin = ?, id_veterinario = ?
                    WHERE id_cita = ? AND estado = ?::estado_cita AND estado IN ('PENDIENTE', 'CONFIRMADA')
                    """, inicio, fin, idVeterinario, idCita, estado);
            if (filas != 1) {
                return false;
            }
            // El trigger solo registra cambios de estado: la reprogramación deja su constancia (motivo, usuario, fecha)
            Jdbc.ejecutar(cn, """
                    INSERT INTO cita_estado_hist (id_cita, estado_anterior, estado_nuevo, motivo, id_usuario)
                    VALUES (?, ?::estado_cita, ?::estado_cita, ?, ?)
                    """, idCita, estado, estado, constancia, idUsuario);
            return true;
        });
    }

    @Override
    public List<CambioEstadoCita> historial(long idCita) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT h.id_hist, h.estado_anterior::text AS estado_anterior, h.estado_nuevo::text AS estado_nuevo,
                       h.motivo, u.usuario, u.nombres || ' ' || u.apellidos AS nombre_usuario, h.fecha_hora
                FROM cita_estado_hist h
                LEFT JOIN usuario u ON u.id_usuario = h.id_usuario
                WHERE h.id_cita = ?
                ORDER BY h.fecha_hora, h.id_hist
                """, rs -> new CambioEstadoCita(rs.getLong("id_hist"), rs.getString("estado_anterior"),
                        rs.getString("estado_nuevo"), rs.getString("motivo"), rs.getString("usuario"),
                        rs.getString("nombre_usuario"), Jdbc.fechaHora(rs, "fecha_hora")), idCita));
    }

    @Override
    public List<ControlProgramado> controles(LocalDate desde, LocalDate hasta, Integer idVeterinario) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT co.id_consulta, co.fecha_control, co.id_mascota, m.nombre AS mascota, e.nombre AS especie,
                       cl.nombres || coalesce(' ' || cl.apellidos, '') AS cliente, cl.telefono,
                       co.id_veterinario, v.nombres || ' ' || v.apellidos AS veterinario,
                       (SELECT d.descripcion FROM consulta_diagnostico d
                        WHERE d.id_consulta = co.id_consulta AND d.es_principal) AS diagnostico,
                       EXISTS (SELECT 1 FROM cita c2 WHERE c2.id_mascota = co.id_mascota
                               AND c2.inicio::date = co.fecha_control
                               AND c2.estado NOT IN ('CANCELADA', 'NO_ASISTIO')) AS con_cita
                FROM consulta co
                JOIN mascota m ON m.id_mascota = co.id_mascota
                JOIN raza r ON r.id_raza = m.id_raza
                JOIN especie e ON e.id_especie = r.id_especie
                JOIN cliente cl ON cl.id_cliente = m.id_cliente
                JOIN usuario v ON v.id_usuario = co.id_veterinario
                WHERE co.fecha_control BETWEEN ? AND ?
                  AND (?::int IS NULL OR co.id_veterinario = ?::int)
                ORDER BY co.fecha_control, m.nombre
                """, rs -> new ControlProgramado(rs.getLong("id_consulta"), Jdbc.fecha(rs, "fecha_control"),
                        rs.getInt("id_mascota"), rs.getString("mascota"), rs.getString("especie"),
                        rs.getString("cliente"), rs.getString("telefono"), rs.getInt("id_veterinario"),
                        rs.getString("veterinario"), rs.getString("diagnostico"), rs.getBoolean("con_cita")),
                desde, hasta, idVeterinario, idVeterinario));
    }
}
