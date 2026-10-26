package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Pago de una venta (RF-28). Tarjeta, Yape/Plin y transferencia exigen el n.° de operación. */
public record Pago(Long idPago, long idVenta, String medio, BigDecimal monto, String referencia,
                   LocalDateTime fechaHora) {

    public static final String EFECTIVO = "EFECTIVO";
    /** Medios de pago del tipo medio_pago. */
    public static final List<String> MEDIOS = List.of(EFECTIVO, "TARJETA", "YAPE_PLIN", "TRANSFERENCIA");

    public boolean esEfectivo() {
        return EFECTIVO.equals(medio);
    }

    public Pago conId(long id) {
        return new Pago(id, idVenta, medio, monto, referencia, fechaHora);
    }
}
