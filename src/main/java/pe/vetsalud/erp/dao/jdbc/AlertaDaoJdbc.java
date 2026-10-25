package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import pe.vetsalud.erp.dao.AlertaDao;
import pe.vetsalud.erp.modelo.Alerta;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

public class AlertaDaoJdbc implements AlertaDao {

    private static final String SELECT = """
            SELECT a.id_alerta, r.nombre AS rol, a.tipo, a.mensaje, a.creada_en,
                   u.nombres || ' ' || u.apellidos AS atendida_por, a.atendida_en
            FROM alerta a JOIN rol r ON r.id_rol = a.id_rol_destino
            LEFT JOIN usuario u ON u.id_usuario = a.atendida_por
            """;

    private final Db db;

    public AlertaDaoJdbc(Db db) {
        this.db = db;
    }

    private static Alerta mapear(ResultSet rs) throws SQLException {
        return new Alerta(rs.getLong("id_alerta"), rs.getString("rol"), rs.getString("tipo"), rs.getString("mensaje"),
                Jdbc.fechaHora(rs, "creada_en"), rs.getString("atendida_por"), Jdbc.fechaHora(rs, "atendida_en"));
    }

    @Override
    public void crear(String rolDestino, String tipo, String mensaje) throws SQLException {
        db.enTransaccion(null, cn -> Jdbc.ejecutar(cn, """
                INSERT INTO alerta (id_rol_destino, tipo, mensaje)
                SELECT id_rol, ?, ? FROM rol WHERE nombre = ?
                """, tipo, mensaje, rolDestino));
    }

    @Override
    public List<Alerta> pendientesPorRol(String rol) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT
                + " WHERE a.atendida_en IS NULL AND r.nombre = ? ORDER BY a.creada_en DESC", AlertaDaoJdbc::mapear, rol));
    }

    @Override
    public List<Alerta> recientes(int limite) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + " ORDER BY a.atendida_en IS NOT NULL, a.creada_en DESC LIMIT ?",
                AlertaDaoJdbc::mapear, limite));
    }

    @Override
    public boolean atender(long idAlerta, int idUsuario, String rol) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                UPDATE alerta SET atendida_por = ?, atendida_en = now()
                WHERE id_alerta = ? AND atendida_en IS NULL
                  AND (? = 'ADMINISTRADOR' OR id_rol_destino = (SELECT id_rol FROM rol WHERE nombre = ?))
                """, idUsuario, idAlerta, rol, rol) == 1);
    }
}
