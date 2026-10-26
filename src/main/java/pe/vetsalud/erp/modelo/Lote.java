package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Lote de un producto (RF-21, RF-23). El stock lo mantiene el trigger del kardex.
 *
 * @param fechaVencimiento null para accesorios no perecibles
 * @param condicion        VENCIDO, POR_VENCER, AGOTADO o VIGENTE (calculada con DIAS_POR_VENCER)
 */
public record Lote(int idLote, int idProducto, String codigoLote, LocalDate fechaVencimiento,
                   BigDecimal costoUnitario, BigDecimal stockActual, String condicion) {

    /** RN-06: un lote vencido no se vende. Vence al terminar el día indicado (igual que fn_lote_sugerido). */
    public boolean vigenteEn(LocalDate dia) {
        return fechaVencimiento == null || !fechaVencimiento.isBefore(dia);
    }

    public Long diasParaVencer() {
        return fechaVencimiento == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), fechaVencimiento);
    }
}
