package pe.vetsalud.erp.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import pe.vetsalud.erp.dao.jdbc.CajaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ClienteDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ProductoDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.UsuarioDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.VentaPosDaoJdbc;
import pe.vetsalud.erp.modelo.Comprobante;
import pe.vetsalud.erp.modelo.LineaVenta;
import pe.vetsalud.erp.modelo.Pago;
import pe.vetsalud.erp.modelo.Venta;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.ErroresBD;
import pe.vetsalud.erp.seguridad.ClaveHasher;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.AutorizacionDescuento;
import pe.vetsalud.erp.servicio.CajaService;
import pe.vetsalud.erp.servicio.ClienteService;
import pe.vetsalud.erp.servicio.ReglaNegocioException;
import pe.vetsalud.erp.servicio.VentaService;

/**
 * P08 · Venta y pago (RF-26 a RF-29, RF-31).
 * Rutas: /ventas · /ventas/nueva · /ventas/editar?id= · /ventas/comprobante?id= · /ventas/anular (POST)
 * · /ventas/descartar (POST)
 */
@WebServlet({"/ventas", "/ventas/nueva", "/ventas/editar", "/ventas/comprobante", "/ventas/anular",
        "/ventas/descartar"})
public class VentasServlet extends ControladorBase {

    private VentaService ventas;
    private ClienteService clientes;

    /** Acción de formulario que decide a dónde redirigir cuando tiene éxito (p. ej. a la venta recién creada). */
    @FunctionalInterface
    private interface AccionConDestino {
        String ejecutar() throws SQLException, IOException, ServletException;
    }

    @Override
    public void init() {
        Db db = Db.get();
        ventas = new VentaService(new VentaPosDaoJdbc(db), new CajaDaoJdbc(db), new ProductoDaoJdbc(db),
                new AutorizacionDescuento(new UsuarioDaoJdbc(db), new ClaveHasher()), Servicios.parametros(),
                Clock.systemDefaultZone());
        clientes = new ClienteService(new ClienteDaoJdbc(db));
    }

    // ------------------------------------------------------------------ GET

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        switch (req.getServletPath()) {
            case "/ventas/nueva" -> mostrar(req, res, "ventas/nueva", () -> {
                req.setAttribute("turno", ventas.turnoAbierto(u).orElse(null));
                String q = texto(req, "q");
                req.setAttribute("q", q);
                if (q != null) {
                    req.setAttribute("clientes", clientes.buscar(q));
                }
                Integer idCliente = entero(req, "cliente");
                if (idCliente != null) {
                    req.setAttribute("cliente", clientes.obtener(idCliente));
                    req.setAttribute("mascotas", ventas.mascotas(idCliente));
                    req.setAttribute("consultasCliente", ventas.consultasPorCobrar(idCliente));
                }
                req.setAttribute("porCobrar", ventas.consultasPorCobrar(null));
            });
            case "/ventas/editar" -> {
                Optional<Venta> v = buscarVenta(req, res);
                if (v.isEmpty()) {
                    return;
                }
                if (!v.get().esPendiente()) {
                    conservarMensaje(req);
                    redirigir(req, res, "/ventas/comprobante?id=" + v.get().idVenta());
                    return;
                }
                mostrar(req, res, "ventas/editar", () -> cargarEdicion(req, u, v.get()));
            }
            case "/ventas/comprobante" -> {
                Optional<Venta> v = buscarVenta(req, res);
                if (v.isEmpty()) {
                    return;
                }
                if (v.get().esPendiente()) {
                    conservarMensaje(req);
                    redirigir(req, res, "/ventas/editar?id=" + v.get().idVenta());
                    return;
                }
                mostrar(req, res, "ventas/comprobante",
                        () -> req.setAttribute("c", ventas.comprobante(v.get().idVenta())));
            }
            case "/ventas/anular", "/ventas/descartar" -> redirigir(req, res, "/ventas");
            default -> mostrar(req, res, "ventas/lista", () -> {
                boolean sinFiltros = texto(req, "desde") == null && texto(req, "hasta") == null;
                LocalDate desde = sinFiltros ? LocalDate.now() : fecha(req, "desde");
                LocalDate hasta = sinFiltros ? LocalDate.now() : fecha(req, "hasta");
                String estado = texto(req, "estado");
                req.setAttribute("desde", desde);
                req.setAttribute("hasta", hasta);
                req.setAttribute("estado", estado);
                req.setAttribute("estados", VentaService.ESTADOS);
                req.setAttribute("turno", ventas.turnoAbierto(u).orElse(null));
                List<Venta> lista = ventas.listar(desde, hasta, estado, u);
                req.setAttribute("ventas", lista);
                req.setAttribute("totalConfirmado", lista.stream().filter(Venta::esConfirmada).map(Venta::total)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
                req.setAttribute("numConfirmadas", lista.stream().filter(Venta::esConfirmada).count());
                req.setAttribute("numPendientes", lista.stream().filter(Venta::esPendiente).count());
                req.setAttribute("numAnuladas", lista.stream().filter(Venta::esAnulada).count());
            });
        }
    }

