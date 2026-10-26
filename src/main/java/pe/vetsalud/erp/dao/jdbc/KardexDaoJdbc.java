package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pe.vetsalud.erp.dao.KardexDao;
import pe.vetsalud.erp.modelo.AjusteInventario;
import pe.vetsalud.erp.modelo.KardexFiltro;
import pe.vetsalud.erp.modelo.LoteAlerta;
import pe.vetsalud.erp.modelo.LoteConciliacion;
import pe.vetsalud.erp.modelo.MovimientoKardex;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link KardexDao}. El kardex es de solo inserción (tg_inmutable_kardex):
 * los ajustes se registran como movimientos nuevos y nunca se edita uno anterior.
 */
public class KardexDaoJdbc implements KardexDao {

    private static final String SELECT = """
            SELECT k.id_movimiento, k.codigo, k.producto, k.codigo_lote, k.fecha_vencimiento, k.fecha_hora,
                   k.tipo::text AS tipo, k.entrada, k.salida, k.saldo_lote, k.motivo, u.usuario
            FROM v_kardex k
            JOIN movimiento_inventario mi ON mi.id_movimiento = k.id_movimiento
            JOIN lote l ON l.id_lote = mi.id_lote
            JOIN usuario u ON u.id_usuario = mi.id_usuario
            """;

    private final Db db;

    public KardexDaoJdbc(Db db) {
        this.db = db;
    }

    private static MovimientoKardex mapear(ResultSet rs) throws SQLException {
        return new MovimientoKardex(rs.getLong("id_movimiento"), rs.getString("codigo"), rs.getString("producto"),
                rs.getString("codigo_lote"), Jdbc.fecha(rs, "fecha_vencimiento"), Jdbc.fechaHora(rs, "fecha_hora"),
                rs.getString("tipo"), rs.getBigDecimal("entrada"), rs.getBigDecimal("salida"),
                rs.getBigDecimal("saldo_lote"), rs.getString("motivo"), rs.getString("usuario"));
    }

    @Override
    public List<MovimientoKardex> movimientos(KardexFiltro f, int limite) throws SQLException {
        // Se toman los N más recientes y se devuelven en orden cronológico (lectura natural del kardex)
        return db.consultar(cn -> Jdbc.lista(cn, "SELECT * FROM (" + SELECT + """
                 WHERE (?::int IS NULL OR l.id_producto = ?::int)
                   AND (?::int IS NULL OR l.id_lote = ?::int)
                   AND (?::date IS NULL OR k.fecha_hora >= ?::date)
                   AND (?::date IS NULL OR k.fecha_hora < ?::date + 1)
                 ORDER BY k.fecha_hora DESC, k.id_movimiento DESC LIMIT ?
                ) x ORDER BY x.fecha_hora, x.id_movimiento
                """, KardexDaoJdbc::mapear, f.idProducto(), f.idProducto(), f.idLote(), f.idLote(),
                f.desde(), f.desde(), f.hasta(), f.hasta(), limite));
    }

    @Override
    public List<MovimientoKardex> ajustesRecientes(int limite) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT
                + " WHERE k.tipo IN ('AJUSTE_ENTRADA', 'AJUSTE_SALIDA') ORDER BY k.fecha_hora DESC LIMIT ?",
                KardexDaoJdbc::mapear, limite));
    }

    @Override
    public List<LoteConciliacion> conciliacion(int idProducto) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT l.id_lote, l.codigo_lote, l.fecha_vencimiento, l.stock_actual,
                       coalesce((SELECT sum(CASE WHEN mi.tipo IN ('INGRESO', 'AJUSTE_ENTRADA', 'DEVOLUCION')
                                                 THEN mi.cantidad ELSE -mi.cantidad END)
                                 FROM movimiento_inventario mi WHERE mi.id_lote = l.id_lote), 0) AS saldo_kardex
                FROM lote l WHERE l.id_producto = ?
                ORDER BY l.fecha_vencimiento NULLS LAST, l.id_lote
                """, rs -> new LoteConciliacion(rs.getInt("id_lote"), rs.getString("codigo_lote"),
                        Jdbc.fecha(rs, "fecha_vencimiento"), rs.getBigDecimal("stock_actual"),
                        rs.getBigDecimal("saldo_kardex")), idProducto));
    }

    @Override
    public long registrarAjuste(AjusteInventario a, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.insertar(cn, """
                INSERT INTO movimiento_inventario (id_lote, tipo, cantidad, id_usuario, motivo)
                VALUES (?, ?::tipo_movimiento, ?, ?, ?) RETURNING id_movimiento
                """, a.idLote(), a.tipo(), a.cantidad(), idUsuario, a.textoMotivo()));
    }

    @Override
    public List<LoteAlerta> alertas() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT p.id_producto, a.codigo, a.nombre, a.alerta, coalesce(a.stock, 0) AS stock, a.vence,
                       p.stock_minimo,
                       (SELECT string_agg(l.codigo_lote, ', ' ORDER BY l.codigo_lote) FROM lote l
                        WHERE a.vence IS NOT NULL AND l.id_producto = p.id_producto
                          AND l.fecha_vencimiento = a.vence AND l.stock_actual > 0) AS lotes
                FROM v_alerta_inventario a
                JOIN producto p ON p.codigo = a.codigo
                ORDER BY CASE a.alerta WHEN 'VENCIDO' THEN 0 WHEN 'STOCK_MINIMO' THEN 1 ELSE 2 END,
                         a.vence NULLS LAST, a.nombre
                """, rs -> new LoteAlerta(rs.getInt("id_producto"), rs.getString("codigo"), rs.getString("nombre"),
                        rs.getString("alerta"), rs.getBigDecimal("stock"), Jdbc.fecha(rs, "vence"),
                        rs.getString("lotes"), rs.getBigDecimal("stock_minimo"))));
    }

    @Override
    public Set<String> alertasPendientes(String rol) throws SQLException {
        return db.consultar(cn -> new HashSet<>(Jdbc.lista(cn, """
                SELECT a.tipo || '|' || a.mensaje FROM alerta a JOIN rol r ON r.id_rol = a.id_rol_destino
                WHERE r.nombre = ? AND a.atendida_en IS NULL
                """, rs -> rs.getString(1), rol)));
    }

    @Override
    public void crearAlerta(String rol, String tipo, String mensaje, int idUsuario) throws SQLException {
        // NOT EXISTS: segunda barrera contra duplicados si dos usuarios generan alertas a la vez
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                INSERT INTO alerta (id_rol_destino, tipo, mensaje)
                SELECT r.id_rol, ?, ? FROM rol r
                WHERE r.nombre = ?
                  AND NOT EXISTS (SELECT 1 FROM alerta a WHERE a.id_rol_destino = r.id_rol AND a.tipo = ?
                                  AND a.mensaje = ? AND a.atendida_en IS NULL)
                """, tipo, mensaje, rol, tipo, mensaje));
    }
}
