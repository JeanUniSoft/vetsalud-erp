package pe.vetsalud.erp.reporte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.modelo.FiltroReporte;
import pe.vetsalud.erp.modelo.LoteStock;
import pe.vetsalud.erp.modelo.VentaDiaria;

/** Genera el Excel en memoria y lo vuelve a leer con Apache POI para verificar su contenido. */
class ReporteExcelInventarioTest {

    private static final ZonedDateTime EMISION = ZonedDateTime.of(2026, 10, 16, 18, 30, 0, 0, ZoneId.of("America/Lima"));
    private static final List<LoteStock> LOTES = List.of(
            lote("MED-001", "AMX-2407", LocalDate.of(2026, 10, 3), "3", "5", "20", "16.50"),    // vencido
            lote("MED-001", "AMX-2511", LocalDate.of(2026, 11, 1), "20", "5", "20", "17.00"),   // por vencer
            lote("ACC-001", "SIN-LOTE", null, "2", "2", "2", "11.00"),                         // stock bajo
            lote("MED-002", "BRV-0925", LocalDate.of(2028, 1, 31), "6", "3", "6", "98.00"));   // normal
    private static final List<VentaDiaria> VENTAS = List.of(
            new VentaDiaria(LocalDate.of(2026, 9, 18), "EFECTIVO", 1, new BigDecimal("100.00")),
            new VentaDiaria(LocalDate.of(2026, 9, 18), "YAPE_PLIN", 1, new BigDecimal("83.00")));

    private static XSSFWorkbook libro;

    @BeforeAll
    static void generar() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ReporteExcelInventario().escribir(out, LOTES, VENTAS,
                new FiltroReporte(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 16), 30), "mccori", EMISION);
        libro = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
    }

    @Test
    void creaLasTresHojasEnOrden() {
        assertEquals(3, libro.getNumberOfSheets());
        assertEquals(ReporteExcelInventario.HOJA_RESUMEN, libro.getSheetName(0));
        assertEquals(ReporteExcelInventario.HOJA_STOCK, libro.getSheetName(1));
        assertEquals(ReporteExcelInventario.HOJA_VENTAS, libro.getSheetName(2));
    }

    @Test
    void clasificaCadaLoteSegunSuCondicion() {
        Sheet hoja = libro.getSheet(ReporteExcelInventario.HOJA_STOCK);
        int f = ReporteExcelInventario.FILA_ENCABEZADO + 1;
        assertEquals("VENCIDO", hoja.getRow(f).getCell(11).getStringCellValue());
        assertEquals("POR_VENCER", hoja.getRow(f + 1).getCell(11).getStringCellValue());
        assertEquals("STOCK_BAJO", hoja.getRow(f + 2).getCell(11).getStringCellValue());
        assertEquals("NORMAL", hoja.getRow(f + 3).getCell(11).getStringCellValue());
        assertEquals(-13, hoja.getRow(f).getCell(5).getNumericCellValue());
    }

    @Test
    void elTotalValorizadoCoincideConLosDatos() {
        double esperado = LOTES.stream().mapToDouble(l -> l.valorizado().doubleValue()).sum();
        Row total = libro.getSheet(ReporteExcelInventario.HOJA_STOCK)
                .getRow(ReporteExcelInventario.FILA_ENCABEZADO + 1 + LOTES.size());
        assertEquals("Total", total.getCell(0).getStringCellValue());
        assertEquals(esperado, total.getCell(10).getNumericCellValue(), 0.001);   // valor calculado de la fórmula
    }

    @Test
    void elResumenCalculaIndicadoresConFormulas() {
        Sheet resumen = libro.getSheet(ReporteExcelInventario.HOJA_RESUMEN);
        assertEquals("COUNTIF('Stock y vencimientos'!$L$5:$L$8,\"VENCIDO\")", valorDe(resumen, "Lotes vencidos").getCellFormula());
        assertEquals(1, valorDe(resumen, "Lotes vencidos").getNumericCellValue());
        assertEquals(1, valorDe(resumen, "Lotes por vencer").getNumericCellValue());
        assertEquals(183.00, valorDe(resumen, "Ventas confirmadas").getNumericCellValue(), 0.001);
        assertEquals("16/10/2026 18:30 (hora de Lima)", valorDe(resumen, "Emitido el").getStringCellValue());
    }

    @Test
    void aplicaFormatoCondicionalFiltroYPanelFijo() {
        Sheet hoja = libro.getSheet(ReporteExcelInventario.HOJA_STOCK);
        assertEquals(4, hoja.getSheetConditionalFormatting().getNumConditionalFormattings());
        assertTrue(hoja.getPaneInformation().isFreezePane());
        assertEquals("\"S/\" #,##0.00", hoja.getRow(5).getCell(9).getCellStyle().getDataFormatString());
    }

    private static org.apache.poi.ss.usermodel.Cell valorDe(Sheet hoja, String etiqueta) {
        for (Row fila : hoja) {
            if (fila.getCell(0) != null && fila.getCell(0).getStringCellValue().startsWith(etiqueta)) {
                return fila.getCell(1);
            }
        }
        throw new AssertionError("No existe la fila " + etiqueta);
    }

    private static LoteStock lote(String codigo, String lote, LocalDate vence, String stock, String minimo,
                                  String disponible, String costo) {
        return new LoteStock(codigo, "Producto " + codigo, "Categoría", "UND", lote, vence, new BigDecimal(stock),
                new BigDecimal(minimo), new BigDecimal(disponible), new BigDecimal(costo));
    }
}