    private void cargarEdicion(HttpServletRequest req, UsuarioSesion u, Venta v) throws SQLException {
        Comprobante c = ventas.comprobante(v.idVenta());
        req.setAttribute("c", c);
        req.setAttribute("servicios", ventas.servicios());
        req.setAttribute("productos", ventas.productosVendibles());
        req.setAttribute("medios", Pago.MEDIOS);
        req.setAttribute("receta", ventas.receta(v.idConsulta()));
        req.setAttribute("productosEnVenta", c.lineas().stream().filter(LineaVenta::esProducto)
                .map(LineaVenta::idProducto).collect(Collectors.toSet()));
        req.setAttribute("limiteDescuento", ventas.limiteDescuento(v.subtotal()));
        req.setAttribute("pctDescuento", ventas.porcentajeMaximoDescuento());
        req.setAttribute("puedeEditar", v.idCajero() == u.idUsuario() && v.turnoAbierto());
        req.setAttribute("puedeDescartar", v.turnoAbierto() && (v.idCajero() == u.idUsuario()
                || CajaService.esAdministrador(u)));
        Integer idProducto = entero(req, "producto");
        if (idProducto != null) {
            req.setAttribute("productoSel", ventas.producto(idProducto));
            req.setAttribute("lotesVendibles", ventas.lotesVendibles(idProducto));
        }
    }

    /** Si esta pantalla redirige a otra, el mensaje de la operación anterior debe llegar a la siguiente. */
    private static void conservarMensaje(HttpServletRequest req) {
        Object flash = req.getAttribute("flash");
        if (flash != null) {
            req.getSession().setAttribute("flash", flash);
        }
    }

