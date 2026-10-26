package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Totales de ventas del periodo (RF-33). Solo las CONFIRMADAS suman; las ANULADAS se informan aparte.
 * Conciliación: servicios + productos − descuentos = total.
 */
public record ReporteVentasResumen(long confirmadas, BigDecimal total, BigDecimal subtotal, BigDecimal descuentos,
                                   BigDecimal servicios, BigDecimal productos, long anuladas,
                                   BigDecimal montoAnulado) {

    public static final ReporteVentasResumen VACIO = new ReporteVentasResumen(0, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO);

    public BigDecimal ticketPromedio() {
        return confirmadas == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(confirmadas), 2, RoundingMode.HALF_UP);
    }
}
