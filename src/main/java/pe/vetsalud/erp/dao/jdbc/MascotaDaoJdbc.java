package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import pe.vetsalud.erp.dao.MascotaDao;
import pe.vetsalud.erp.modelo.CambioPropietario;
import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.modelo.MascotaCambio;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link MascotaDao}. Toda escritura va en transacción con el id del usuario:
 * el trigger tg_aud_mascota lo usa para la auditoría (RF-04, RF-08).
 */
public class MascotaDaoJdbc implements MascotaDao {

    static final String SELECT = """
            SELECT m.id_mascota, m.id_cliente, cl.nombres || coalesce(' ' || cl.apellidos, '') AS propietario,
                   e.id_especie, e.nombre AS especie, r.id_raza, r.nombre AS raza, m.nombre, m.sexo,
                   m.fecha_nacimiento, m.color, m.peso_kg, m.microchip, m.esterilizado, m.alertas, m.foto_ruta,
                   m.estado, m.creado_en
            FROM mascota m
            JOIN cliente cl ON cl.id_cliente = m.id_cliente
            JOIN raza r ON r.id_raza = m.id_raza
            JOIN especie e ON e.id_especie = r.id_especie
            """;

    /** Activas primero, luego inactivas y fallecidas. */
    static final String ORDEN_ESTADO =
            " ORDER BY CASE m.estado WHEN 'ACTIVA' THEN 0 WHEN 'INACTIVA' THEN 1 ELSE 2 END, m.nombre";

    private final Db db;

    public MascotaDaoJdbc(Db db) {
        this.db = db;
    }

    static Mascota mapear(ResultSet rs) throws SQLException {
        return new Mascota(rs.getInt("id_mascota"), rs.getInt("id_cliente"), rs.getString("propietario"),
                rs.getInt("id_especie"), rs.getString("especie"), rs.getInt("id_raza"), rs.getString("raza"),
                rs.getString("nombre"), StringUtils.trim(rs.getString("sexo")), Jdbc.fecha(rs, "fecha_nacimiento"),
                rs.getString("color"), rs.getBigDecimal("peso_kg"), rs.getString("microchip"),
                rs.getBoolean("esterilizado"), rs.getString("alertas"), rs.getString("foto_ruta"),
                rs.getString("estado"), Jdbc.fechaHora(rs, "creado_en"));
    }

