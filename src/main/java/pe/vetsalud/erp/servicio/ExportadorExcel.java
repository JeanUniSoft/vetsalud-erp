package pe.vetsalud.erp.servicio;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import pe.vetsalud.erp.modelo.FilaReporte;
import pe.vetsalud.erp.modelo.ReporteColumna;
import pe.vetsalud.erp.modelo.ReporteTabla;

/**
 * Exporta un reporte a Excel .xlsx con Apache POI (RF-36): una hoja por tabla (principal y anexos),
 * cabecera con emisión, usuario y filtros, encabezados en negrita, formato de moneda PEN y fecha
 * dd/mm/aaaa (RNF-17), anchos de columna calculados, filtro automático y panel inmovilizado.
 */
public class ExportadorExcel implements ExportadorReporte {

    /** Ancho máximo y mínimo de columna, en caracteres. */
    private static final int ANCHO_MIN = 10;
    private static final int ANCHO_MAX = 60;
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public String formato() {
        return "xlsx";
    }

    @Override
    public String tipoContenido() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public String extension() {
        return "xlsx";
    }

    @Override
    public void exportar(ReporteTabla reporte, String emitidoPor, LocalDateTime emision, OutputStream salida)
            throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Estilos e = new Estilos(libro);
            Set<String> nombres = new HashSet<>();
            hoja(libro, e, nombres, reporte, reporte, emitidoPor, emision);
            for (ReporteTabla anexo : reporte.anexos()) {
                hoja(libro, e, nombres, anexo, reporte, emitidoPor, emision);
            }
            libro.getProperties().getCoreProperties().setCreator("VetSalud ERP");
            libro.getProperties().getCoreProperties().setTitle(reporte.titulo());
            libro.getProperties().getCoreProperties().setDescription("Emitido por " + emitidoPor);
            libro.write(salida);
        }
    }

    private void hoja(XSSFWorkbook libro, Estilos e, Set<String> nombres, ReporteTabla t, ReporteTabla principal,
                      String emitidoPor, LocalDateTime emision) {
        Sheet hoja = libro.createSheet(nombreHoja(t.titulo(), nombres));
        int columnas = Math.max(2, t.columnas().size());
        int r = 0;
        texto(hoja.createRow(r++), 0, "VetSalud ERP · " + t.titulo(), e.titulo);
        hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, columnas - 1));
        par(hoja.createRow(r++), "Emitido el", FECHA_HORA.format(emision) + " (hora de Lima)", e);
        par(hoja.createRow(r++), "Emitido por", emitidoPor, e);
        for (Map.Entry<String, String> f : principal.filtros().entrySet()) {
            par(hoja.createRow(r++), f.getKey(), f.getValue(), e);
        }
        r++;                                                     // línea en blanco
        int filaEncabezado = r;
        Row enc = hoja.createRow(r++);
        enc.setHeightInPoints(20);
        int[] anchos = new int[t.columnas().size()];
        for (int c = 0; c < t.columnas().size(); c++) {
            String titulo = t.columnas().get(c).titulo();
            texto(enc, c, titulo, e.encabezado);
            anchos[c] = titulo.length();
        }
        if (t.vacia()) {
            texto(hoja.createRow(r++), 0, "Sin registros para los filtros elegidos", e.texto);
        }
        for (FilaReporte fila : t.filas()) {
            escribirFila(hoja.createRow(r++), t.columnas(), fila, e, false, anchos);
        }
        int ultimaDatos = r - 1;
        if (t.totales() != null) {
            escribirFila(hoja.createRow(r++), t.columnas(), t.totales(), e, true, anchos);
        }
        if (t == principal && !t.notas().isEmpty()) {
            r++;
            for (String nota : t.notas()) {
                texto(hoja.createRow(r++), 0, nota, e.nota);
            }
        }
        for (int c = 0; c < anchos.length; c++) {
            hoja.setColumnWidth(c, (Math.min(ANCHO_MAX, Math.max(ANCHO_MIN, anchos[c])) + 2) * 256);
        }
        if (!t.columnas().isEmpty()) {
            hoja.createFreezePane(0, filaEncabezado + 1);
            if (!t.vacia()) {
                hoja.setAutoFilter(new CellRangeAddress(filaEncabezado, ultimaDatos, 0, t.columnas().size() - 1));
            }
        }
        hoja.getPrintSetup().setLandscape(true);
    }

    private static void escribirFila(Row fila, List<ReporteColumna> columnas, FilaReporte datos, Estilos e,
                                     boolean total, int[] anchos) {
        for (int c = 0; c < columnas.size(); c++) {
            ReporteColumna.Tipo tipo = columnas.get(c).tipo();
            Object v = datos.valor(c);
            Cell celda = fila.createCell(c);
            celda.setCellStyle(e.estilo(tipo, total));
            if (v == null) {
                continue;
            }
            if (v instanceof BigDecimal n) {
                celda.setCellValue(n.doubleValue());
            } else if (v instanceof Number n) {
                celda.setCellValue(n.doubleValue());
            } else if (v instanceof LocalDateTime dt) {
                celda.setCellValue(dt);
            } else if (v instanceof LocalDate d) {
                celda.setCellValue(d);
            } else {
                celda.setCellValue(v.toString());
            }
            anchos[c] = Math.max(anchos[c], largoVisible(v, tipo));
        }
    }

    private static int largoVisible(Object v, ReporteColumna.Tipo tipo) {
        return switch (tipo) {
            case FECHA -> 10;
            case FECHA_HORA -> 16;
            case MONEDA -> v.toString().length() + 6;
            default -> v.toString().length();
        };
    }

    /** Nombre de hoja válido para Excel (máx. 31 caracteres, sin : \ / ? * [ ]) y sin repetirse. */
    static String nombreHoja(String titulo, Set<String> usados) {
        String base = WorkbookUtil.createSafeSheetName(StringUtils.defaultIfBlank(titulo, "Hoja"));   // corta a 31
        String nombre = base;
        for (int i = 2; usados.contains(nombre.toLowerCase()); i++) {
            nombre = StringUtils.left(base, 28) + " " + i;
        }
        usados.add(nombre.toLowerCase());
        return nombre;
    }

    private static void par(Row fila, String etiqueta, String valor, Estilos e) {
        texto(fila, 0, etiqueta, e.etiqueta);
        texto(fila, 1, valor, e.valorCabecera);
    }

    private static void texto(Row fila, int col, String valor, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    /** Estilos creados una sola vez por libro (POI limita la cantidad de estilos). */
    private static final class Estilos {
        final CellStyle titulo, etiqueta, valorCabecera, encabezado, nota, texto, entero, decimal, moneda,
                porcentaje, fecha, fechaHora;
        final CellStyle totalTexto, totalEntero, totalDecimal, totalMoneda, totalPorcentaje;

        Estilos(XSSFWorkbook libro) {
            DataFormat df = libro.createDataFormat();
            Font negrita = libro.createFont();
            negrita.setBold(true);
            Font blanca = libro.createFont();
            blanca.setBold(true);
            blanca.setColor(IndexedColors.WHITE.getIndex());
            Font grande = libro.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);
            grande.setColor(IndexedColors.DARK_TEAL.getIndex());
            Font gris = libro.createFont();
            gris.setItalic(true);
            gris.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            titulo = libro.createCellStyle();
            titulo.setFont(grande);
            etiqueta = libro.createCellStyle();
            etiqueta.setFont(negrita);
            valorCabecera = libro.createCellStyle();
            nota = libro.createCellStyle();
            nota.setFont(gris);

            encabezado = libro.createCellStyle();
            encabezado.setFont(blanca);
            encabezado.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            encabezado.setAlignment(HorizontalAlignment.CENTER);
            encabezado.setWrapText(true);
            bordes(encabezado);

            texto = conBordes(libro, null, df);
            entero = conBordes(libro, "#,##0", df);
            decimal = conBordes(libro, "#,##0.00", df);
            moneda = conBordes(libro, "\"S/\" #,##0.00", df);          // RNF-17: soles
            porcentaje = conBordes(libro, "0.0\" %\"", df);
            fecha = conBordes(libro, "dd/mm/yyyy", df);                 // RNF-17: dd/mm/aaaa
            fecha.setAlignment(HorizontalAlignment.CENTER);
            fechaHora = conBordes(libro, "dd/mm/yyyy hh:mm", df);
            fechaHora.setAlignment(HorizontalAlignment.CENTER);

            totalTexto = libro.createCellStyle();
            totalTexto.cloneStyleFrom(texto);
            totalTexto.setFont(negrita);
            totalTexto.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalTexto.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalTexto.setBorderTop(BorderStyle.DOUBLE);
            totalEntero = total(libro, totalTexto, entero);
            totalDecimal = total(libro, totalTexto, decimal);
            totalMoneda = total(libro, totalTexto, moneda);
            totalPorcentaje = total(libro, totalTexto, porcentaje);
        }

        CellStyle estilo(ReporteColumna.Tipo tipo, boolean total) {
            return switch (tipo) {
                case ENTERO -> total ? totalEntero : entero;
                case DECIMAL -> total ? totalDecimal : decimal;
                case MONEDA -> total ? totalMoneda : moneda;
                case PORCENTAJE -> total ? totalPorcentaje : porcentaje;
                case FECHA -> total ? totalTexto : fecha;
                case FECHA_HORA -> total ? totalTexto : fechaHora;
                default -> total ? totalTexto : texto;
            };
        }

        private static CellStyle total(XSSFWorkbook libro, CellStyle base, CellStyle numerico) {
            CellStyle s = libro.createCellStyle();
            s.cloneStyleFrom(base);
            s.setDataFormat(numerico.getDataFormat());
            return s;
        }

        private static CellStyle conBordes(XSSFWorkbook libro, String formato, DataFormat df) {
            CellStyle s = libro.createCellStyle();
            if (formato != null) {
                s.setDataFormat(df.getFormat(formato));
            }
            bordes(s);
            return s;
        }

        private static void bordes(CellStyle s) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            s.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            s.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            s.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            s.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        }
    }
}
