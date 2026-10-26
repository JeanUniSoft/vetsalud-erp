package pe.vetsalud.erp.dao.jdbc;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.dao.IngresoDao;
import pe.vetsalud.erp.modelo.IngresoDocumento;
import pe.vetsalud.erp.modelo.IngresoResumen;
import pe.vetsalud.erp.modelo.LineaIngreso;
import pe.vetsalud.erp.modelo.Lote;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link IngresoDao}. Todo el documento va en una sola transacción (RNF-08):
 * si una línea falla no queda ni el ingreso ni ningún movimiento.
 */
public class IngresoDaoJdbc implements IngresoDao {

    private final Db db;

    public IngresoDaoJdbc(Db db) {
        this.db = db;
    }

    @Override
    public boolean existeDocumento(int idProveedor, String tipoDoc, String numDoc) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn, """
                SELECT EXISTS (SELECT 1 FROM ingreso WHERE id_proveedor = ? AND tipo_doc = ? AND upper(num_doc) = upper(?))
                """, Boolean.class, idProveedor, tipoDoc, numDoc));
    }

    @Override
    public Optional<Lote> lotePorCodigo(int idProducto, String codigoLote) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, """
                SELECT l.id_lote, l.id_producto, l.codigo_lote, l.fecha_vencimiento, l.costo_unitario, l.stock_actual,
                       'VIGENTE' AS condicion
                FROM lote l WHERE l.id_producto = ? AND upper(l.codigo_lote) = upper(?)
                """, ProductoDaoJdbc::mapearLote, idProducto, codigoLote));
    }

    @Override
    public long registrar(IngresoDocumento doc, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> {
            long idIngreso = Jdbc.insertar(cn, """
                    INSERT INTO ingreso (id_proveedor, id_usuario, tipo_doc, num_doc, fecha)
                    VALUES (?, ?, ?, ?, ?) RETURNING id_ingreso
                    """, doc.idProveedor(), idUsuario, doc.tipoDoc(), doc.numDoc(), doc.fecha());
            for (LineaIngreso l : doc.lineas()) {
                // FOR UPDATE: dos ingresos simultáneos del mismo lote no calculan el costo con datos viejos
                Optional<Integer> existente = Jdbc.uno(cn,
                        "SELECT id_lote FROM lote WHERE id_producto = ? AND upper(codigo_lote) = upper(?) FOR UPDATE",
                        rs -> rs.getInt(1), l.idProducto(), l.codigoLote());
                int idLote;
                if (existente.isPresent()) {
                    idLote = existente.get();
                    // Costo promedio ponderado del lote (solo se permite actualizar costo, código y vencimiento)
                    Jdbc.ejecutar(cn, """
                            UPDATE lote SET costo_unitario = round((stock_actual * costo_unitario + ? * ?)
                                                                   / (stock_actual + ?), 2)
                            WHERE id_lote = ?
                            """, l.cantidad(), l.costoUnitario(), l.cantidad(), idLote);
                } else {
                    idLote = (int) Jdbc.insertar(cn, """
                            INSERT INTO lote (id_producto, codigo_lote, fecha_vencimiento, costo_unitario)
                            VALUES (?, ?, ?, ?) RETURNING id_lote
                            """, l.idProducto(), l.codigoLote(), l.fechaVencimiento(), l.costoUnitario());
                }
                // El trigger fn_kardex_aplicar suma la cantidad al stock del lote y calcula el saldo
                Jdbc.ejecutar(cn, """
                        INSERT INTO movimiento_inventario (id_lote, tipo, cantidad, id_ingreso, id_usuario)
                        VALUES (?, 'INGRESO', ?, ?, ?)
                        """, idLote, l.cantidad(), idIngreso, idUsuario);
            }
            return idIngreso;
        });
    }

    @Override
    public List<IngresoResumen> recientes(int limite) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT i.id_ingreso, pr.razon_social, i.tipo_doc, i.num_doc, i.fecha, u.usuario,
                       count(mi.id_movimiento) AS lineas, coalesce(sum(mi.cantidad), 0) AS unidades
                FROM ingreso i
                JOIN proveedor pr ON pr.id_proveedor = i.id_proveedor
                JOIN usuario u ON u.id_usuario = i.id_usuario
                LEFT JOIN movimiento_inventario mi ON mi.id_ingreso = i.id_ingreso AND mi.tipo = 'INGRESO'
                GROUP BY i.id_ingreso, pr.id_proveedor, u.id_usuario
                ORDER BY i.id_ingreso DESC LIMIT ?
                """, rs -> new IngresoResumen(rs.getLong("id_ingreso"), rs.getString("razon_social"),
                        rs.getString("tipo_doc"), rs.getString("num_doc"), Jdbc.fecha(rs, "fecha"),
                        rs.getString("usuario"), rs.getInt("lineas"), rs.getBigDecimal("unidades")), limite));
    }
}
