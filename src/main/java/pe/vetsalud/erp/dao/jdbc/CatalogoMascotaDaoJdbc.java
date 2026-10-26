package pe.vetsalud.erp.dao.jdbc;

import java.sql.SQLException;
import java.util.List;

import pe.vetsalud.erp.dao.CatalogoMascotaDao;
import pe.vetsalud.erp.modelo.Especie;
import pe.vetsalud.erp.modelo.Raza;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link CatalogoMascotaDao} (tablas especie y raza). */
public class CatalogoMascotaDaoJdbc implements CatalogoMascotaDao {

    private final Db db;

    public CatalogoMascotaDaoJdbc(Db db) {
        this.db = db;
    }

    @Override
    public List<Especie> especies() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, "SELECT id_especie, nombre FROM especie ORDER BY id_especie",
                rs -> new Especie(rs.getInt("id_especie"), rs.getString("nombre"))));
    }

    @Override
    public List<Raza> razas() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT r.id_raza, r.id_especie, e.nombre AS especie, r.nombre
                FROM raza r JOIN especie e ON e.id_especie = r.id_especie
                ORDER BY e.id_especie, r.nombre
                """, rs -> new Raza(rs.getInt("id_raza"), rs.getInt("id_especie"), rs.getString("especie"),
                        rs.getString("nombre"))));
    }
}
