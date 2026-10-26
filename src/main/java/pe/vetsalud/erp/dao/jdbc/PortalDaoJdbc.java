package pe.vetsalud.erp.dao.jdbc;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.dao.PortalDao;
import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.modelo.PortalCita;
import pe.vetsalud.erp.modelo.PortalControl;
import pe.vetsalud.erp.modelo.Recordatorio;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link PortalDao}. Cada consulta incluye "m.id_cliente = ?" (control de acceso a
 * nivel de fila) y solo selecciona columnas autorizadas: nunca motivo, anamnesis, diagnóstico ni receta.
 */
public class PortalDaoJdbc implements PortalDao {

    private final Db db;

    public PortalDaoJdbc(Db db) {
        this.db = db;
    }

    @Override
    public List<Mascota> mascotas(int idCliente) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, MascotaDaoJdbc.SELECT + " WHERE m.id_cliente = ?"
                + MascotaDaoJdbc.ORDEN_ESTADO, MascotaDaoJdbc::mapear, idCliente));
    }

    @Override
    public Optional<Mascota> mascota(int idCliente, int idMascota) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, MascotaDaoJdbc.SELECT + " WHERE m.id_cliente = ? AND m.id_mascota = ?",
                MascotaDaoJdbc::mapear, idCliente, idMascota));
    }

    @Override
    public List<PortalCita> citasDesde(int idCliente, Integer idMascota, LocalDate desde, int limite)
            throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT c.id_cita, m.id_mascota, m.nombre AS mascota, c.inicio, s.nombre AS servicio,
                       v.nombres || ' ' || v.apellidos AS profesional, c.estado::text AS estado
                FROM cita c
                JOIN mascota m ON m.id_mascota = c.id_mascota
                JOIN servicio s ON s.id_servicio = c.id_servicio
                JOIN usuario v ON v.id_usuario = c.id_veterinario
                WHERE m.id_cliente = ? AND (?::int IS NULL OR m.id_mascota = ?::int) AND c.inicio >= ?::date
                ORDER BY c.inicio LIMIT ?
                """, rs -> new PortalCita(rs.getLong("id_cita"), rs.getInt("id_mascota"), rs.getString("mascota"),
                        Jdbc.fechaHora(rs, "inicio"), rs.getString("servicio"), rs.getString("profesional"),
                        rs.getString("estado")),
                idCliente, idMascota, idMascota, desde, limite));
    }

    @Override
    public List<PortalControl> controlesDesde(int idCliente, Integer idMascota, LocalDate desde) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT co.id_consulta, m.id_mascota, m.nombre AS mascota, co.fecha_control,
                       v.nombres || ' ' || v.apellidos AS profesional
                FROM consulta co
                JOIN mascota m ON m.id_mascota = co.id_mascota
                JOIN usuario v ON v.id_usuario = co.id_veterinario
                WHERE m.id_cliente = ? AND (?::int IS NULL OR m.id_mascota = ?::int)
                  AND co.estado = 'FIRMADA' AND co.fecha_control >= ?::date
                ORDER BY co.fecha_control, m.nombre
                """, rs -> new PortalControl(rs.getLong("id_consulta"), rs.getInt("id_mascota"),
                        rs.getString("mascota"), Jdbc.fecha(rs, "fecha_control"), rs.getString("profesional")),
                idCliente, idMascota, idMascota, desde));
    }

    @Override
    public List<Recordatorio> recordatorios(int idCliente, int limite) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, RecordatorioDaoJdbc.SELECT + """
                 WHERE r.id_cliente = ? AND r.estado <> 'CANCELADO'
                 ORDER BY r.programado_para DESC, r.id_recordatorio DESC LIMIT ?
                """, RecordatorioDaoJdbc::mapear, idCliente, limite));
    }
}
