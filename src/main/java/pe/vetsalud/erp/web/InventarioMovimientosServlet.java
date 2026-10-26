package pe.vetsalud.erp.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import pe.vetsalud.erp.dao.jdbc.CatalogoInventarioDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.IngresoDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.KardexDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ProductoDaoJdbc;
import pe.vetsalud.erp.modelo.AjusteInventario;
import pe.vetsalud.erp.modelo.IngresoDocumento;
import pe.vetsalud.erp.modelo.KardexFiltro;
import pe.vetsalud.erp.modelo.LineaIngreso;
import pe.vetsalud.erp.modelo.MovimientoKardex;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.ErroresBD;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.InventarioService;
import pe.vetsalud.erp.servicio.ProductoService;
import pe.vetsalud.erp.servicio.ReglaNegocioException;

/**
 * P07 · Movimientos de inventario: ingresos por lote (RF-21), ajustes (RF-25), kardex (RF-22) y alertas (RF-24).
 * Rutas: /inventario/ingreso · /inventario/ajuste · /inventario/kardex · /inventario/alertas
 */
@WebServlet({"/inventario/ingreso", "/inventario/ajuste", "/inventario/kardex", "/inventario/alertas"})
public class InventarioMovimientosServlet extends ControladorBase {

    private static final int FILAS_INICIALES = 5;

    private ProductoService productos;
    private InventarioService inventario;

