package pe.vetsalud.erp.reporte;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.modelo.FiltroReporte;
import pe.vetsalud.erp.modelo.LoteStock;
import pe.vetsalud.erp.modelo.VentaDiaria;

/**
 * Genera con Apache POI el libro Excel del módulo de reportes:
 * hoja Resumen (filtros, emisión e indicadores), hoja de stock y vencimientos por lote (RF-34)
 * y hoja de ventas por medio de pago (RF-33). Cumple RF-36: el archivo conserva filtros,
 * fecha de emisión y usuario; los formatos siguen RNF-17 (PEN, dd/mm/aaaa, hora de Perú).
 */
public class ReporteExcelInventario {

    private static final Logger LOG = LoggerFactory.getLogger(ReporteExcelInventario.class);

    public static final String HOJA_RESUMEN = "Resumen";
    public static final String HOJA_STOCK = "Stock y vencimientos";
    public static final String HOJA_VENTAS = "Ventas por medio de pago";

    static final String[] COLUMNAS_STOCK = {"Código", "Producto", "Categoría", "Lote", "Vencimiento",
            "Días p/ vencer", "Stock lote", "Unidad", "Stock mín.", "Costo unit.", "Valorizado", "Condición"};
    static final String[] COLUMNAS_VENTAS = {"Día", "Medio de pago", "N.° ventas", "Total"};

    /** Primera fila de datos (0-based) de las hojas de detalle: título, subtítulo, línea en blanco y encabezado. */
    static final int FILA_ENCABEZADO = 3;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public void escribir(OutputStream salida, List<LoteStock> lotes, List<VentaDiaria> ventas,
                         FiltroReporte filtro, String usuario, ZonedDateTime emision) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Estilos e = new Estilos(libro);
            LocalDate hoy = emision.toLocalDate();

            Sheet resumen = libro.createSheet(HOJA_RESUMEN);
            int totalStock = hojaStock(libro.createSheet(HOJA_STOCK), e, lotes, filtro, hoy);
            int totalVentas = hojaVentas(libro.createSheet(HOJA_VENTAS), e, ventas, filtro);
            hojaResumen(resumen, e, filtro, usuario, emision, lotes.size(), totalStock, totalVentas);

            libro.getProperties().getCoreProperties().setCreator("VetSalud ERP");
            libro.getProperties().getCoreProperties().setTitle("Reporte de inventario y ventas");
            libro.getProperties().getCoreProperties().setDescription("Emitido por " + usuario);

