package pe.vetsalud.erp.dao.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import pe.vetsalud.erp.dao.ProductoDao;
import pe.vetsalud.erp.modelo.Lote;
import pe.vetsalud.erp.modelo.Producto;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link ProductoDao}. La auditoría de producto la registra el trigger tg_aud_producto.
 * El stock se lee de los lotes; la aplicación nunca lo actualiza directamente (privilegio por columna, RN-06).
 */
public class ProductoDaoJdbc implements ProductoDao {

    /** Producto + stock por lotes. El primer parámetro es DIAS_POR_VENCER. */
    private static final String SELECT = """
            SELECT p.id_producto, p.id_categoria, c.nombre AS categoria, p.codigo, p.nombre, p.unidad, p.precio_venta,
                   p.stock_minimo, p.requiere_receta, p.activo,
                   coalesce(s.disponible, 0) AS stock_disponible, coalesce(s.total, 0) AS stock_total,
                   coalesce(s.por_vencer, 0) AS por_vencer, coalesce(s.vencidos, 0) AS vencidos
            FROM producto p
            JOIN categoria c ON c.id_categoria = p.id_categoria
            LEFT JOIN LATERAL (
                SELECT sum(l.stock_actual) FILTER (WHERE l.fecha_vencimiento IS NULL
                                                      OR l.fecha_vencimiento >= CURRENT_DATE) AS disponible,
                       sum(l.stock_actual) AS total,
                       count(*) FILTER (WHERE l.stock_actual > 0 AND l.fecha_vencimiento >= CURRENT_DATE
                                          AND l.fecha_vencimiento < CURRENT_DATE + ?::int) AS por_vencer,
                       count(*) FILTER (WHERE l.stock_actual > 0 AND l.fecha_vencimiento < CURRENT_DATE) AS vencidos
                FROM lote l WHERE l.id_producto = p.id_producto
            ) s ON TRUE
            """;

    private static final String SELECT_LOTE = """
            SELECT l.id_lote, l.id_producto, l.codigo_lote, l.fecha_vencimiento, l.costo_unitario, l.stock_actual,
                   CASE WHEN l.fecha_vencimiento < CURRENT_DATE THEN 'VENCIDO'
                        WHEN l.stock_actual = 0 THEN 'AGOTADO'
                        WHEN l.fecha_vencimiento < CURRENT_DATE + ?::int THEN 'POR_VENCER'
                        ELSE 'VIGENTE' END AS condicion
            FROM lote l
            """;

    private final Db db;

    public ProductoDaoJdbc(Db db) {
        this.db = db;
    }

    static Producto mapear(ResultSet rs) throws SQLException {
        return new Producto(rs.getInt("id_producto"), rs.getInt("id_categoria"), rs.getString("categoria"),
                rs.getString("codigo"), rs.getString("nombre"), rs.getString("unidad"), rs.getBigDecimal("precio_venta"),
                rs.getBigDecimal("stock_minimo"), rs.getBoolean("requiere_receta"), rs.getBoolean("activo"),
                rs.getBigDecimal("stock_disponible"), rs.getBigDecimal("stock_total"), rs.getInt("por_vencer"),
                rs.getInt("vencidos"));
    }

    static Lote mapearLote(ResultSet rs) throws SQLException {
        return new Lote(rs.getInt("id_lote"), rs.getInt("id_producto"), rs.getString("codigo_lote"),
                Jdbc.fecha(rs, "fecha_vencimiento"), rs.getBigDecimal("costo_unitario"),
                rs.getBigDecimal("stock_actual"), rs.getString("condicion"));
    }

    @Override
    public List<Producto> buscar(String texto, Integer idCategoria, boolean soloAlertas, boolean incluirInactivos,
                                 int diasPorVencer, int limite) throws SQLException {
        String t = StringUtils.trimToNull(texto);
        String patron = t == null ? null : "%" + t + "%";
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                 WHERE (?::text IS NULL OR p.codigo ILIKE ? OR p.nombre ILIKE ?)
                   AND (?::int IS NULL OR p.id_categoria = ?::int)
                   AND (? OR p.activo)
                   AND (NOT ? OR (p.activo AND (coalesce(s.total, 0) <= p.stock_minimo
                                                OR s.por_vencer > 0 OR s.vencidos > 0)))
                 ORDER BY p.activo DESC, c.nombre, p.nombre
                 LIMIT ?
                """, ProductoDaoJdbc::mapear, diasPorVencer, patron, patron, patron, idCategoria, idCategoria,
                incluirInactivos, soloAlertas, limite));
    }

    @Override
    public Optional<Producto> buscarPorId(int idProducto, int diasPorVencer) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE p.id_producto = ?", ProductoDaoJdbc::mapear,
                diasPorVencer, idProducto));
    }

    @Override
    public boolean existeCodigo(String codigo, Integer exceptoId) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn, """
                SELECT EXISTS (SELECT 1 FROM producto WHERE upper(codigo) = upper(?)
                               AND (?::int IS NULL OR id_producto <> ?::int))
                """, Boolean.class, codigo, exceptoId, exceptoId));
    }

    @Override
    public int insertar(Producto p, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> (int) Jdbc.insertar(cn, """
                INSERT INTO producto (id_categoria, codigo, nombre, unidad, precio_venta, stock_minimo, requiere_receta)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_producto
                """, p.idCategoria(), p.codigo(), p.nombre(), p.unidad(), p.precioVenta(), p.stockMinimo(),
                p.requiereReceta()));
    }

    @Override
    public void actualizar(Producto p, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                UPDATE producto SET id_categoria = ?, codigo = ?, nombre = ?, unidad = ?, precio_venta = ?,
                       stock_minimo = ?, requiere_receta = ?, activo = ?
                WHERE id_producto = ?
                """, p.idCategoria(), p.codigo(), p.nombre(), p.unidad(), p.precioVenta(), p.stockMinimo(),
                p.requiereReceta(), p.activo(), p.idProducto()));
    }

    @Override
    public List<Lote> lotes(int idProducto, int diasPorVencer) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT_LOTE
                + " WHERE l.id_producto = ? ORDER BY l.fecha_vencimiento NULLS LAST, l.id_lote",
                ProductoDaoJdbc::mapearLote, diasPorVencer, idProducto));
    }

    @Override
    public Optional<Lote> lotePorId(int idLote, int diasPorVencer) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT_LOTE + " WHERE l.id_lote = ?", ProductoDaoJdbc::mapearLote,
                diasPorVencer, idLote));
    }

    @Override
    public Optional<Integer> loteSugerido(int idProducto, BigDecimal cantidad) throws SQLException {
        return db.consultar(cn -> Optional.ofNullable(Jdbc.escalar(cn, "SELECT fn_lote_sugerido(?, ?)",
                Integer.class, idProducto, cantidad)));
    }
}
