package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.ReporteDaoEnMemoria;
import pe.vetsalud.erp.modelo.FilaReporte;
import pe.vetsalud.erp.modelo.LoteStock;
import pe.vetsalud.erp.modelo.ReporteCitas;
import pe.vetsalud.erp.modelo.ReporteColumna;
import pe.vetsalud.erp.modelo.ReporteFiltro;
import pe.vetsalud.erp.modelo.ReporteGrafico;
import pe.vetsalud.erp.modelo.ReporteGrupo;
import pe.vetsalud.erp.modelo.ReporteRotacion;
import pe.vetsalud.erp.modelo.ReporteTabla;
import pe.vetsalud.erp.modelo.ReporteVentaAnulada;
import pe.vetsalud.erp.modelo.ReporteVentasResumen;

/** P10 · Reportes: filtros, totales, rotación, ausentismo y gráfico (RF-32 a RF-36). */
class ReportesServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** "Hoy" fijo: 25/10/2026 10:00 en Lima. */
    private static final Clock RELOJ = Clock.fixed(LocalDateTime.of(2026, 10, 25, 10, 0).atZone(LIMA).toInstant(), LIMA);

    private ReporteDaoEnMemoria dao;
    private ReportesService servicio;

    @BeforeEach
    void preparar() {
        dao = new ReporteDaoEnMemoria();
        servicio = new ReportesService(dao, List.of(new ExportadorCsv(), new ExportadorExcel()), () -> 30, RELOJ);
    }

    private static BigDecimal s(String v) {
        return new BigDecimal(v);
    }

    // ------------------------------------------------------------------ filtros

    @Test
    @DisplayName("RF-32: sin fechas el periodo va del primer día del mes a hoy")
    void periodoPorDefecto() {
        ReporteFiltro f = servicio.filtro("ventas", null, null, null, null, null, null);
        assertEquals(LocalDate.of(2026, 10, 1), f.desde());
        assertEquals(LocalDate.of(2026, 10, 25), f.hasta());
        assertEquals("dia", f.agrupacion());
        assertEquals(30, f.dias());
        assertEquals(LocalDate.of(2026, 10, 26), f.hastaExclusivo());
    }

    @Test
    @DisplayName("RF-33: rechaza un rango con «desde» posterior a «hasta»")
    void rangoInvertido() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.filtro("ventas",
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 1), null, null, null, null));
        assertTrue(e.getMessage().contains("posterior"));
    }

    @Test
    @DisplayName("RNF-01: rechaza periodos de más de 366 días y acepta exactamente 366")
    void rangoMaximo() {
        assertThrows(ReglaNegocioException.class, () -> servicio.filtro("clinica",
                LocalDate.of(2025, 10, 1), LocalDate.of(2026, 10, 2), null, null, null, null));
        ReporteFiltro f = servicio.filtro("clinica", LocalDate.of(2025, 10, 25), LocalDate.of(2026, 10, 25),
                null, null, null, null);
        assertEquals(366, f.diasPeriodo());
    }

    @Test
    @DisplayName("Seguridad: agrupación, vista o días fuera de la lista blanca se rechazan")
    void listaBlanca() {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.filtro("ventas", null, null, null, "1;DROP TABLE venta", null, null));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.filtro("inventario", null, null, "otra", null, null, null));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.filtro("inventario", null, null, "stock", null, null, 0));
        assertThrows(ReglaNegocioException.class, () -> servicio.filtro("nomina", null, null, null, null, null, null));
    }

    // ------------------------------------------------------------------ ventas

    @Test
    @DisplayName("RF-33: el total por medio de pago coincide con las ventas confirmadas; las anuladas van aparte")
    void ventasPorMedio() throws Exception {
        dao.ventas.put("medio", List.of(new ReporteGrupo("EFECTIVO", 2, null, s("295.00")),
                new ReporteGrupo("TARJETA", 1, null, s("78.00")), new ReporteGrupo("YAPE_PLIN", 1, null, s("83.00"))));
        dao.resumen = new ReporteVentasResumen(3, s("456.00"), s("456.00"), BigDecimal.ZERO, s("110.00"),
                s("346.00"), 1, s("24.00"));
        dao.anuladas.add(new ReporteVentaAnulada("T001-00000004", LocalDateTime.of(2026, 10, 25, 9, 0), "Diego Ramos",
                s("24.00"), "Cliente desistió", LocalDateTime.of(2026, 10, 25, 9, 30)));

        ReporteTabla t = servicio.ventas(servicio.filtro("ventas", null, null, null, "medio", null, null));

        assertEquals("Yape / Plin", t.filas().get(2).valor(0));
        assertEquals(0, s("456.00").compareTo((BigDecimal) t.totales().valor(2)), "suma = total confirmado");
        assertEquals(3L, t.totales().valor(1), "comprobantes sin duplicar");
        ReporteTabla anuladas = t.anexos().get(1);
        assertEquals(1, anuladas.filas().size());
        assertEquals(0, s("24.00").compareTo((BigDecimal) anuladas.totales().valor(3)));
        assertEquals("Medio de pago", t.filtros().get("Agrupación"));
        assertTrue(t.conGrafico());
    }

    @Test
    @DisplayName("RF-33: conciliación servicios + productos − descuentos = total vendido")
    void conciliacion() throws Exception {
        dao.resumen = new ReporteVentasResumen(2, s("180.00"), s("200.00"), s("20.00"), s("120.00"), s("80.00"),
                0, BigDecimal.ZERO);
        ReporteTabla t = servicio.ventas(servicio.filtro("ventas", null, null, null, "servicio", null, null));
        ReporteTabla c = t.anexos().get(0);
        BigDecimal suma = c.filas().stream().map(f -> (BigDecimal) f.valor(1)).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, suma.compareTo((BigDecimal) c.totales().valor(1)));
    }

    @Test
    @DisplayName("RF-32: KPIs de ventas con ticket promedio del periodo")
    void kpisVentas() throws Exception {
        dao.resumen = new ReporteVentasResumen(4, s("500.00"), s("500.00"), BigDecimal.ZERO, s("300"), s("200"),
                1, s("24.00"));
        var kpis = servicio.kpis("ventas", servicio.filtro("ventas", null, null, null, null, null, null));
        assertEquals(0, s("500.00").compareTo(kpis.get(0).valor()));
        assertEquals(0, s("125.00").compareTo(kpis.get(1).valor()));
        assertEquals(0, BigDecimal.ONE.compareTo(kpis.get(3).valor()));
    }

    // ------------------------------------------------------------------ inventario

    @Test
    @DisplayName("RF-34: índice de rotación = vendidas ÷ stock y cobertura = stock ÷ venta diaria")
    void formulaRotacion() {
        assertEquals(s("2.00"), ReportesService.indiceRotacion(s("10"), s("5")));
        assertEquals(15L, ReportesService.coberturaDias(s("10"), s("5"), 30));
        assertNull(ReportesService.indiceRotacion(s("3"), BigDecimal.ZERO));
        assertNull(ReportesService.coberturaDias(BigDecimal.ZERO, s("8"), 30));
        assertEquals("Agotado", ReportesService.lecturaRotacion(s("3"), BigDecimal.ZERO));
        assertEquals("Sin movimiento", ReportesService.lecturaRotacion(BigDecimal.ZERO, s("8")));
        assertEquals("Baja", ReportesService.lecturaRotacion(s("1"), s("20")));
        assertEquals("Alta", ReportesService.lecturaRotacion(s("6"), s("6")));
    }

    @Test
    @DisplayName("RF-34: el reporte de rotación muestra la fórmula y el índice por producto")
    void reporteRotacion() throws Exception {
        dao.rotacion.add(new ReporteRotacion("MED-005", "NexGard 4-10 kg", "Antiparasitarios", "UND", s("1"), s("3")));
        ReporteTabla t = servicio.inventario(servicio.filtro("inventario", null, null, "rotacion", null, null, null));
        assertEquals(s("0.33"), t.filas().get(0).valor(5));
        assertTrue(t.notas().contains(ReportesService.FORMULA_ROTACION));
    }

    @Test
    @DisplayName("RF-34: vencimientos lista solo lotes con stock vencidos o por vencer, del más próximo al más lejano")
    void vencimientos() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 10, 25);
        dao.lotes.add(lote("A", "L1", hoy.plusDays(20), "4"));      // por vencer
        dao.lotes.add(lote("B", "L2", hoy.minusDays(5), "10"));     // vencido
        dao.lotes.add(lote("C", "L3", hoy.plusDays(200), "6"));     // normal: no aparece
        dao.lotes.add(lote("D", "L4", hoy.plusDays(3), "0"));       // sin stock: no aparece
        dao.lotes.add(lote("E", "SIN-LOTE", null, "2"));            // no vence: no aparece
        ReporteTabla t = servicio.inventario(servicio.filtro("inventario", null, null, "vencimientos", null, null, null));
        assertEquals(2, t.filas().size());
        assertEquals("L2", t.filas().get(0).valor(2));
        assertEquals("Vencido", t.filas().get(0).valor(7));
        assertEquals(-5L, t.filas().get(0).valor(4));
        assertEquals("Por vencer", t.filas().get(1).valor(7));
    }

    @Test
    @DisplayName("RF-34: el stock por lote identifica producto, lote, saldo y condición")
    void stockPorLote() throws Exception {
        dao.lotes.add(lote("A", "L1", LocalDate.of(2026, 10, 1), "3"));
        ReporteTabla t = servicio.inventario(servicio.filtro("inventario", null, null, "stock", null, null, null));
        FilaReporte f = t.filas().get(0);
        assertEquals("Producto A", f.valor(0));
        assertEquals("L1", f.valor(3));
        assertEquals(s("3"), f.valor(5));
        assertEquals("Vencido", f.valor(9));
        assertEquals("25/10/2026", t.filtros().get("Corte"));
        assertFalse(t.filtros().containsKey("Desde"), "el stock es una foto al corte, no un periodo");
    }

    private static LoteStock lote(String p, String lote, LocalDate vence, String stock) {
        return new LoteStock("COD-" + p, "Producto " + p, "Cat", "UND", lote, vence, new BigDecimal(stock),
                BigDecimal.ONE, new BigDecimal("50"), new BigDecimal("10.00"));
    }

    // ------------------------------------------------------------------ clínica

    @Test
    @DisplayName("RF-35: ausencias = canceladas + no asistió y % de ausentismo por profesional")
    void ausentismo() throws Exception {
        dao.citas.add(new ReporteCitas("Carlos Mendoza", 8, 4, 0, 1, 2, 1));
        dao.citas.add(new ReporteCitas("Ana Salazar", 2, 2, 0, 0, 0, 0));
        ReporteTabla t = servicio.clinica(servicio.filtro("clinica", null, null, "ausencias", "profesional", null, null));
        assertEquals(3L, t.filas().get(0).valor(4));
        assertEquals(s("37.5"), t.filas().get(0).valor(5));
        assertEquals(10L, t.totales().valor(1));
        assertEquals(s("30.0"), t.totales().valor(5));
        assertEquals("Detalle de ausencias", t.anexos().get(0).titulo());
        assertTrue(dao.consultas.get(0).startsWith("citas-profesional:2026-10-01..2026-10-26"));
    }

    // ------------------------------------------------------------------ gráfico y exportación

    @Test
    @DisplayName("Gráfico: barras ordenadas, escaladas al mayor valor (100) y recortadas al máximo")
    void graficoDeBarras() {
        List<FilaReporte> filas = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            filas.add(FilaReporte.de("Grupo " + i, BigDecimal.valueOf(i * 10L)));
        }
        ReporteGrafico g = ReportesService.grafico("Prueba", filas, 0, 1, ReporteColumna.Tipo.MONEDA, true, 12);
        assertEquals(12, g.barras().size());
        assertEquals(15, g.categorias());
        assertTrue(g.recortado());
        assertEquals("Grupo 15", g.barras().get(0).etiqueta());
        assertEquals(100, g.barras().get(0).porcentaje());
        assertEquals(27, g.barras().get(11).porcentaje(), "40 de 150 ≈ 27 %");
    }

    @Test
    @DisplayName("RF-36: formato desconocido rechazado y nombre de archivo seguro")
    void exportacion() throws Exception {
        assertThrows(ReglaNegocioException.class, () -> servicio.exportador("exe"));
        ReporteFiltro f = servicio.filtro("ventas", null, null, null, "cajero", null, null);
        ReporteTabla t = servicio.ventas(f);
        assertEquals("reporte_ventas_cajero_20261025-1000.csv", servicio.nombreArchivo(t, f, servicio.exportador("CSV")));
    }
}
