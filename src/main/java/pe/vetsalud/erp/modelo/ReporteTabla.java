package pe.vetsalud.erp.modelo;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Reporte genérico listo para mostrar o exportar (RF-36): título, filtros aplicados (en orden),
 * columnas tipadas, filas, fila de totales opcional, notas y tablas anexas (p. ej. ventas anuladas).
 * Es independiente del formato: lo consumen la vista JSP y cada {@code ExportadorReporte}.
 *
 * @param codigo  identificador corto (ventas, inventario, clinica) usado en el nombre del archivo
 * @param grafico barras del gráfico SVG de la pantalla (null = sin gráfico; los exportadores lo ignoran)
 */
public record ReporteTabla(String codigo, String titulo, Map<String, String> filtros, List<ReporteColumna> columnas,
                           List<FilaReporte> filas, FilaReporte totales, List<String> notas,
                           List<ReporteTabla> anexos, ReporteGrafico grafico) {

    public ReporteTabla {
        filtros = filtros == null ? ImmutableMap.of() : ImmutableMap.copyOf(filtros);
        columnas = ImmutableList.copyOf(columnas);
        filas = ImmutableList.copyOf(filas);
        notas = notas == null ? ImmutableList.of() : ImmutableList.copyOf(notas);
        anexos = anexos == null ? ImmutableList.of() : ImmutableList.copyOf(anexos);
    }

    /** Tabla anexa: sin filtros propios (usa los del reporte principal) ni gráfico. */
    public static ReporteTabla anexo(String titulo, List<ReporteColumna> columnas, List<FilaReporte> filas,
                                     FilaReporte totales) {
        return new ReporteTabla(null, titulo, null, columnas, filas, totales, null, null, null);
    }

    public List<String> encabezados() {
        return columnas.stream().map(ReporteColumna::titulo).toList();
    }

    public boolean vacia() {
        return filas.isEmpty();
    }

    public boolean conGrafico() {
        return grafico != null && !grafico.barras().isEmpty();
    }
}
