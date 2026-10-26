package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/** Subtotal, descuento y autorizador de una venta pendiente (RN-08: total = subtotal − descuento). */
public record VentaTotales(BigDecimal subtotal, BigDecimal descuento, Integer idAutoriza) {

    public BigDecimal total() {
        return subtotal.subtract(descuento);
    }
}
