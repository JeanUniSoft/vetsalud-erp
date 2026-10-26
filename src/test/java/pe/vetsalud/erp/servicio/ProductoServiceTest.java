package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.CatalogoInventarioDaoEnMemoria;
import pe.vetsalud.erp.fakes.ParametroDaoEnMemoria;
import pe.vetsalud.erp.fakes.ProductoDaoEnMemoria;
import pe.vetsalud.erp.modelo.Producto;
import pe.vetsalud.erp.modelo.Proveedor;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-20 / RF-23: catálogo de productos, categorías y proveedores. */
class ProductoServiceTest {

    private ProductoDaoEnMemoria productos;
    private CatalogoInventarioDaoEnMemoria catalogo;
    private ProductoService servicio;

    private final UsuarioSesion farmacia = new UsuarioSesion(6, "mccori", "Milagros Ccori", "FARMACIA",
            Set.of("INVENTARIO_INGRESAR", "INVENTARIO_AJUSTAR"), null, false);
    private final UsuarioSesion cajero = new UsuarioSesion(7, "dramos", "Diego Ramos", "CAJERO",
            Set.of("VENTA_PROCESAR"), null, false);

    @BeforeEach
    void preparar() {
        productos = new ProductoDaoEnMemoria();
        catalogo = new CatalogoInventarioDaoEnMemoria();
        servicio = new ProductoService(productos, catalogo, new ParametroService(new ParametroDaoEnMemoria()));
    }

    private static Producto datos(String codigo, String unidad, String precio) {
        return Producto.datos(null, 1, codigo, "  amoxicilina   500 mg x 10 tab ", unidad,
                precio == null ? null : new BigDecimal(precio), new BigDecimal("5"), true, true);
    }

    @Test
    @DisplayName("RF-20: registra el producto normalizando código, nombre y montos")
    void registra() throws Exception {
        int id = servicio.registrar(datos(" med-010 ", "caja", "29.9"), farmacia);
        Producto p = productos.productos.get(id);
        assertEquals("MED-010", p.codigo());
        assertEquals("amoxicilina 500 mg x 10 tab", p.nombre());
        assertEquals("CAJA", p.unidad());
        assertEquals(new BigDecimal("29.90"), p.precioVenta());
        assertTrue(p.requiereReceta());
    }

    @Test
    @DisplayName("RF-20: el código de producto es único")
    void codigoUnico() throws Exception {
        servicio.registrar(datos("MED-010", "CAJA", "10"), farmacia);
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(datos("med-010", "UND", "10"), farmacia));
    }

    @Test
    @DisplayName("RF-20: unidad de la lista, precio no negativo y categoría existente")
    void validaciones() {
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(datos("MED-011", "LITRO", "10"), farmacia));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(datos("MED-011", "UND", "-1"), farmacia));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(datos("MED-011", "UND", null), farmacia));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(Producto.datos(null, 99, "MED-011", "X",
                "UND", BigDecimal.ONE, BigDecimal.ZERO, false, true), farmacia));
    }

    @Test
    @DisplayName("RF-20: se desactiva en lugar de borrar y conserva el registro")
    void desactivar() throws Exception {
        int id = servicio.registrar(datos("MED-012", "CAJA", "10"), farmacia);
        Producto p = productos.productos.get(id);
        servicio.actualizar(Producto.datos(id, 1, p.codigo(), p.nombre(), p.unidad(), p.precioVenta(), p.stockMinimo(),
                p.requiereReceta(), false), farmacia);
        assertFalse(productos.productos.get(id).activo());
        assertTrue(servicio.buscar(null, null, false, false).isEmpty());
        assertEquals(1, servicio.buscar(null, null, false, true).size());
    }

    @Test
    @DisplayName("RF-02: sin permiso INVENTARIO_INGRESAR no se modifica el catálogo")
    void sinPermiso() {
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(datos("MED-013", "CAJA", "10"), cajero));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrarCategoria("Vacunas", cajero));
    }

    @Test
    @DisplayName("RF-20: no permite categorías repetidas (sin distinguir mayúsculas)")
    void categorias() throws Exception {
        servicio.registrarCategoria("  Vacunas ", farmacia);
        assertThrows(ReglaNegocioException.class, () -> servicio.registrarCategoria("vacunas", farmacia));
        assertThrows(ReglaNegocioException.class, () -> servicio.renombrarCategoria(1, "Accesorios", farmacia));
    }

    @Test
    @DisplayName("RF-21: el proveedor necesita un RUC válido y único")
    void proveedores() throws Exception {
        assertThrows(ReglaNegocioException.class, () -> servicio.registrarProveedor(
                new Proveedor(null, "30123456789", "Mala S.A.", null, null, true), farmacia));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrarProveedor(
                new Proveedor(null, "20100123456", "Duplicada S.A.", null, null, true), farmacia));
        int id = servicio.registrarProveedor(new Proveedor(null, "20512345678", " Vet  Import S.A.C. ", "014445566",
                "Ventas@VetImport.pe", true), farmacia);
        assertEquals("ventas@vetimport.pe", catalogo.proveedores.get(id).correo());
        assertEquals("Vet Import S.A.C.", catalogo.proveedores.get(id).razonSocial());
    }

    @Test
    @DisplayName("RF-23 / RN-07: sugiere el lote vigente que vence primero y cubre la cantidad")
    void loteSugerido() throws Exception {
        productos.agregarProducto(3, "Antiparasitarios", "MED-003", "Drontal", "TABLETA", "12", false);
        productos.agregarLote(1, 3, "VENCIDO", LocalDate.now().minusDays(1), "100");
        productos.agregarLote(2, 3, "PRONTO", LocalDate.now().plusDays(10), "2");
        productos.agregarLote(3, 3, "LEJANO", LocalDate.now().plusDays(300), "40");
        assertEquals("PRONTO", servicio.loteSugerido(3, new BigDecimal("2")).orElseThrow().codigoLote());
        assertEquals("LEJANO", servicio.loteSugerido(3, new BigDecimal("5")).orElseThrow().codigoLote());
        assertTrue(servicio.loteSugerido(3, new BigDecimal("41")).isEmpty());
        assertThrows(ReglaNegocioException.class, () -> servicio.loteSugerido(3, BigDecimal.ZERO));
    }
}