    @Override
    public void init() {
        Db db = Db.get();
        ProductoDaoJdbc productoDao = new ProductoDaoJdbc(db);
        CatalogoInventarioDaoJdbc catalogoDao = new CatalogoInventarioDaoJdbc(db);
        productos = new ProductoService(productoDao, catalogoDao, Servicios.parametros());
        inventario = new InventarioService(new IngresoDaoJdbc(db), new KardexDaoJdbc(db), productoDao, catalogoDao,
                Servicios.parametros(), Clock.systemDefaultZone());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        req.setAttribute("diasPorVencer", productos.diasPorVencer());
        switch (req.getServletPath()) {
            case "/inventario/ingreso" -> mostrarIngreso(req, res);
            case "/inventario/ajuste" -> mostrar(req, res, "inventario/ajuste", () -> {
                req.setAttribute("productos", productos.activos());
                req.setAttribute("tiposAjuste", InventarioService.TIPOS_AJUSTE);
                req.setAttribute("motivos", InventarioService.MOTIVOS_AJUSTE);
                req.setAttribute("recientes", inventario.ajustesRecientes());
                Integer idProducto = entero(req, "producto");
                if (idProducto != null) {
                    req.setAttribute("productoSel", productos.obtener(idProducto));
                    req.setAttribute("lotes", productos.lotes(idProducto));
                }
            });
            case "/inventario/kardex" -> mostrar(req, res, "inventario/kardex", () -> {
                Integer idProducto = entero(req, "producto");
                boolean sinFiltros = req.getParameterMap().isEmpty();
                LocalDate desde = sinFiltros ? LocalDate.now().minusDays(30) : fecha(req, "desde");
                LocalDate hasta = fecha(req, "hasta");
                Integer idLote = idProducto == null ? null : entero(req, "lote");
                req.setAttribute("productos", productos.activos());
                req.setAttribute("productoSel", idProducto);
                req.setAttribute("loteSel", idLote);
                req.setAttribute("desde", desde);
                req.setAttribute("hasta", hasta);
                List<MovimientoKardex> movimientos = inventario.kardex(new KardexFiltro(idProducto, idLote, desde, hasta));
                req.setAttribute("movimientos", movimientos);
                req.setAttribute("limite", InventarioService.LIMITE_KARDEX);
                req.setAttribute("totalEntradas", movimientos.stream().map(MovimientoKardex::entrada)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
                req.setAttribute("totalSalidas", movimientos.stream().map(MovimientoKardex::salida)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
                if (idProducto != null) {
                    req.setAttribute("producto", productos.obtener(idProducto));
                    req.setAttribute("lotes", productos.lotes(idProducto));
                    req.setAttribute("conciliacion", inventario.conciliacion(idProducto));
                }
            });
            default -> mostrar(req, res, "inventario/alertas", () -> req.setAttribute("alertas", inventario.alertas()));
        }
    }

    private void mostrarIngreso(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        mostrar(req, res, "inventario/ingreso", () -> {
            int filas = filas(req);
            req.setAttribute("filas", filas);
            req.setAttribute("indices", IntStream.range(0, filas).boxed().toList());
            req.setAttribute("maximoFilas", InventarioService.MAXIMO_LINEAS);
            req.setAttribute("proveedores", productos.proveedores(true));
            req.setAttribute("productos", productos.activos());
            req.setAttribute("tiposDocumento", InventarioService.TIPOS_DOCUMENTO);
            req.setAttribute("hoy", LocalDate.now());
            req.setAttribute("recientes", inventario.ingresosRecientes());
        });
    }

    private int filas(HttpServletRequest req) {
        int filas = NumberUtils.toInt(req.getParameter("filas"), FILAS_INICIALES);
        return Math.max(1, Math.min(filas, InventarioService.MAXIMO_LINEAS));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        switch (req.getServletPath()) {
            case "/inventario/ingreso" -> registrarIngreso(req, res, u);
            case "/inventario/ajuste" -> {
                String producto = texto(req, "producto");
                String volver = "/inventario/ajuste" + (NumberUtils.isDigits(producto) ? "?producto=" + producto : "");
                procesar(req, res, volver, volver, "Ajuste registrado en el kardex y auditado.", () -> {
                    exigirPermiso(req, InventarioService.PERMISO_AJUSTAR);
                    inventario.registrarAjuste(new AjusteInventario(entero(req, "lote"), texto(req, "tipo"),
                            decimal(req, "cantidad"), texto(req, "motivo"), texto(req, "evidencia")), u);
                });
            }
            case "/inventario/alertas" -> procesar(req, res, "/inventario/alertas", "/inventario/alertas", null, () -> {
                int creadas = inventario.generarAlertas(u);
                exito(req, creadas == 0 ? "No hay alertas nuevas: las vigentes ya están pendientes para Farmacia."
                        : "Se generaron " + creadas + " alerta(s) interna(s) para Farmacia.");
            });
            default -> res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    /**
     * Si el documento no cumple una regla se vuelve a mostrar el formulario con lo escrito (no se pierde
     * un documento de varias líneas); si se registra, Post/Redirect/Get.
     */
    private void registrarIngreso(HttpServletRequest req, HttpServletResponse res, UsuarioSesion u)
            throws IOException, ServletException {
        try {
            exigirPermiso(req, InventarioService.PERMISO_INGRESAR);
            List<LineaIngreso> lineas = new ArrayList<>();
            for (int i = 0; i < filas(req); i++) {
                LineaIngreso l = leerLinea(req, i);
                if (l != null) {
                    lineas.add(l);
                }
            }
            IngresoDocumento doc = new IngresoDocumento(entero(req, "proveedor"), texto(req, "tipoDoc"),
                    texto(req, "numDoc"), fecha(req, "fecha"), lineas);
            long id = inventario.registrarIngreso(doc, u);
            exito(req, "Ingreso n.° " + id + " registrado: " + lineas.size() + " línea(s); el stock y el kardex se "
                    + "actualizaron por lote.");
            redirigir(req, res, "/inventario/ingreso");
        } catch (ReglaNegocioException | IllegalArgumentException e) {
            log.info("Ingreso rechazado: {}", e.getMessage());
            req.setAttribute("flash", new Flash("error", e.getMessage()));
            mostrarIngreso(req, res);
        } catch (SQLException e) {
            log.warn("Error de base de datos al registrar ingreso ({}): {}", e.getSQLState(), e.getMessage(), e);
            req.setAttribute("flash", new Flash("error", ErroresBD.mensaje(e)));
            mostrarIngreso(req, res);
        }
    }

    /** Lee la línea i del formulario; null si está completamente vacía. */
    private LineaIngreso leerLinea(HttpServletRequest req, int i) {
        String producto = texto(req, "producto_" + i);
        String lote = texto(req, "lote_" + i);
        String vence = texto(req, "vence_" + i);
        String cantidad = texto(req, "cantidad_" + i);
        String costo = texto(req, "costo_" + i);
        if (StringUtils.isAllBlank(producto, lote, vence, cantidad, costo)) {
            return null;
        }
        String pre = "Línea " + (i + 1) + ": ";
        try {
            return new LineaIngreso(producto == null ? null : Integer.valueOf(producto), lote, fecha(req, "vence_" + i),
                    decimal(req, "cantidad_" + i), decimal(req, "costo_" + i));
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(pre + "seleccione un producto válido.");
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(pre + "revise la fecha de vencimiento, la cantidad y el costo (deben ser "
                    + "una fecha y números válidos).");
        }
    }
}
