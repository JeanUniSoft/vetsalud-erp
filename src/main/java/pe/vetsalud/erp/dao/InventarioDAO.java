package pe.vetsalud.erp.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.modelo.LoteStock;

/** Consulta de stock por lote sobre la vista v_stock_lote (solo lectura). */
public class InventarioDAO {

    private static final Logger LOG = LoggerFactory.getLogger(InventarioDAO.class);

    private static final String SQL_STOCK = """
            SELECT codigo, producto, categoria, unidad, codigo_lote, fecha_vencimiento,
                   stock_actual, stock_minimo, stock_disponible_producto, costo_unitario
            FROM vetsalud.v_stock_lote
            ORDER BY categoria, producto, fecha_vencimiento NULLS LAST""";

    public List<LoteStock> listarStockPorLote(Connection cn) throws SQLException {
        long inicio = System.nanoTime();
        List<LoteStock> lotes = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(SQL_STOCK);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lotes.add(new LoteStock(
                        rs.getString("codigo"),
                        rs.getString("producto"),
                        rs.getString("categoria"),
                        rs.getString("unidad"),
                        rs.getString("codigo_lote"),
                        rs.getObject("fecha_vencimiento", java.time.LocalDate.class),
                        rs.getBigDecimal("stock_actual"),
                        rs.getBigDecimal("stock_minimo"),
                        rs.getBigDecimal("stock_disponible_producto"),
                        rs.getBigDecimal("costo_unitario")));
            }
        }
        LOG.debug("v_stock_lote: {} lotes leídos en {} ms", lotes.size(), (System.nanoTime() - inicio) / 1_000_000);
        return lotes;
    }
}
