package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.dao.ProfesionalDao;
import pe.vetsalud.erp.modelo.Profesional;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link ProfesionalDao}: nunca lee la clave ni datos de acceso del usuario. */
public class ProfesionalDaoJdbc implements ProfesionalDao {

    private static final String SELECT = """
            SELECT u.id_usuario, u.nombres, u.apellidos, r.nombre AS rol, u.colegiatura_cmvp, u.activo AND NOT u.bloqueado AS activo
            FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
            """;

    private final Db db;

    public ProfesionalDaoJdbc(Db db) {
        this.db = db;
    }

    static Profesional mapear(ResultSet rs) throws SQLException {
        return new Profesional(rs.getInt("id_usuario"), rs.getString("nombres"), rs.getString("apellidos"),
                rs.getString("rol"), rs.getString("colegiatura_cmvp"), rs.getBoolean("activo"));
    }

    @Override
    public List<Profesional> veterinarios() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + " WHERE r.nombre = 'VETERINARIO' AND u.activo ORDER BY u.apellidos",
                ProfesionalDaoJdbc::mapear));
    }

    @Override
    public List<Profesional> personalClinico() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT
                + " WHERE r.nombre IN ('VETERINARIO', 'TECNICO') AND u.activo ORDER BY r.nombre DESC, u.apellidos",
                ProfesionalDaoJdbc::mapear));
    }

    @Override
    public Optional<Profesional> buscarPorId(int idUsuario) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE u.id_usuario = ?", ProfesionalDaoJdbc::mapear, idUsuario));
    }
}
