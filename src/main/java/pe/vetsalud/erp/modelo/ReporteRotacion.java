package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/**
 * Rotación de un producto en el periodo (RF-34).
 *
 * @param vendidas unidades vendidas netas (salidas por VENTA − DEVOLUCION por anulación) en el periodo
 * @param stock    stock actual sumando todos los lotes
 */
public record ReporteRotacion(String codigo, String producto, String categoria, String unidad, BigDecimal vendidas,
                              BigDecimal stock) {
}
