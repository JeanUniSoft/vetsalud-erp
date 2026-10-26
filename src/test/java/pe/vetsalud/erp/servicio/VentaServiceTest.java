package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.CajaDaoEnMemoria;
import pe.vetsalud.erp.fakes.ParametroDaoEnMemoria;
import pe.vetsalud.erp.fakes.ProductoDaoEnMemoria;
import pe.vetsalud.erp.fakes.UsuarioDaoEnMemoria;
import pe.vetsalud.erp.fakes.VentaPosDaoEnMemoria;
import pe.vetsalud.erp.modelo.ConsultaPorCobrar;
import pe.vetsalud.erp.modelo.LineaVenta;
import pe.vetsalud.erp.modelo.ProductoRecetado;
import pe.vetsalud.erp.modelo.ServicioVendible;
import pe.vetsalud.erp.modelo.Usuario;
import pe.vetsalud.erp.modelo.Venta;
import pe.vetsalud.erp.seguridad.ClaveHasher;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-23, RF-26 a RF-29, RF-31 y RN-06 a RN-12: punto de venta sin base de datos. */
class VentaServiceTest {

    private static final LocalDate HOY = LocalDate.now();
    private static final String CLAVE_ADMIN = "Admin#2026";

    private CajaDaoEnMemoria caja;
    private VentaPosDaoEnMemoria ventas;
    private ProductoDaoEnMemoria productos;
    private UsuarioDaoEnMemoria usuarios;
    private VentaService servicio;

    private final UsuarioSesion cajero = new UsuarioSesion(7, "dramos", "Diego Ramos", "CAJERO",
            Set.of("VENTA_PROCESAR", "CAJA_CERRAR"), null, false);
    private final UsuarioSesion otroCajero = new UsuarioSesion(8, "pcajas", "Pedro Cajas", "CAJERO",
            Set.of("VENTA_PROCESAR", "CAJA_CERRAR"), null, false);
    private final UsuarioSesion administrador = new UsuarioSesion(1, "rparedes", "Rosa Paredes", "ADMINISTRADOR",
            Set.of("VENTA_PROCESAR", "VENTA_ANULAR", "DESCUENTO_AUTORIZAR", "CAJA_CERRAR"), null, false);

    @BeforeEach
    void preparar() {
        caja = new CajaDaoEnMemoria();
        caja.abrir(7, new BigDecimal("150.00"), 7);
        ventas = new VentaPosDaoEnMemoria(caja);
        productos = new ProductoDaoEnMemoria();
        productos.agregarProducto(1, "Antibióticos", "MED-001", "Amoxicilina 250 mg x 10 tab", "CAJA", "28.00", true);
        productos.agregarProducto(3, "Antiparasitarios", "MED-003", "Drontal Plus perro", "TABLETA", "12.00", false);
        productos.agregarLote(31, 3, "DRP-0801", HOY.minusDays(5), "10");      // vencido
        productos.agregarLote(32, 3, "DRP-1102", HOY.plusDays(250), "50");
        productos.agregarLote(33, 3, "DRP-1201", HOY.plusDays(40), "3");       // vence primero (FEFO)
        productos.agregarLote(11, 1, "AMX-2407", HOY.plusDays(15), "3");
        usuarios = new UsuarioDaoEnMemoria();
        String hash = new ClaveHasher().hashear(CLAVE_ADMIN.toCharArray());
        usuarios.agregar(new Usuario(1, "ADMINISTRADOR", "Rosa", "Paredes", "rparedes", "r@vetsalud.pe", hash, null, 0,
                false, true, false, null));
        usuarios.agregar(new Usuario(9, "ADMINISTRADOR", "Admin", "Bloqueado", "ablock", "b@vetsalud.pe", hash, null, 5,
                true, true, false, null));
        usuarios.agregar(new Usuario(6, "FARMACIA", "Milagros", "Ccori", "mccori", "m@vetsalud.pe", hash, null, 0,
                false, true, false, null));
        usuarios.permisos.put("ADMINISTRADOR", Set.of("DESCUENTO_AUTORIZAR", "VENTA_ANULAR"));
        servicio = new VentaService(ventas, caja, productos, new AutorizacionDescuento(usuarios, new ClaveHasher()),
                new ParametroService(new ParametroDaoEnMemoria()), Clock.systemDefaultZone());
    }

