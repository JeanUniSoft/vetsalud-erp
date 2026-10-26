package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.dao.ServicioDao;
import pe.vetsalud.erp.modelo.Servicio;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link ServicioDao}. */
public class ServicioDaoJdbc implements ServicioDao {

    private static final String SELECT = "SELECT id_servicio, nombre, tipo, duracion_min, tarifa, activo FROM servicio";

    private final Db db;

    public ServicioDaoJdbc(Db db) {
        this.db = db;
    }

    static Servicio mapear(ResultSet rs) throws SQLException {
        return new Servicio(rs.getInt("id_servicio"), rs.getString("nombre"), rs.getString("tipo"),
                rs.getInt("duracion_min"), rs.getBigDecimal("tarifa"), rs.getBoolean("activo"));
    }

    @Override
    public List<Servicio> activos() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + " WHERE activo ORDER BY tipo, nombre", ServicioDaoJdbc::mapear));
    }

    @Override
    public Optional<Servicio> buscarPorId(int idServicio) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE id_servicio = ?", ServicioDaoJdbc::mapear, idServicio));
    }
}
