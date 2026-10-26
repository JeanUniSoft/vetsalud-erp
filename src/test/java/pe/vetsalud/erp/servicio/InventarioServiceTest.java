package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.CatalogoInventarioDaoEnMemoria;
import pe.vetsalud.erp.fakes.IngresoDaoEnMemoria;
import pe.vetsalud.erp.fakes.KardexDaoEnMemoria;
import pe.vetsalud.erp.fakes.ParametroDaoEnMemoria;
import pe.vetsalud.erp.fakes.ProductoDaoEnMemoria;
import pe.vetsalud.erp.modelo.AjusteInventario;
import pe.vetsalud.erp.modelo.IngresoDocumento;
import pe.vetsalud.erp.modelo.LineaIngreso;
import pe.vetsalud.erp.modelo.LoteAlerta;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-21, RF-22, RF-24, RF-25 y RN-06: ingresos por lote, ajustes y alertas. */
class InventarioServiceTest {

    private static final LocalDate HOY = LocalDate.now();

    private ProductoDaoEnMemoria productos;
    private IngresoDaoEnMemoria ingresos;
    private KardexDaoEnMemoria kardex;
    private InventarioService servicio;

    private final UsuarioSesion farmacia = new UsuarioSesion(6, "mccori", "Milagros Ccori", "FARMACIA",
            Set.of("INVENTARIO_INGRESAR", "INVENTARIO_AJUSTAR"), null, false);
    private final UsuarioSesion cajero = new UsuarioSesion(7, "dramos", "Diego Ramos", "CAJERO",
            Set.of("VENTA_PROCESAR", "CAJA_CERRAR"), null, false);

    @BeforeEach
    void preparar() {
        productos = new ProductoDaoEnMemoria();
        productos.agregarProducto(1, "Antibióticos", "MED-001", "Amoxicilina 250 mg x 10 tab", "CAJA", "28.00", true);
        productos.agregarProducto(2, "Accesorios", "ACC-001", "Collar isabelino talla M", "UND", "25.00", false);
        productos.agregarProducto(3, "Alimentos", "ALI-001", "Pro Plan granel", "KG", "30.00", false);
        productos.agregarLote(11, 1, "AMX-2407", HOY.plusDays(15), "3");
        productos.agregarLote(12, 1, "AMX-0101", HOY.minusDays(3), "4");
        ingresos = new IngresoDaoEnMemoria(productos);
        kardex = new KardexDaoEnMemoria();
        servicio = new InventarioService(ingresos, kardex, productos, new CatalogoInventarioDaoEnMemoria(),
                new ParametroService(new ParametroDaoEnMemoria()), Clock.systemDefaultZone());
    }

    private static IngresoDocumento factura(String numero, LineaIngreso... lineas) {
        return new IngresoDocumento(1, "FACTURA", numero, null, List.of(lineas));
    }

    private static LineaIngreso linea(int producto, String lote, LocalDate vence, String cantidad, String costo) {
        return new LineaIngreso(producto, lote, vence, cantidad == null ? null : new BigDecimal(cantidad),
                costo == null ? null : new BigDecimal(costo));
    }

    @Nested
    @DisplayName("RF-21: ingresos por lote")
    class Ingresos {

        @Test
        @DisplayName("Registra el documento con varias líneas y normaliza códigos")
        void valido() throws Exception {
            servicio.registrarIngreso(factura(" f001-00012 ",
                    linea(1, " amx-2612 ", HOY.plusDays(400), "20", "16.5"),
                    linea(2, "SIN-LOTE", null, "5", "11")), farmacia);
            IngresoDocumento d = ingresos.registrados.get(0);
            assertEquals("F001-00012", d.numDoc());
            assertEquals(HOY, d.fecha());
            assertEquals("AMX-2612", d.lineas().get(0).codigoLote());
            assertEquals(new BigDecimal("16.50"), d.lineas().get(0).costoUnitario());
            assertNull(d.lineas().get(1).fechaVencimiento(), "un accesorio puede ingresar sin vencimiento");
        }

