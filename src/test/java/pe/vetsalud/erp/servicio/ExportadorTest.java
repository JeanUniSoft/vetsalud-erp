package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.ReporteDaoEnMemoria;
import pe.vetsalud.erp.modelo.FilaReporte;
import pe.vetsalud.erp.modelo.ReporteColumna;
import pe.vetsalud.erp.modelo.ReporteTabla;

/** RF-36: exportación CSV (Apache Commons Text) y Excel (Apache POI) con cabecera de filtros. */
class ExportadorTest {

    private static final LocalDateTime EMISION = LocalDateTime.of(2026, 10, 25, 19, 30);

    private static ReporteTabla reporte() {
        Map<String, String> filtros = new LinkedHashMap<>();
        filtros.put("Desde", "01/10/2026");
        filtros.put("Hasta", "25/10/2026");
        filtros.put("Agrupación", "Producto");
        List<ReporteColumna> columnas = List.of(ReporteColumna.texto("Producto"), ReporteColumna.entero("Líneas"),
                ReporteColumna.fecha("Vence"), ReporteColumna.moneda("Importe"));
        List<FilaReporte> filas = List.of(
                FilaReporte.de("Collar, talla M", 2L, LocalDate.of(2027, 1, 31), new BigDecimal("45.5")),
                FilaReporte.de("Shampoo \"Suave\"", 1L, null, new BigDecimal("30.00")),
                FilaReporte.de("=HYPERLINK(\"http://malo\")", 1L, null, new BigDecimal("1.00")));
        ReporteTabla anexo = ReporteTabla.anexo("Ventas anuladas (no suman)",
                List.of(ReporteColumna.texto("Comprobante"), ReporteColumna.moneda("Total")),
                List.of(FilaReporte.de("T001-00000004", new BigDecimal("24.00"))), null);
        return new ReporteTabla("ventas", "Ventas por producto", filtros, columnas, filas,
                FilaReporte.de("Total", 4L, null, new BigDecimal("76.50")), List.of("Solo ventas confirmadas."),
                List.of(anexo), null);
    }

    private static String csv(ReporteTabla t) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ExportadorCsv().exportar(t, "rparedes (Rosa Paredes)", EMISION, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("RF-36: el CSV conserva título, emisión, usuario emisor y filtros en la cabecera")
    void csvConCabecera() throws Exception {
        String[] lineas = csv(reporte()).split("\r\n");
        assertTrue(lineas[0].startsWith("﻿"), "BOM UTF-8 para Excel");
        assertTrue(lineas[0].contains("Ventas por producto"));
        assertEquals("Emitido el,25/10/2026 19:30", lineas[1]);
        assertEquals("Emitido por,rparedes (Rosa Paredes)", lineas[2]);
        assertEquals("Filtro: Desde,01/10/2026", lineas[3]);
        assertEquals("Filtro: Agrupación,Producto", lineas[5]);
        assertEquals("Producto,Líneas,Vence,Importe", lineas[7]);
    }

    @Test
    @DisplayName("RF-36: el CSV escapa comas y comillas (StringEscapeUtils.escapeCsv) y da formato a montos y fechas")
    void csvEscapado() throws Exception {
        String texto = csv(reporte());
        assertTrue(texto.contains("\"Collar, talla M\",2,31/01/2027,45.50\r\n"));
        assertTrue(texto.contains("\"Shampoo \"\"Suave\"\"\",1,,30.00\r\n"));
        assertTrue(texto.contains("Total,4,,76.50\r\n"));
        assertTrue(texto.contains("Ventas anuladas (no suman)\r\nComprobante,Total\r\nT001-00000004,24.00"));
        assertTrue(texto.contains("Notas\r\nSolo ventas confirmadas."));
    }

    @Test
    @DisplayName("Seguridad: el CSV neutraliza fórmulas (inyección CSV) anteponiendo un apóstrofo")
    void csvSinFormulas() throws Exception {
        String texto = csv(reporte());
        assertTrue(texto.contains("\"'=HYPERLINK(\"\"http://malo\"\")\""));
        assertFalse(texto.contains("\n=HYPERLINK"));
        assertEquals("'-5", ExportadorCsv.texto("-5"));
        assertEquals("'@SUMA", ExportadorCsv.texto("@SUMA"));
    }

    @Test
    @DisplayName("RF-36: el Excel generado se reabre con POI y tiene cabecera, filas, totales y una hoja por anexo")
    void excelLegible() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ExportadorExcel().exportar(reporte(), "rparedes (Rosa Paredes)", EMISION, out);
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertEquals(2, libro.getNumberOfSheets());
            Sheet hoja = libro.getSheetAt(0);
            assertEquals("Ventas por producto", hoja.getSheetName());
            assertTrue(hoja.getRow(0).getCell(0).getStringCellValue().contains("Ventas por producto"));
            assertEquals("Emitido por", hoja.getRow(2).getCell(0).getStringCellValue());
            assertEquals("rparedes (Rosa Paredes)", hoja.getRow(2).getCell(1).getStringCellValue());
            assertEquals("Agrupación", hoja.getRow(5).getCell(0).getStringCellValue());
            // fila 6 en blanco; 7 = encabezados; 8..10 = datos; 11 = totales
            Row encabezado = hoja.getRow(7);
            assertEquals("Importe", encabezado.getCell(3).getStringCellValue());
            assertTrue(libro.getFontAt(encabezado.getCell(0).getCellStyle().getFontIndex()).getBold(),
                    "encabezados en negrita");
            Cell importe = hoja.getRow(8).getCell(3);
            assertEquals(CellType.NUMERIC, importe.getCellType());
            assertEquals(45.5, importe.getNumericCellValue(), 0.001);
            assertTrue(importe.getCellStyle().getDataFormatString().contains("S/"), "formato de moneda PEN");
            assertEquals("=HYPERLINK(\"http://malo\")", hoja.getRow(10).getCell(0).getStringCellValue(),
                    "se guarda como texto, no como fórmula");
            assertEquals(76.5, hoja.getRow(11).getCell(3).getNumericCellValue(), 0.001);
            assertTrue(hoja.getColumnWidth(0) > 10 * 256, "ancho de columna calculado");
            assertEquals("Ventas anuladas (no suman)", libro.getSheetAt(1).getSheetName());
        }
    }

    @Test
    @DisplayName("SOLID (abierto/cerrado): un formato nuevo se agrega sin modificar ReportesService")
    void formatoNuevo() throws Exception {
        ExportadorReporte texto = new ExportadorReporte() {
            public String formato() { return "txt"; }
            public String tipoContenido() { return "text/plain"; }
            public String extension() { return "txt"; }
            public void exportar(ReporteTabla r, String por, LocalDateTime e, OutputStream s) throws IOException {
                s.write(r.titulo().getBytes(StandardCharsets.UTF_8));
            }
        };
        ReportesService servicio = new ReportesService(new ReporteDaoEnMemoria(),
                List.of(new ExportadorCsv(), new ExportadorExcel(), texto), () -> 30, Clock.systemDefaultZone());
        assertEquals(texto, servicio.exportador("txt"));
        assertEquals(3, servicio.formatos().size());
    }
}
