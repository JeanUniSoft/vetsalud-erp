package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Venta anulada del periodo: no suma a los totales y se muestra aparte (RF-33, RN-09). */
public record ReporteVentaAnulada(String comprobante, LocalDateTime fecha, String cajero, BigDecimal total,
                                  String motivo, LocalDateTime anuladaEn) {
}
