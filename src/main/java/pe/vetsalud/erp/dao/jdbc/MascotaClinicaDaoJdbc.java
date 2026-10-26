package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import pe.vetsalud.erp.dao.MascotaClinicaDao;
import pe.vetsalud.erp.modelo.MascotaClinica;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link MascotaClinicaDao} (solo lectura). */
public class MascotaClinicaDaoJdbc implements MascotaClinicaDao {

    private static final String SELECT = """
            SELECT m.id_mascota, m.nombre, e.nombre AS especie, r.nombre AS raza, m.sexo, m.fecha_nacimiento, m.peso_kg,
                   m.color, m.microchip, m.esterilizado, m.alertas, m.estado,
                   cl.id_cliente, cl.nombres || coalesce(' ' || cl.apellidos, '') AS cliente,
                   cl.tipo_documento::text AS tipo_documento, cl.num_documento, cl.telefono, cl.correo,
                   cl.acepta_recordatorios,
                   (SELECT max(co.fecha_hora) FROM consulta co WHERE co.id_mascota = m.id_mascota) AS ultima_consulta
            FROM mascota m
            JOIN raza r ON r.id_raza = m.id_raza
            JOIN especie e ON e.id_especie = r.id_especie
            JOIN cliente cl ON cl.id_cliente = m.id_cliente
            """;

    private final Db db;

    public MascotaClinicaDaoJdbc(Db db) {
        this.db = db;
    }

    static MascotaClinica mapear(ResultSet rs) throws SQLException {
        return new MascotaClinica(rs.getInt("id_mascota"), rs.getString("nombre"), rs.getString("especie"),
                rs.getString("raza"), rs.getString("sexo"), Jdbc.fecha(rs, "fecha_nacimiento"), rs.getBigDecimal("peso_kg"),
                rs.getString("color"), rs.getString("microchip"), rs.getBoolean("esterilizado"), rs.getString("alertas"),
                rs.getString("estado"), rs.getInt("id_cliente"), rs.getString("cliente"), rs.getString("tipo_documento"),
                rs.getString("num_documento"), rs.getString("telefono"), rs.getString("correo"),
                rs.getBoolean("acepta_recordatorios"), Jdbc.fechaHora(rs, "ultima_consulta"));
    }

    @Override
    public List<MascotaClinica> buscar(String texto, int limite) throws SQLException {
        if (StringUtils.isBlank(texto)) {
            // Sin texto: pacientes atendidos recientemente (y luego los últimos registrados)
            return db.consultar(cn -> Jdbc.lista(cn, "SELECT * FROM (" + SELECT + """
                    ) x ORDER BY x.ultima_consulta DESC NULLS LAST, x.id_mascota DESC LIMIT ?
                    """, MascotaClinicaDaoJdbc::mapear, limite));
        }
        String t = texto.trim();
        String patron = "%" + t + "%";
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                 WHERE m.nombre ILIKE ? OR cl.num_documento = ? OR m.microchip = ?
                    OR (cl.nombres || ' ' || coalesce(cl.apellidos, '')) ILIKE ?
                 ORDER BY (m.estado = 'ACTIVA') DESC, m.nombre, cliente LIMIT ?
                """, MascotaClinicaDaoJdbc::mapear, patron, t, t, patron, limite));
    }

    @Override
    public Optional<MascotaClinica> buscarPorId(int idMascota) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE m.id_mascota = ?", MascotaClinicaDaoJdbc::mapear, idMascota));
    }
}
