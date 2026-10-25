package pe.vetsalud.erp.dao.jdbc;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.dao.ParametroDao;
import pe.vetsalud.erp.modelo.Parametro;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

public class ParametroDaoJdbc implements ParametroDao {

    private final Db db;

    public ParametroDaoJdbc(Db db) {
        this.db = db;
    }

    @Override
    public Optional<String> valor(String clave) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, "SELECT valor FROM parametro WHERE clave = ?",
                rs -> rs.getString(1), clave));
    }

    @Override
    public List<Parametro> todos() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn,
                "SELECT clave, valor, descripcion, actualizado_en FROM parametro ORDER BY clave",
                rs -> new Parametro(rs.getString(1), rs.getString(2), rs.getString(3), Jdbc.fechaHora(rs, "actualizado_en"))));
    }

    @Override
    public void actualizar(String clave, String valor, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn,
                "UPDATE parametro SET valor = ?, actualizado_en = now() WHERE clave = ?", valor, clave));
    }
}