        @Test
        @DisplayName("Rechaza un vencimiento pasado y un medicamento sin vencimiento")
        void vencimiento() {
            ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(
                    factura("F1", linea(1, "AMX-9", HOY.minusDays(1), "5", "10")), farmacia));
            assertTrue(e.getMessage().contains("vencida"));
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(
                    factura("F1", linea(1, "AMX-9", null, "5", "10")), farmacia));
        }

        @Test
        @DisplayName("La cantidad debe ser mayor que cero (entera salvo KG) y el costo no negativo")
        void cantidadYCosto() throws Exception {
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(
                    factura("F1", linea(1, "AMX-9", HOY.plusDays(90), "0", "10")), farmacia));
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(
                    factura("F1", linea(1, "AMX-9", HOY.plusDays(90), "1.5", "10")), farmacia));
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(
                    factura("F1", linea(1, "AMX-9", HOY.plusDays(90), "5", "-0.01")), farmacia));
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(
                    factura("F1", linea(1, "AMX-9", HOY.plusDays(90), "5", null)), farmacia));
            servicio.registrarIngreso(factura("F2", linea(3, "PPL-1", HOY.plusDays(90), "2.5", "0")), farmacia);
            assertEquals(1, ingresos.registrados.size());
        }

        @Test
        @DisplayName("El documento es único por proveedor y exige al menos una línea")
        void documentoUnico() throws Exception {
            servicio.registrarIngreso(factura("F001-1", linea(2, "SIN-LOTE", null, "1", "11")), farmacia);
            ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(
                    factura("f001-1", linea(2, "SIN-LOTE", null, "1", "11")), farmacia));
            assertTrue(e.getMessage().contains("ya fue registrado"));
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(factura("F001-2"), farmacia));
        }

        @Test
        @DisplayName("Reutiliza un lote existente solo si coincide el vencimiento; no repite lotes en el documento")
        void loteExistente() throws Exception {
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(
                    factura("F3", linea(1, "AMX-2407", HOY.plusDays(16), "5", "10")), farmacia));
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(factura("F3",
                    linea(1, "AMX-77", HOY.plusDays(90), "5", "10"), linea(1, "amx-77", HOY.plusDays(90), "1", "10")),
                    farmacia));
            servicio.registrarIngreso(factura("F3", linea(1, "amx-2407", HOY.plusDays(15), "5", "10")), farmacia);
            assertEquals("AMX-2407", ingresos.registrados.get(0).lineas().get(0).codigoLote());
        }

        @Test
        @DisplayName("Proveedor inactivo o rol sin INVENTARIO_INGRESAR es rechazado")
        void permisosYProveedor() {
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(new IngresoDocumento(2, "FACTURA",
                    "F9", null, List.of(linea(2, "SIN-LOTE", null, "1", "1"))), farmacia));
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarIngreso(
                    factura("F9", linea(2, "SIN-LOTE", null, "1", "1")), cajero));
        }
    }

    @Nested
    @DisplayName("RF-25: ajustes autorizados con motivo y evidencia")
    class Ajustes {

        @Test
        @DisplayName("Un rol sin INVENTARIO_AJUSTAR no puede ajustar")
        void sinPermiso() {
            ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.registrarAjuste(
                    new AjusteInventario(11, "AJUSTE_SALIDA", BigDecimal.ONE, "Merma o rotura", "Acta 0012"), cajero));
            assertTrue(e.getMessage().contains("INVENTARIO_AJUSTAR"));
            assertTrue(kardex.ajustes.isEmpty());
        }

        @Test
        @DisplayName("Sin motivo o sin evidencia el ajuste es rechazado")
        void sinMotivo() {
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarAjuste(
                    new AjusteInventario(11, "AJUSTE_SALIDA", BigDecimal.ONE, " ", "Acta 0012"), farmacia));
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarAjuste(
                    new AjusteInventario(11, "AJUSTE_SALIDA", BigDecimal.ONE, "Merma o rotura", null), farmacia));
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarAjuste(
                    new AjusteInventario(11, "OTRO", BigDecimal.ONE, "Merma o rotura", "Acta 1"), farmacia));
        }

        @Test
        @DisplayName("RN-06: una salida no puede dejar el lote en negativo")
        void salidaMayorAlStock() {
            ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.registrarAjuste(
                    new AjusteInventario(11, "AJUSTE_SALIDA", new BigDecimal("4"), "Merma o rotura", "Acta 0012"),
                    farmacia));
            assertTrue(e.getMessage().contains("disponible 3"));
        }

        @Test
        @DisplayName("Registra el ajuste con motivo y evidencia (la baja de un lote vencido es válida)")
        void valido() throws Exception {
            servicio.registrarAjuste(new AjusteInventario(12, "AJUSTE_SALIDA", new BigDecimal("4"),
                    "Baja por vencimiento", "Acta de baja 0007"), farmacia);
            assertEquals("Baja por vencimiento · Evidencia: Acta de baja 0007", kardex.ajustes.get(0).textoMotivo());
            assertThrows(ReglaNegocioException.class, () -> servicio.registrarAjuste(new AjusteInventario(12,
                    "AJUSTE_ENTRADA", BigDecimal.ONE, "Conteo físico (inventario)", "Conteo 25/10"), farmacia));
        }
    }

    @Test
    @DisplayName("RF-24: generar alertas internas no duplica las que siguen pendientes")
    void alertasSinDuplicar() throws Exception {
        kardex.alertas.add(new LoteAlerta(2, "ACC-001", "Collar isabelino talla M", "STOCK_MINIMO", new BigDecimal("2"),
                null, null, new BigDecimal("2")));
        kardex.alertas.add(new LoteAlerta(1, "MED-001", "Amoxicilina", "POR_VENCER", new BigDecimal("3"),
                HOY.plusDays(15), "AMX-2407", new BigDecimal("5")));
        assertEquals(2, servicio.generarAlertas(farmacia));
        assertEquals(0, servicio.generarAlertas(farmacia));
        assertTrue(kardex.alertasCreadas.get(0).startsWith("FARMACIA|STOCK_MINIMO|"));
        assertTrue(kardex.alertasCreadas.get(1).contains("lote AMX-2407"));
    }

    @Test
    @DisplayName("RF-22: el periodo del kardex debe ser coherente")
    void periodoKardex() {
        assertThrows(ReglaNegocioException.class, () -> servicio.kardex(
                new pe.vetsalud.erp.modelo.KardexFiltro(null, null, HOY, HOY.minusDays(1))));
    }
}
