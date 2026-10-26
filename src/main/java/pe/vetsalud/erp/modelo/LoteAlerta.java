package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila de la vista v_alerta_inventario con los lotes afectados (RF-24). */
public record LoteAlerta(int idProducto, String codigo, String producto, String alerta, BigDecimal stock,
                         LocalDate vence, String lotes, BigDecimal stockMinimo) {
}