    /** Venta pendiente del cajero con una consulta general (S/ 60). */
    private long ventaConConsulta() throws Exception {
        long id = servicio.crear(1, 10, null, cajero);
        servicio.agregarServicio(id, 1, BigDecimal.ONE, cajero);
        return id;
    }

    @Test
    @DisplayName("RF-26: la venta nace PENDIENTE en el turno abierto del cajero")
    void nacePendiente() throws Exception {
        long id = ventaConConsulta();
        Venta v = servicio.obtener(id);
        assertEquals(Venta.PENDIENTE, v.estado());
        assertEquals(caja.turnoAbierto(7).orElseThrow().idTurno(), v.idTurno());
        assertEquals(new BigDecimal("60.00"), v.total());
    }

    @Test
    @DisplayName("RF-30: sin turno de caja abierto no se puede registrar una venta")
    void sinTurnoAbierto() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.crear(1, null, null, otroCajero));
        assertTrue(e.getMessage().contains("abra su caja"));
    }

    @Test
    @DisplayName("RF-26: la mascota debe pertenecer al cliente")
    void mascotaDeOtroCliente() {
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(1, 20, null, cajero));
    }

    @Test
    @DisplayName("RF-26: cobrar atención carga la consulta y sus procedimientos sin duplicar el servicio")
    void cobrarAtencion() throws Exception {
        ventas.consultas.put(5L, new ConsultaPorCobrar(5, LocalDateTime.now(), 10, "Rocky", 1, "María Gonzales",
                "Dr. Mendoza", "Cojera", "Laceración", 2, false));
        ventas.servicioBase.put(5L, new ServicioVendible(1, "Consulta general", "CONSULTA", new BigDecimal("60.00")));
        ventas.procedimientos.put(5L, List.of(
                LineaVenta.servicio(5, "Curación de herida", BigDecimal.ONE, new BigDecimal("70.00")),
                LineaVenta.servicio(1, "Consulta general", BigDecimal.ONE, new BigDecimal("60.00"))));
        long id = servicio.cobrarAtencion(5L, cajero);
        Venta v = servicio.obtener(id);
        assertEquals(10, v.idMascota());
        assertEquals(5L, v.idConsulta());
        assertEquals(2, ventas.lineas(id).size(), "la consulta general no se cobra dos veces");
        assertEquals(new BigDecimal("130.00"), v.subtotal());
        // la misma consulta ya no se puede volver a cobrar
        assertThrows(ReglaNegocioException.class, () -> servicio.cobrarAtencion(5L, cajero));
    }

    @Nested
    @DisplayName("RF-23 / RN-06 / RN-07: productos por lote")
    class Lotes {

        @Test
        @DisplayName("FEFO: usa primero el lote vigente que vence antes y reparte si no alcanza")
        void fefo() throws Exception {
            long id = ventaConConsulta();
            servicio.agregarProducto(id, 3, new BigDecimal("5"), null, cajero);
            List<LineaVenta> productosVenta = ventas.lineas(id).stream().filter(LineaVenta::esProducto).toList();
            assertEquals(2, productosVenta.size());
            assertEquals("DRP-1201", productosVenta.get(0).codigoLote());
            assertEquals(0, new BigDecimal("3").compareTo(productosVenta.get(0).cantidad()));
            assertEquals("DRP-1102", productosVenta.get(1).codigoLote());
            assertEquals(0, new BigDecimal("2").compareTo(productosVenta.get(1).cantidad()));
            assertEquals(new BigDecimal("120.00"), servicio.obtener(id).total());
        }

        @Test
        @DisplayName("RN-06: no vende un lote vencido aunque se elija a mano")
        void loteVencido() throws Exception {
            long id = ventaConConsulta();
            ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                    () -> servicio.agregarProducto(id, 3, BigDecimal.ONE, 31, cajero));
            assertTrue(e.getMessage().contains("venció"));
        }

        @Test
        @DisplayName("RF-23: nunca vende más que el stock vigente (los vencidos no cuentan)")
        void stockInsuficiente() throws Exception {
            long id = ventaConConsulta();
            ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                    () -> servicio.agregarProducto(id, 3, new BigDecimal("54"), null, cajero));
            assertTrue(e.getMessage().contains("disponible 53"));
            assertTrue(e.getMessage().contains("vencidos"));
        }

        @Test
        @DisplayName("RF-23: considera lo ya reservado del mismo lote en la venta")
        void reservado() throws Exception {
            long id = ventaConConsulta();
            servicio.agregarProducto(id, 3, new BigDecimal("2"), 33, cajero);
            assertThrows(ReglaNegocioException.class, () -> servicio.agregarProducto(id, 3, new BigDecimal("2"), 33, cajero));
        }

        @Test
        @DisplayName("RF-20: un producto con receta solo se vende con la consulta que lo prescribe")
        void requiereReceta() throws Exception {
            long sinConsulta = ventaConConsulta();
            assertThrows(ReglaNegocioException.class,
                    () -> servicio.agregarProducto(sinConsulta, 1, BigDecimal.ONE, null, cajero));

            ventas.consultas.put(6L, new ConsultaPorCobrar(6, LocalDateTime.now(), 10, "Rocky", 1, "María", "Dr. Mendoza",
                    "Control", "Otitis", 0, true));
            ventas.recetas.put(6L, List.of(new ProductoRecetado(1, "MED-001", "Amoxicilina 250 mg x 10 tab",
                    BigDecimal.ONE, "1 tab", "c/12 h", 7, new BigDecimal("28.00"), 11, "AMX-2407", new BigDecimal("3"))));
            long conReceta = servicio.cobrarAtencion(6L, cajero);
            String mensaje = servicio.agregarReceta(conReceta, cajero);
            assertTrue(mensaje.contains("Amoxicilina"));
            assertEquals("AMX-2407", ventas.lineas(conReceta).get(0).codigoLote());
        }
    }

    @Nested
    @DisplayName("RF-27 / RN-08: descuentos")
    class Descuentos {

        @Test
        @DisplayName("Hasta el 10 % lo aplica el cajero sin autorización")
        void dentroDelLimite() throws Exception {
            long id = ventaConConsulta();
            servicio.aplicarDescuento(id, new BigDecimal("6.00"), null, null, cajero);
            Venta v = servicio.obtener(id);
            assertEquals(new BigDecimal("54.00"), v.total());
            assertNull(v.idAutorizaDescuento());
        }

        @Test
        @DisplayName("Más del 10 % sin credenciales de administrador es rechazado")
        void sinAutorizacion() throws Exception {
            long id = ventaConConsulta();
            ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                    () -> servicio.aplicarDescuento(id, new BigDecimal("6.01"), null, null, cajero));
            assertTrue(e.getMessage().contains("RN-08"));
            assertEquals(0, servicio.obtener(id).descuento().signum());
        }

        @Test
        @DisplayName("Más del 10 % rechaza contraseña errónea, usuario no administrador o bloqueado")
        void autorizacionInvalida() throws Exception {
            long id = ventaConConsulta();
            assertThrows(ReglaNegocioException.class, () -> servicio.aplicarDescuento(id, new BigDecimal("12"),
                    "rparedes", "otra".toCharArray(), cajero));
            assertThrows(ReglaNegocioException.class, () -> servicio.aplicarDescuento(id, new BigDecimal("12"),
                    "mccori", CLAVE_ADMIN.toCharArray(), cajero));
            assertThrows(ReglaNegocioException.class, () -> servicio.aplicarDescuento(id, new BigDecimal("12"),
                    "ablock", CLAVE_ADMIN.toCharArray(), cajero));
        }

        @Test
        @DisplayName("Más del 10 % con administrador válido queda registrado el autorizador")
        void autorizado() throws Exception {
            long id = ventaConConsulta();
            char[] clave = CLAVE_ADMIN.toCharArray();
            servicio.aplicarDescuento(id, new BigDecimal("12.00"), "rparedes", clave, cajero);
            assertEquals(1, servicio.obtener(id).idAutorizaDescuento());
            assertEquals('\0', clave[0], "la contraseña se borra de memoria");
            servicio.registrarPago(id, "EFECTIVO", new BigDecimal("48.00"), null, cajero);
            servicio.confirmar(id, cajero);
            assertEquals(1, ventas.ultimoAutorizador);
            assertEquals(new BigDecimal("12.00"), ventas.ultimoDescuentoConfirmado);
        }

        @Test
        @DisplayName("Si se quitan líneas y el descuento ya no cumple la regla, se retira")
        void descuentoRetirado() throws Exception {
            long id = ventaConConsulta();
            servicio.agregarServicio(id, 5, BigDecimal.ONE, cajero);              // subtotal 130
            servicio.aplicarDescuento(id, new BigDecimal("13.00"), null, null, cajero);
            long idCuracion = ventas.lineas(id).get(1).idDetalle();
            String mensaje = servicio.quitarLinea(id, idCuracion, cajero);         // subtotal 60: 13 > 6
            assertTrue(mensaje.contains("retiró el descuento"));
            assertEquals(new BigDecimal("60.00"), servicio.obtener(id).total());
        }
    }

    @Nested
    @DisplayName("RF-28 / RN-12: pagos y confirmación")
    class Pagos {

        @Test
        @DisplayName("Un pago con tarjeta, Yape/Plin o transferencia sin n.° de operación es rechazado")
        void sinReferencia() throws Exception {
            long id = ventaConConsulta();
            for (String medio : List.of("TARJETA", "YAPE_PLIN", "TRANSFERENCIA")) {
                assertThrows(ReglaNegocioException.class,
                        () -> servicio.registrarPago(id, medio, new BigDecimal("10"), "  ", cajero));
            }
            servicio.registrarPago(id, "YAPE_PLIN", new BigDecimal("10"), "OP-558712", cajero);
            assertEquals(1, ventas.pagos(id).size());
        }

        @Test
        @DisplayName("RN-12: no confirma si la suma de pagos no coincide con el total")
        void pagosNoCuadran() throws Exception {
            long id = ventaConConsulta();
            servicio.registrarPago(id, "TARJETA", new BigDecimal("50.00"), "VISA-1234", cajero);
            ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.confirmar(id, cajero));
            assertTrue(e.getMessage().contains("falta S/ 10.00"));
            assertEquals(Venta.PENDIENTE, servicio.obtener(id).estado());
        }

        @Test
        @DisplayName("El efectivo mayor al saldo registra solo el saldo e informa el vuelto")
        void vuelto() throws Exception {
            long id = ventaConConsulta();
            String mensaje = servicio.registrarPago(id, "EFECTIVO", new BigDecimal("100"), null, cajero);
            assertTrue(mensaje.contains("Vuelto para el cliente: S/ 40.00"));
            assertEquals(new BigDecimal("60.00"), ventas.pagos(id).get(0).monto());
            assertThrows(ReglaNegocioException.class,
                    () -> servicio.registrarPago(id, "EFECTIVO", BigDecimal.ONE, null, cajero));
        }

        @Test
        @DisplayName("Un pago no efectivo no puede superar el saldo")
        void tarjetaMayorAlSaldo() throws Exception {
            long id = ventaConConsulta();
            assertThrows(ReglaNegocioException.class,
                    () -> servicio.registrarPago(id, "TARJETA", new BigDecimal("61"), "VISA-1", cajero));
        }

        @Test
        @DisplayName("RF-29: con pagos mixtos que cuadran se confirma y se obtiene el correlativo")
        void confirma() throws Exception {
            long id = ventaConConsulta();
            servicio.registrarPago(id, "EFECTIVO", new BigDecimal("20"), null, cajero);
            servicio.registrarPago(id, "YAPE_PLIN", new BigDecimal("40"), "OP-1", cajero);
            assertEquals("T001-00000001", servicio.confirmar(id, cajero));
            assertEquals(Venta.CONFIRMADA, servicio.obtener(id).estado());
            assertThrows(ReglaNegocioException.class, () -> servicio.agregarServicio(id, 1, BigDecimal.ONE, cajero));
        }

        @Test
        @DisplayName("Una venta sin líneas no se confirma")
        void sinLineas() throws Exception {
            long id = servicio.crear(1, null, null, cajero);
            assertThrows(ReglaNegocioException.class, () -> servicio.confirmar(id, cajero));
        }

        @Test
        @DisplayName("Solo el cajero del turno modifica la venta")
        void otroCajeroNoModifica() throws Exception {
            long id = ventaConConsulta();
            caja.abrir(8, BigDecimal.TEN, 8);
            assertThrows(ReglaNegocioException.class,
                    () -> servicio.registrarPago(id, "EFECTIVO", BigDecimal.TEN, null, otroCajero));
        }
    }

    @Nested
    @DisplayName("RF-31 / RN-09: anulación")
    class Anulacion {

        private long confirmada() throws Exception {
            long id = ventaConConsulta();
            servicio.registrarPago(id, "EFECTIVO", new BigDecimal("60"), null, cajero);
            servicio.confirmar(id, cajero);
            return id;
        }

        @Test
        @DisplayName("El cajero no puede anular (falta VENTA_ANULAR)")
        void sinPermiso() throws Exception {
            long id = confirmada();
            assertThrows(ReglaNegocioException.class, () -> servicio.anular(id, "Error de cobro", cajero));
        }

        @Test
        @DisplayName("Anular exige motivo y no borra el documento")
        void conMotivo() throws Exception {
            long id = confirmada();
            assertThrows(ReglaNegocioException.class, () -> servicio.anular(id, " ", administrador));
            servicio.anular(id, "Cliente desistió de la compra", administrador);
            Venta v = servicio.obtener(id);
            assertEquals(Venta.ANULADA, v.estado());
            assertEquals("T001-00000001", v.comprobante());
            assertThrows(ReglaNegocioException.class, () -> servicio.anular(id, "Otra vez", administrador));
        }

        @Test
        @DisplayName("No se anula una venta pendiente")
        void pendiente() throws Exception {
            long id = ventaConConsulta();
            assertThrows(ReglaNegocioException.class, () -> servicio.anular(id, "No corresponde", administrador));
        }
    }

    @Nested
    @DisplayName("RF-26 / RF-30: descarte de ventas pendientes")
    class Descarte {

        @Test
        @DisplayName("El cajero del turno descarta su venta pendiente con líneas y pagos")
        void descartaPendiente() throws Exception {
            long id = ventaConConsulta();
            servicio.registrarPago(id, "EFECTIVO", new BigDecimal("20"), null, cajero);
            servicio.descartar(id, cajero);
            assertTrue(ventas.buscarPorId(id).isEmpty());
            assertTrue(ventas.pagos(id).isEmpty());
            assertThrows(ReglaNegocioException.class, () -> servicio.obtener(id));
        }

        @Test
        @DisplayName("Una venta confirmada no se descarta (se anula)")
        void rechazaConfirmada() throws Exception {
            long id = ventaConConsulta();
            servicio.registrarPago(id, "EFECTIVO", new BigDecimal("60"), null, cajero);
            servicio.confirmar(id, cajero);
            ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.descartar(id, cajero));
            assertTrue(e.getMessage().contains("se anula"));
            assertEquals(Venta.CONFIRMADA, servicio.obtener(id).estado());
        }

        @Test
        @DisplayName("Otro cajero no la descarta; el administrador sí")
        void rechazaOtroCajero() throws Exception {
            long id = ventaConConsulta();
            caja.abrir(8, BigDecimal.TEN, 8);
            assertThrows(ReglaNegocioException.class, () -> servicio.descartar(id, otroCajero));
            assertTrue(ventas.buscarPorId(id).isPresent());
            servicio.descartar(id, administrador);
            assertTrue(ventas.buscarPorId(id).isEmpty());
        }
    }

    @Test
    @DisplayName("RN-08: el límite sin autorización usa el parámetro DESCUENTO_MAX_PCT")
    void limiteParametrizado() {
        ParametroDaoEnMemoria parametros = new ParametroDaoEnMemoria();
        parametros.valores.putAll(Map.of("DESCUENTO_MAX_PCT", "15"));
        VentaService otro = new VentaService(ventas, caja, productos, new AutorizacionDescuento(usuarios, new ClaveHasher()),
                new ParametroService(parametros), Clock.systemDefaultZone());
        assertEquals(new BigDecimal("15.00"), otro.limiteDescuento(new BigDecimal("100.00")));
        assertEquals(new BigDecimal("6.00"), servicio.limiteDescuento(new BigDecimal("60.00")));
    }
}
