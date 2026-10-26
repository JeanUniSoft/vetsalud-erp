package pe.vetsalud.erp.modelo;

import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * Gráfico de barras horizontales de un reporte (SVG sin estilos en línea, CSP).
 *
 * @param tipo       formato de los valores (MONEDA, ENTERO, DECIMAL...)
 * @param categorias total de categorías antes de recortar a las barras visibles
 */
public record ReporteGrafico(String titulo, ReporteColumna.Tipo tipo, List<ReporteBarra> barras, int categorias) {

    /** Alto de cada fila del gráfico en unidades del viewBox. */
    public static final int ALTO_FILA = 30;

    public ReporteGrafico {
        barras = ImmutableList.copyOf(barras);
    }

    public boolean recortado() {
        return categorias > barras.size();
    }

    /** Alto total del viewBox del SVG. */
    public int alto() {
        return barras.size() * ALTO_FILA + 12;
    }
}
