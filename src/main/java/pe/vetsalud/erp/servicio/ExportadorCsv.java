package pe.vetsalud.erp.servicio;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import pe.vetsalud.erp.modelo.FilaReporte;
import pe.vetsalud.erp.modelo.ReporteColumna;
import pe.vetsalud.erp.modelo.ReporteTabla;

/**
 * Exporta un reporte a CSV (RFC 4180) con Apache Commons Text ({@code StringEscapeUtils.escapeCsv}).
 * <ul>
 *   <li>UTF-8 con BOM para que Excel muestre bien tildes y eñes.</li>
 *   <li>Cabecera con título, emisión, usuario y filtros; luego encabezados, filas, totales y anexos.</li>
 *   <li>Defensa contra inyección de fórmulas (CSV injection): un texto que empieza con = + - @ se antepone
 *       con un apóstrofo para que la hoja de cálculo no lo ejecute.</li>
 *   <li>Números con punto decimal y sin separador de miles (legibles por cualquier programa).</li>
 * </ul>
 */
public class ExportadorCsv implements ExportadorReporte {

    private static final String FIN = "\r\n";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public String formato() {
        return "csv";
    }

    @Override
    public String tipoContenido() {
        return "text/csv; charset=UTF-8";
    }

    @Override
    public String extension() {
        return "csv";
    }

    @Override
    public void exportar(ReporteTabla reporte, String emitidoPor, LocalDateTime emision, OutputStream salida)
            throws IOException {
        Writer w = new OutputStreamWriter(salida, StandardCharsets.UTF_8);
        w.write('﻿');
        linea(w, List.of(texto("VetSalud ERP · " + reporte.titulo())));
        linea(w, List.of(texto("Emitido el"), texto(FECHA_HORA.format(emision))));
        linea(w, List.of(texto("Emitido por"), texto(emitidoPor)));
        for (Map.Entry<String, String> f : reporte.filtros().entrySet()) {
            linea(w, List.of(texto("Filtro: " + f.getKey()), texto(f.getValue())));
        }
        w.write(FIN);
        tabla(w, reporte);
        for (ReporteTabla anexo : reporte.anexos()) {
            w.write(FIN);
            linea(w, List.of(texto(anexo.titulo())));
            tabla(w, anexo);
        }
        if (!reporte.notas().isEmpty()) {
            w.write(FIN);
            linea(w, List.of(texto("Notas")));
            for (String nota : reporte.notas()) {
                linea(w, List.of(texto(nota)));
            }
        }
        w.flush();
    }

    private void tabla(Writer w, ReporteTabla t) throws IOException {
        linea(w, t.encabezados().stream().map(ExportadorCsv::texto).toList());
        if (t.vacia()) {
            linea(w, List.of(texto("Sin registros para los filtros elegidos")));
        }
        for (FilaReporte fila : t.filas()) {
            linea(w, celdas(t.columnas(), fila));
        }
        if (t.totales() != null) {
            linea(w, celdas(t.columnas(), t.totales()));
        }
    }

    private static List<String> celdas(List<ReporteColumna> columnas, FilaReporte fila) {
        List<String> salida = new ArrayList<>(columnas.size());
        for (int i = 0; i < columnas.size(); i++) {
            salida.add(celda(fila.valor(i), columnas.get(i).tipo()));
        }
        return salida;
    }

    /** Valor ya escapado para CSV según su tipo. */
    static String celda(Object valor, ReporteColumna.Tipo tipo) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof BigDecimal n) {
            return tipo == ReporteColumna.Tipo.MONEDA
                    ? n.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    : n.stripTrailingZeros().toPlainString();
        }
        if (valor instanceof Number n) {
            return n.toString();
        }
        if (valor instanceof LocalDateTime dt) {
            return dt.format(FECHA_HORA);
        }
        if (valor instanceof LocalDate d) {
            return d.format(FECHA);
        }
        return texto(valor.toString());
    }

    /** Texto escapado y neutralizado frente a fórmulas (=, +, -, @, tabulador, retorno). */
    static String texto(String valor) {
        String v = StringUtils.defaultString(valor);
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return StringEscapeUtils.escapeCsv(v);
    }

    private static void linea(Writer w, List<String> celdas) throws IOException {
        w.write(String.join(",", celdas));
        w.write(FIN);
    }
}
