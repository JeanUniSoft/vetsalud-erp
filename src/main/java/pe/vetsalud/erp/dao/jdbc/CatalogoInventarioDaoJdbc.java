package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.dao.CatalogoInventarioDao;
import pe.vetsalud.erp.modelo.Categoria;
import pe.vetsalud.erp.modelo.Proveedor;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link CatalogoInventarioDao} (categorías y proveedores). */
public class CatalogoInventarioDaoJdbc implements CatalogoInventarioDao {

    private static final String SELECT_PROVEEDOR =
            "SELECT id_proveedor, ruc, razon_social, telefono, correo, activo FROM proveedor";

    private final Db db;

    public CatalogoInventarioDaoJdbc(Db db) {
        this.db = db;
    }

    private static Proveedor mapearProveedor(ResultSet rs) throws SQLException {
        return new Proveedor(rs.getInt("id_proveedor"), rs.getString("ruc"), rs.getString("razon_social"),
                rs.getString("telefono"), rs.getString("correo"), rs.getBoolean("activo"));
    }

    @Override
    public List<Categoria> categorias() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT c.id_categoria, c.nombre,
                       (SELECT count(*) FROM producto p WHERE p.id_categoria = c.id_categoria) AS num_productos
                FROM categoria c ORDER BY c.nombre
                """, rs -> new Categoria(rs.getInt("id_categoria"), rs.getString("nombre"), rs.getInt("num_productos"))));
    }

    @Override
    public boolean existeCategoria(String nombre, Integer exceptoId) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn, """
                SELECT EXISTS (SELECT 1 FROM categoria WHERE lower(nombre) = lower(?)
                               AND (?::int IS NULL OR id_categoria <> ?::int))
                """, Boolean.class, nombre, exceptoId, exceptoId));
    }

    @Override
    public int insertarCategoria(String nombre, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> (int) Jdbc.insertar(cn,
                "INSERT INTO categoria (nombre) VALUES (?) RETURNING id_categoria", nombre));
    }

    @Override
    public void renombrarCategoria(int idCategoria, String nombre, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn,
                "UPDATE categoria SET nombre = ? WHERE id_categoria = ?", nombre, idCategoria));
    }

    @Override
    public List<Proveedor> proveedores(boolean soloActivos) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT_PROVEEDOR
                + " WHERE (NOT ? OR activo) ORDER BY activo DESC, razon_social",
                CatalogoInventarioDaoJdbc::mapearProveedor, soloActivos));
    }

    @Override
    public Optional<Proveedor> proveedorPorId(int idProveedor) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT_PROVEEDOR + " WHERE id_proveedor = ?",
                CatalogoInventarioDaoJdbc::mapearProveedor, idProveedor));
    }

    @Override
    public boolean existeRuc(String ruc, Integer exceptoId) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn, """
                SELECT EXISTS (SELECT 1 FROM proveedor WHERE ruc = ? AND (?::int IS NULL OR id_proveedor <> ?::int))
                """, Boolean.class, ruc, exceptoId, exceptoId));
    }

    @Override
    public int insertarProveedor(Proveedor p, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> (int) Jdbc.insertar(cn, """
                INSERT INTO proveedor (ruc, razon_social, telefono, correo) VALUES (?, ?, ?, ?) RETURNING id_proveedor
                """, p.ruc(), p.razonSocial(), p.telefono(), p.correo()));
    }

    @Override
    public void actualizarProveedor(Proveedor p, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                UPDATE proveedor SET ruc = ?, razon_social = ?, telefono = ?, correo = ?, activo = ?
                WHERE id_proveedor = ?
                """, p.ruc(), p.razonSocial(), p.telefono(), p.correo(), p.activo(), p.idProveedor()));
    }
}