    @Override
    public List<Mascota> buscar(String texto, int limite) throws SQLException {
        if (StringUtils.isBlank(texto)) {
            return db.consultar(cn -> Jdbc.lista(cn, SELECT + " ORDER BY m.creado_en DESC, m.id_mascota DESC LIMIT ?",
                    MascotaDaoJdbc::mapear, limite));
        }
        String exacto = texto.trim();
        String patron = "%" + exacto + "%";
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                 WHERE m.microchip = ? OR cl.num_documento = ? OR m.nombre ILIKE ?
                    OR (cl.nombres || ' ' || coalesce(cl.apellidos, '')) ILIKE ?
                 ORDER BY m.nombre, m.id_mascota LIMIT ?
                """, MascotaDaoJdbc::mapear, exacto, exacto, patron, patron, limite));
    }

    @Override
    public List<Mascota> listarPorCliente(int idCliente) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + " WHERE m.id_cliente = ?" + ORDEN_ESTADO,
                MascotaDaoJdbc::mapear, idCliente));
    }

    @Override
    public Optional<Mascota> buscarPorId(int idMascota) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE m.id_mascota = ?", MascotaDaoJdbc::mapear, idMascota));
    }

    @Override
    public boolean existeMicrochip(String microchip, Integer exceptoId) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn, """
                SELECT EXISTS (SELECT 1 FROM mascota WHERE microchip = ?
                               AND (?::int IS NULL OR id_mascota <> ?::int))
                """, Boolean.class, microchip, exceptoId, exceptoId));
    }

    @Override
    public int insertar(Mascota m, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> (int) Jdbc.insertar(cn, """
                INSERT INTO mascota (id_cliente, id_raza, nombre, sexo, fecha_nacimiento, color, peso_kg, microchip,
                                     esterilizado, alertas)
                VALUES (?, ?, ?, ?, ?::date, ?, ?::numeric, ?, ?, ?) RETURNING id_mascota
                """, m.idCliente(), m.idRaza(), m.nombre(), m.sexo(), m.fechaNacimiento(), m.color(), m.pesoKg(),
                m.microchip(), m.esterilizado(), m.alertas()));
    }

    @Override
    public void actualizar(Mascota m, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                UPDATE mascota SET id_raza = ?, nombre = ?, sexo = ?, fecha_nacimiento = ?::date, color = ?,
                       peso_kg = ?::numeric, microchip = ?, esterilizado = ?, alertas = ?
                WHERE id_mascota = ?
                """, m.idRaza(), m.nombre(), m.sexo(), m.fechaNacimiento(), m.color(), m.pesoKg(), m.microchip(),
                m.esterilizado(), m.alertas(), m.idMascota()));
    }

    @Override
    public void cambiarEstado(int idMascota, String estado, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn,
                "UPDATE mascota SET estado = ? WHERE id_mascota = ?", estado, idMascota));
    }

    @Override
    public void actualizarFoto(int idMascota, String fotoRuta, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn,
                "UPDATE mascota SET foto_ruta = ? WHERE id_mascota = ?", fotoRuta, idMascota));
    }

    @Override
    public boolean cambiarPropietario(int idMascota, int idClienteAnterior, int idClienteNuevo, String motivo,
                                      int idUsuario) throws SQLException {
        // Una sola transacción (RNF-08): si falla cualquier sentencia no queda ni el cambio ni el historial
        return db.enTransaccion(idUsuario, cn -> {
            int filas = Jdbc.ejecutar(cn, "UPDATE mascota SET id_cliente = ? WHERE id_mascota = ? AND id_cliente = ?",
                    idClienteNuevo, idMascota, idClienteAnterior);
            if (filas == 0) {
                return false;
            }
            Jdbc.insertar(cn, """
                    INSERT INTO mascota_propietario_hist (id_mascota, id_cliente_anterior, id_cliente_nuevo, motivo, id_usuario)
                    VALUES (?, ?, ?, ?, ?) RETURNING id_hist
                    """, idMascota, idClienteAnterior, idClienteNuevo, motivo, idUsuario);
            // Los recordatorios aún no enviados de la mascota pasan al nuevo dueño (el anterior ya no debe recibirlos)
            Jdbc.ejecutar(cn, """
                    UPDATE recordatorio SET id_cliente = ?
                    WHERE estado = 'PENDIENTE' AND id_cliente = ?
                      AND (id_cita IN (SELECT id_cita FROM cita WHERE id_mascota = ?)
                           OR id_consulta IN (SELECT id_consulta FROM consulta WHERE id_mascota = ?))
                    """, idClienteNuevo, idClienteAnterior, idMascota, idMascota);
            return true;
        });
    }

    @Override
    public List<CambioPropietario> historialPropietarios(int idMascota) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT h.id_hist, h.id_mascota, h.id_cliente_anterior,
                       ca.nombres || coalesce(' ' || ca.apellidos, '') AS anterior,
                       h.id_cliente_nuevo, cnu.nombres || coalesce(' ' || cnu.apellidos, '') AS nuevo,
                       h.motivo, u.usuario, h.fecha_hora
                FROM mascota_propietario_hist h
                JOIN cliente ca ON ca.id_cliente = h.id_cliente_anterior
                JOIN cliente cnu ON cnu.id_cliente = h.id_cliente_nuevo
                JOIN usuario u ON u.id_usuario = h.id_usuario
                WHERE h.id_mascota = ?
                ORDER BY h.fecha_hora DESC, h.id_hist DESC
                """, rs -> new CambioPropietario(rs.getLong("id_hist"), rs.getInt("id_mascota"),
                        rs.getInt("id_cliente_anterior"), rs.getString("anterior"), rs.getInt("id_cliente_nuevo"),
                        rs.getString("nuevo"), rs.getString("motivo"), rs.getString("usuario"),
                        Jdbc.fechaHora(rs, "fecha_hora")), idMascota));
    }

    /** Fila plana (un campo modificado por fila) antes de agrupar por registro de auditoría. */
    private record FilaCambio(long idAuditoria, LocalDateTime fecha, String usuario, String operacion, String campo,
                              String antes, String despues) {
    }

    @Override
    public List<MascotaCambio> historialCambios(int idMascota, int limite) throws SQLException {
        // jsonb_each compara valores_antes y valores_despues del trigger fn_auditar() y devuelve solo lo que cambió
        List<FilaCambio> filas = db.consultar(cn -> Jdbc.lista(cn, """
                WITH a AS (
                  SELECT id_auditoria, fecha_hora, id_usuario, operacion, valores_antes, valores_despues
                  FROM auditoria
                  WHERE tabla = 'mascota' AND id_registro = ?
                  ORDER BY fecha_hora DESC, id_auditoria DESC
                  LIMIT ?
                )
                SELECT a.id_auditoria, a.fecha_hora, coalesce(u.usuario, 'sistema') AS usuario, a.operacion,
                       k.key AS campo, a.valores_antes ->> k.key AS antes, k.value #>> '{}' AS despues
                FROM a
                LEFT JOIN usuario u ON u.id_usuario = a.id_usuario
                LEFT JOIN LATERAL jsonb_each(a.valores_despues) k
                       ON a.operacion = 'UPDATE' AND k.key <> 'creado_en'
                      AND k.value IS DISTINCT FROM (a.valores_antes -> k.key)
                ORDER BY a.fecha_hora DESC, a.id_auditoria DESC, k.key
                """, rs -> new FilaCambio(rs.getLong("id_auditoria"), Jdbc.fechaHora(rs, "fecha_hora"),
                        rs.getString("usuario"), rs.getString("operacion"), rs.getString("campo"),
                        rs.getString("antes"), rs.getString("despues")), idMascota, limite));

        Map<Long, List<FilaCambio>> porRegistro = new LinkedHashMap<>();
        for (FilaCambio f : filas) {
            porRegistro.computeIfAbsent(f.idAuditoria(), k -> new ArrayList<>()).add(f);
        }
        List<MascotaCambio> cambios = new ArrayList<>();
        for (List<FilaCambio> grupo : porRegistro.values()) {
            FilaCambio primera = grupo.get(0);
            List<MascotaCambio.Campo> campos = grupo.stream().filter(f -> f.campo() != null)
                    .map(f -> new MascotaCambio.Campo(f.campo(), f.antes(), f.despues())).toList();
            cambios.add(new MascotaCambio(primera.idAuditoria(), primera.fecha(), primera.usuario(),
                    primera.operacion(), campos));
        }
        return cambios;
    }
}
