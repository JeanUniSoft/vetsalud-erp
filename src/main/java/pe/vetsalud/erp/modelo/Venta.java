package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Venta del punto de venta (RF-26 a RF-31). Nace PENDIENTE en el turno abierto del cajero,
 * se confirma con fn_confirmar_venta (asigna correlativo, RN-11) y solo se anula, nunca se borra (RN-09).
 *
 * @param pagado suma de los pagos registrados (RN-12: debe igualar al total para confirmar)
 */
public record Venta(long idVenta, int idTurno, int idCajero, String cajero, boolean turnoAbierto, int idCliente,
                    String cliente, String documentoCliente, Integer idMascota, String mascota, Long idConsulta,
                    String serie, Integer numero, LocalDateTime fechaHora, BigDecimal subtotal, BigDecimal descuento,
                    BigDecimal total, Integer idAutorizaDescuento, String autorizaDescuento, String estado,
                    String motivoAnulacion, LocalDateTime anuladaEn, BigDecimal pagado, int numLineas) {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String ANULADA = "ANULADA";

    /** Número de comprobante interno, p. ej. T001-00000003 (null si aún no se confirma). */
    public String comprobante() {
        return numero == null ? null : serie.trim() + "-" + String.format("%08d", numero);
    }

    /** Lo que falta pagar (negativo si se pagó de más). */
    public BigDecimal saldo() {
        return total.subtract(pagado);
    }

    public boolean esPendiente() {
        return PENDIENTE.equals(estado);
    }

    public boolean esConfirmada() {
        return CONFIRMADA.equals(estado);
    }

    public boolean esAnulada() {
        return ANULADA.equals(estado);
    }
}
