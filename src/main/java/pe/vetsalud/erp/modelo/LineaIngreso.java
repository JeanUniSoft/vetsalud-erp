package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Línea de un documento de compra: producto, lote, vencimiento, cantidad y costo (RF-21). */
public record LineaIngreso(Integer idProducto, String codigoLote, LocalDate fechaVencimiento, BigDecimal cantidad,
                           BigDecimal costoUnitario) {
}