    /** Venta indicada en ?id=; si no existe vuelve al listado con el mensaje. */
    private Optional<Venta> buscarVenta(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        try {
            return Optional.of(ventas.obtener(idObligatorio(req, res, "id")));
        } catch (ReglaNegocioException | IllegalArgumentException e) {
            error(req, e.getMessage());
            redirigir(req, res, "/ventas");
        } catch (SQLException e) {
            mostrar(req, res, "error/error", () -> {
                throw e;
            });
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ POST

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        switch (req.getServletPath()) {
            case "/ventas/nueva" -> {
                String volver = "cobrar".equals(texto(req, "accion")) ? "/ventas/nueva"
                        : "/ventas/nueva" + (StringUtils.isNumeric(texto(req, "cliente")) ? "?cliente=" + texto(req, "cliente") : "");
                ejecutar(req, res, volver, () -> {
                    exigirPermiso(req, VentaService.PERMISO_PROCESAR);
                    long id = "cobrar".equals(texto(req, "accion"))
                            ? ventas.cobrarAtencion(largo(req, "consulta"), u)
                            : ventas.crear(entero(req, "cliente"), entero(req, "mascota"), largo(req, "consulta"), u);
                    exito(req, "Venta creada en su turno de caja. Agregue servicios o productos, registre los pagos y confirme.");
                    return "/ventas/editar?id=" + id;
                });
            }
            case "/ventas/editar" -> editar(req, res, u);
            case "/ventas/descartar" -> {
                String id = StringUtils.defaultString(texto(req, "id"));
                String volver = StringUtils.isNumeric(id) && !id.isEmpty() ? "/ventas/editar?id=" + id : "/ventas";
                procesar(req, res, "/ventas", volver, "Venta n.° " + id + " descartada: se borraron sus líneas y pagos; "
                        + "ya no bloquea el cierre de caja.", () -> {
                            exigirPermiso(req, VentaService.PERMISO_PROCESAR);
                            ventas.descartar(idObligatorio(req, res, "id"), u);
                        });
            }
            case "/ventas/anular" -> {
                String id = StringUtils.defaultString(texto(req, "id"));
                String destino = StringUtils.isNumeric(id) && !id.isEmpty() ? "/ventas/comprobante?id=" + id : "/ventas";
                procesar(req, res, destino, destino,
                        "Venta anulada: el comprobante se conserva y el stock de sus productos volvió al kardex (DEVOLUCION).",
                        () -> {
                            exigirPermiso(req, VentaService.PERMISO_ANULAR);
                            ventas.anular(idObligatorio(req, res, "id"), texto(req, "motivo"), u);
                        });
            }
            default -> res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void editar(HttpServletRequest req, HttpServletResponse res, UsuarioSesion u)
            throws IOException, ServletException {
        String idTexto = StringUtils.defaultString(texto(req, "id"));
        if (!StringUtils.isNumeric(idTexto) || idTexto.isEmpty()) {
            error(req, "Falta el identificador de la venta.");
            redirigir(req, res, "/ventas");
            return;
        }
        long id = Long.parseLong(idTexto);
        String volver = "/ventas/editar?id=" + id;
        String accion = StringUtils.defaultString(texto(req, "accion"));
        // Si falla al agregar un producto se conserva su selección para ver los lotes
        String volverError = accion.equals("producto") && StringUtils.isNumeric(texto(req, "producto"))
                ? volver + "&producto=" + texto(req, "producto") : volver;
        ejecutar(req, res, volverError, () -> {
            exigirPermiso(req, VentaService.PERMISO_PROCESAR);
            switch (accion) {
                case "servicio" -> exito(req, ventas.agregarServicio(id, entero(req, "servicio"), decimal(req, "cantidad"), u));
                case "producto" -> exito(req, ventas.agregarProducto(id, entero(req, "producto"), decimal(req, "cantidad"),
                        entero(req, "lote"), u));
                case "receta" -> exito(req, ventas.agregarReceta(id, u));
                case "quitar" -> exito(req, ventas.quitarLinea(id, largo(req, "detalle"), u));
                case "descuento" -> {
                    String clave = req.getParameter("claveAdmin");
                    exito(req, ventas.aplicarDescuento(id, decimal(req, "descuento"), texto(req, "usuarioAdmin"),
                            clave == null ? null : clave.toCharArray(), u));
                }
                case "pago" -> exito(req, ventas.registrarPago(id, texto(req, "medio"), decimal(req, "monto"),
                        texto(req, "referencia"), u));
                case "quitarPago" -> {
                    ventas.quitarPago(id, largo(req, "pago"), u);
                    exito(req, "Pago quitado.");
                }
                case "confirmar" -> {
                    String comprobante = ventas.confirmar(id, u);
                    exito(req, "Venta confirmada. Comprobante " + comprobante + " emitido.");
                    return "/ventas/comprobante?id=" + id;
                }
                default -> throw new ReglaNegocioException("Acción no reconocida.");
            }
            return volver;
        });
    }

    /**
     * Igual que {@link #procesar} (Post/Redirect/Get con mensajes claros), pero la acción indica el destino
     * de éxito, que puede depender del resultado (id de la venta creada, comprobante emitido).
     */
    private void ejecutar(HttpServletRequest req, HttpServletResponse res, String destinoError, AccionConDestino accion)
            throws IOException, ServletException {
        try {
            redirigir(req, res, accion.ejecutar());
        } catch (ReglaNegocioException | IllegalArgumentException e) {
            log.info("Operación rechazada: {}", e.getMessage());
            error(req, e.getMessage());
            redirigir(req, res, destinoError);
        } catch (SQLException e) {
            log.warn("Error de base de datos ({}): {}", e.getSQLState(), e.getMessage(), e);
            error(req, ErroresBD.mensaje(e));
            redirigir(req, res, destinoError);
        }
    }
}