            // Calcula y guarda el resultado de las fórmulas para que cualquier lector vea los valores
            XSSFFormulaEvaluator.evaluateAllFormulaCells(libro);
            libro.write(salida);
            LOG.debug("Libro Excel escrito: {} lotes, {} filas de ventas", lotes.size(), ventas.size());
        }
    }

    // ---------------------------------------------------------------- hoja de stock (RF-34)

    private int hojaStock(Sheet hoja, Estilos e, List<LoteStock> lotes, FiltroReporte filtro, LocalDate hoy) {
        titulo(hoja, e, "Stock y vencimientos por lote",
                "Alerta de vencimiento: " + filtro.diasAlerta() + " días · Corte: " + FECHA.format(hoy),
                COLUMNAS_STOCK.length);
        encabezado(hoja, e, COLUMNAS_STOCK);

        int r = FILA_ENCABEZADO + 1;
        for (LoteStock l : lotes) {
            Row fila = hoja.createRow(r);
            texto(fila, 0, l.codigo(), e.texto);
            texto(fila, 1, l.producto(), e.texto);
            texto(fila, 2, l.categoria(), e.texto);
            texto(fila, 3, l.codigoLote(), e.texto);
            if (l.vencimiento() != null) {
                Cell c = fila.createCell(4);
                c.setCellValue(l.vencimiento());
                c.setCellStyle(e.fecha);
                numero(fila, 5, l.diasParaVencer(hoy), e.entero);
            } else {
                texto(fila, 4, "No aplica", e.textoCentro);
                texto(fila, 5, "—", e.textoCentro);
            }
            numero(fila, 6, l.stock().doubleValue(), e.decimal);
            texto(fila, 7, l.unidad(), e.textoCentro);
            numero(fila, 8, l.stockMinimo().doubleValue(), e.decimal);
            numero(fila, 9, l.costoUnitario().doubleValue(), e.moneda);
            formula(fila, 10, "G" + (r + 1) + "*J" + (r + 1), e.moneda);   // valorizado = stock × costo
            texto(fila, 11, l.condicion(hoy, filtro.diasAlerta()).name(), e.textoCentro);
            r++;
        }
        if (lotes.isEmpty()) {
            texto(hoja.createRow(r++), 0, "Sin registros para los filtros elegidos", e.texto);
        }
        int primera = FILA_ENCABEZADO + 2;            // en notación de Excel (1-based)
        int ultima = Math.max(r, primera);
        Row total = hoja.createRow(r);
        texto(total, 0, "Total", e.totalTexto);
        for (int c = 1; c < COLUMNAS_STOCK.length; c++) {
            total.createCell(c).setCellStyle(e.totalTexto);
        }
        formula(total, 6, "SUM(G" + primera + ":G" + ultima + ")", e.totalDecimal);
        formula(total, 10, "SUM(K" + primera + ":K" + ultima + ")", e.totalMoneda);

        // Formato condicional: el color de la fila depende de la condición del lote
        SheetConditionalFormatting fc = hoja.getSheetConditionalFormatting();
        CellRangeAddress[] datos = {CellRangeAddress.valueOf("A" + primera + ":L" + ultima)};
        fc.addConditionalFormatting(datos, regla(fc, "$L" + primera + "=\"VENCIDO\"", IndexedColors.ROSE));
        fc.addConditionalFormatting(datos, regla(fc, "$L" + primera + "=\"POR_VENCER\"", IndexedColors.LIGHT_ORANGE));
        fc.addConditionalFormatting(datos, regla(fc, "$L" + primera + "=\"STOCK_BAJO\"", IndexedColors.LEMON_CHIFFON));
        // Días para vencer negativos en rojo
        ConditionalFormattingRule negativos = fc.createConditionalFormattingRule(ComparisonOperator.LT, "0");
        negativos.createFontFormatting().setFontColorIndex(IndexedColors.RED.getIndex());
        fc.addConditionalFormatting(new CellRangeAddress[]{CellRangeAddress.valueOf("F" + primera + ":F" + ultima)},
                negativos);

        hoja.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADO, ultima - 1, 0, COLUMNAS_STOCK.length - 1));
        hoja.createFreezePane(2, FILA_ENCABEZADO + 1);
        anchos(hoja, 11, 34, 16, 12, 13, 13, 11, 10, 11, 12, 13, 14);
        imprimirHorizontal(hoja);
        return r + 1;   // fila (1-based) de totales
    }

    private ConditionalFormattingRule regla(SheetConditionalFormatting fc, String formula, IndexedColors color) {
        ConditionalFormattingRule regla = fc.createConditionalFormattingRule(formula);
        regla.createPatternFormatting().setFillBackgroundColor(color.getIndex());
        return regla;
    }

    // ---------------------------------------------------------------- hoja de ventas (RF-33)

    private int hojaVentas(Sheet hoja, Estilos e, List<VentaDiaria> ventas, FiltroReporte filtro) {
        titulo(hoja, e, "Ventas confirmadas por día y medio de pago",
                "Periodo: " + FECHA.format(filtro.desde()) + " al " + FECHA.format(filtro.hasta()),
                COLUMNAS_VENTAS.length);
        encabezado(hoja, e, COLUMNAS_VENTAS);
        int r = FILA_ENCABEZADO + 1;
        for (VentaDiaria v : ventas) {
            Row fila = hoja.createRow(r++);
            Cell dia = fila.createCell(0);
            dia.setCellValue(v.dia());
            dia.setCellStyle(e.fecha);
            texto(fila, 1, v.medioPago().replace('_', '/'), e.texto);
            numero(fila, 2, v.ventas(), e.entero);
            numero(fila, 3, v.total().doubleValue(), e.moneda);
        }
        if (ventas.isEmpty()) {
            texto(hoja.createRow(r++), 0, "Sin ventas confirmadas en el periodo", e.texto);
        }
        int primera = FILA_ENCABEZADO + 2;
        int ultima = Math.max(r, primera);
        Row total = hoja.createRow(r);
        texto(total, 0, "Total", e.totalTexto);
        total.createCell(1).setCellStyle(e.totalTexto);
        formula(total, 2, "SUM(C" + primera + ":C" + ultima + ")", e.totalEntero);
        formula(total, 3, "SUM(D" + primera + ":D" + ultima + ")", e.totalMoneda);
        hoja.createFreezePane(0, FILA_ENCABEZADO + 1);
        anchos(hoja, 13, 18, 12, 14);
        return r + 1;
    }

    // ---------------------------------------------------------------- hoja resumen

    private void hojaResumen(Sheet hoja, Estilos e, FiltroReporte filtro, String usuario, ZonedDateTime emision,
                             int nLotes, int filaTotalStock, int filaTotalVentas) {
        titulo(hoja, e, "VetSalud ERP · Reporte de inventario y ventas",
                "Clínica Veterinaria VetSalud Integral S.A.C.", 2);

        String s = "'" + HOJA_STOCK + "'!";
        String v = "'" + HOJA_VENTAS + "'!";
        int primera = FILA_ENCABEZADO + 2;
        int ultima = Math.max(FILA_ENCABEZADO + 1 + nLotes, primera);
        String condiciones = s + "$L$" + primera + ":$L$" + ultima;

        Object[][] datos = {
                {"Datos de emisión", null},
                {"Emitido el", FECHA_HORA.format(emision) + " (hora de Lima)"},
                {"Emitido por", usuario},
                {"Periodo de ventas", FECHA.format(filtro.desde()) + " al " + FECHA.format(filtro.hasta())},
                {"Alerta de vencimiento", filtro.diasAlerta() + " días"},
                {null, null},
                {"Indicadores", null},
                {"Lotes con stock", "=COUNTIF(" + s + "$G$" + primera + ":$G$" + ultima + ",\">0\")"},
                {"Valor del inventario", "=" + s + "K" + filaTotalStock},
                {"Lotes vencidos (no vender, RN-06)", "=COUNTIF(" + condiciones + ",\"VENCIDO\")"},
                {"Lotes por vencer", "=COUNTIF(" + condiciones + ",\"POR_VENCER\")"},
                {"Lotes de productos con stock bajo", "=COUNTIF(" + condiciones + ",\"STOCK_BAJO\")"},
                {"Ventas confirmadas del periodo", "=" + v + "D" + filaTotalVentas},
        };
        int r = 3;
        for (Object[] d : datos) {
            Row fila = hoja.createRow(r++);
            if (d[0] == null) {
                continue;
            }
            if (d[1] == null) {                                  // subtítulo de sección
                texto(fila, 0, (String) d[0], e.seccion);
                fila.createCell(1).setCellStyle(e.seccion);
                continue;
            }
            texto(fila, 0, (String) d[0], e.etiqueta);
            String valor = (String) d[1];
            if (valor.startsWith("=")) {
                boolean dinero = d[0].toString().startsWith("Valor") || d[0].toString().startsWith("Ventas");
                formula(fila, 1, valor.substring(1), dinero ? e.moneda : e.entero);
            } else {
                texto(fila, 1, valor, e.texto);
            }
        }
        Row nota = hoja.createRow(r + 1);
        texto(nota, 0, "Documento interno. No contiene datos personales de clientes (RNF-15).", e.nota);
        anchos(hoja, 36, 34);
    }

    // ---------------------------------------------------------------- utilidades

    private void titulo(Sheet hoja, Estilos e, String titulo, String subtitulo, int columnas) {
        texto(hoja.createRow(0), 0, titulo, e.titulo);
        texto(hoja.createRow(1), 0, subtitulo, e.subtitulo);
        if (columnas > 1) {
            hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, columnas - 1));
            hoja.addMergedRegion(new CellRangeAddress(1, 1, 0, columnas - 1));
        }
        hoja.getRow(0).setHeightInPoints(22);
    }

    private void encabezado(Sheet hoja, Estilos e, String[] columnas) {
        Row fila = hoja.createRow(FILA_ENCABEZADO);
        fila.setHeightInPoints(20);
        for (int i = 0; i < columnas.length; i++) {
            texto(fila, i, columnas[i], e.encabezado);
        }
    }

    private static void texto(Row fila, int col, String valor, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    private static void numero(Row fila, int col, double valor, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    private static void formula(Row fila, int col, String formula, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellFormula(formula);
        c.setCellStyle(estilo);
    }

    private static void anchos(Sheet hoja, int... caracteres) {
        for (int i = 0; i < caracteres.length; i++) {
            hoja.setColumnWidth(i, caracteres[i] * 256);
        }
    }

    private static void imprimirHorizontal(Sheet hoja) {
        PrintSetup ps = hoja.getPrintSetup();
        ps.setLandscape(true);
        ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
        ps.setFitWidth((short) 1);
        ps.setFitHeight((short) 0);
        hoja.setFitToPage(true);
        hoja.setRepeatingRows(new CellRangeAddress(FILA_ENCABEZADO, FILA_ENCABEZADO, -1, -1));
    }

    /** Estilos reutilizados: POI limita la cantidad de estilos por libro, por eso se crean una sola vez. */
    private static final class Estilos {
        final CellStyle titulo, subtitulo, seccion, encabezado, etiqueta, texto, textoCentro, nota;
        final CellStyle fecha, entero, decimal, moneda;
        final CellStyle totalTexto, totalEntero, totalDecimal, totalMoneda;

        Estilos(XSSFWorkbook libro) {
            DataFormat df = libro.createDataFormat();
            short fmtMoneda = df.getFormat("\"S/\" #,##0.00");   // RNF-17: moneda PEN
            short fmtFecha = df.getFormat("dd/mm/yyyy");           // RNF-17: fecha dd/mm/aaaa

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
            subtitulo = libro.createCellStyle();
            subtitulo.setFont(gris);
            nota = subtitulo;

            seccion = libro.createCellStyle();
            seccion.setFont(blanca);
            seccion.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            seccion.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            encabezado = libro.createCellStyle();
            encabezado.cloneStyleFrom(seccion);
            encabezado.setAlignment(HorizontalAlignment.CENTER);
            encabezado.setWrapText(true);
            bordes(encabezado);

            etiqueta = libro.createCellStyle();
            etiqueta.setFont(negrita);
            bordes(etiqueta);
            texto = libro.createCellStyle();
            bordes(texto);
            textoCentro = libro.createCellStyle();
            textoCentro.cloneStyleFrom(texto);
            textoCentro.setAlignment(HorizontalAlignment.CENTER);

            fecha = numerico(libro, fmtFecha);
            fecha.setAlignment(HorizontalAlignment.CENTER);
            entero = numerico(libro, df.getFormat("#,##0"));
            decimal = numerico(libro, df.getFormat("#,##0.00"));
            moneda = numerico(libro, fmtMoneda);

            totalTexto = libro.createCellStyle();
            totalTexto.setFont(negrita);
            totalTexto.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalTexto.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bordes(totalTexto);
            totalTexto.setBorderTop(BorderStyle.DOUBLE);
            totalEntero = libro.createCellStyle();
            totalEntero.cloneStyleFrom(totalTexto);
            totalEntero.setDataFormat(df.getFormat("#,##0"));
            totalDecimal = libro.createCellStyle();
            totalDecimal.cloneStyleFrom(totalTexto);
            totalDecimal.setDataFormat(df.getFormat("#,##0.00"));
            totalMoneda = libro.createCellStyle();
            totalMoneda.cloneStyleFrom(totalTexto);
            totalMoneda.setDataFormat(fmtMoneda);
        }

        private static CellStyle numerico(XSSFWorkbook libro, short formato) {
            CellStyle s = libro.createCellStyle();
            s.setDataFormat(formato);
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
