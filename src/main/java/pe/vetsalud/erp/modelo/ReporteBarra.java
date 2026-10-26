package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/**
 * Barra del gráfico SVG de un reporte.
 *
 * @param etiqueta   texto abreviado para el eje
 * @param completo   texto completo (se muestra como título accesible de la barra)
 * @param porcentaje largo de la barra (0-100) respecto del mayor valor
 */
public record ReporteBarra(String etiqueta, String completo, BigDecimal valor, int porcentaje) {

    /** Largo máximo de una barra en unidades del viewBox del SVG. */
    public static final int LARGO_MAXIMO = 390;

    /** Largo de la barra en unidades del viewBox (entero: sin decimales en los atributos del SVG). */
    public int largo() {
        return porcentaje * LARGO_MAXIMO / 100;
    }
}
