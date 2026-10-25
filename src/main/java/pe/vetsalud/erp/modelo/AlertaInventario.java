package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila de la vista v_alerta_inventario (RF-24): STOCK_MINIMO, POR_VENCER o VENCIDO. */
public record AlertaInventario(String codigo, String producto, String alerta, BigDecimal stock, LocalDate vence) {
}
