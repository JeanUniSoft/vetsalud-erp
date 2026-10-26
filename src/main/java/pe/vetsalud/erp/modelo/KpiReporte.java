package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/**
 * Indicador (tarjeta KPI) de un reporte para el periodo filtrado (RF-32).
 *
 * @param formato MONEDA, ENTERO, DECIMAL o PORCENTAJE: la vista aplica el formato peruano correspondiente
 */
public record KpiReporte(String etiqueta, BigDecimal valor, String formato, String detalle, boolean destacado) {

    public static KpiReporte moneda(String etiqueta, BigDecimal valor, String detalle) {
        return new KpiReporte(etiqueta, valor, "MONEDA", detalle, false);
    }

    public static KpiReporte entero(String etiqueta, long valor, String detalle) {
        return new KpiReporte(etiqueta, BigDecimal.valueOf(valor), "ENTERO", detalle, false);
    }

    public static KpiReporte decimal(String etiqueta, BigDecimal valor, String detalle) {
        return new KpiReporte(etiqueta, valor, "DECIMAL", detalle, false);
    }

    public static KpiReporte porcentaje(String etiqueta, BigDecimal valor, String detalle) {
        return new KpiReporte(etiqueta, valor, "PORCENTAJE", detalle, false);
    }

    public KpiReporte resaltado() {
        return new KpiReporte(etiqueta, valor, formato, detalle, true);
    }
}
