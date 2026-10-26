package pe.vetsalud.erp.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import pe.vetsalud.erp.dao.jdbc.CatalogoInventarioDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.IngresoDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.KardexDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ProductoDaoJdbc;
import pe.vetsalud.erp.modelo.LoteAlerta;
import pe.vetsalud.erp.modelo.Producto;
import pe.vetsalud.erp.modelo.Proveedor;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.InventarioService;
import pe.vetsalud.erp.servicio.ProductoService;
import pe.vetsalud.erp.servicio.ReglaNegocioException;

/**
 * P07 · Inventario por lotes: catálogo de productos, ficha con lotes, categorías y proveedores (RF-20, RF-23, RF-24).
 * Rutas: /inventario · /inventario/producto?id= · /inventario/producto/nuevo · /inventario/producto/editar?id=
 * · /inventario/categorias · /inventario/proveedores
 */
@WebServlet({"/inventario", "/inventario/producto", "/inventario/producto/nuevo", "/inventario/producto/editar",
        "/inventario/categorias", "/inventario/proveedores"})
public class InventarioServlet extends ControladorBase {

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
            case "/inventario/producto" -> mostrar(req, res, "inventario/producto", () -> {
                int id = (int) idObligatorio(req, res, "id");
                req.setAttribute("producto", productos.obtener(id));
                req.setAttribute("lotes", productos.lotes(id));
                req.setAttribute("movimientos", inventario.ultimosMovimientos(id));
                req.setAttribute("conciliacion", inventario.conciliacion(id));
                BigDecimal cantidad = decimal(req, "cantidad");
                if (cantidad != null) {
                    req.setAttribute("cantidadSimulada", cantidad);
                    req.setAttribute("sugerido", productos.loteSugerido(id, cantidad).orElse(null));
                }
            });
            case "/inventario/producto/nuevo" -> mostrar(req, res, "inventario/formulario", () -> datosFormulario(req));
            case "/inventario/producto/editar" -> {
                // Si el producto no existe se vuelve al catálogo (no se muestra el formulario como alta)
                try {
                    req.setAttribute("producto", productos.obtener((int) idObligatorio(req, res, "id")));
                } catch (ReglaNegocioException | IllegalArgumentException e) {
                    error(req, e.getMessage());
                    redirigir(req, res, "/inventario");
                    return;
                } catch (SQLException e) {
                    mostrar(req, res, "error/error", () -> {
                        throw e;
                    });
                    return;
                }
                mostrar(req, res, "inventario/formulario", () -> datosFormulario(req));
            }
            case "/inventario/categorias" -> mostrar(req, res, "inventario/categorias",
                    () -> req.setAttribute("categorias", productos.categorias()));
            case "/inventario/proveedores" -> mostrar(req, res, "inventario/proveedores", () -> {
                req.setAttribute("proveedores", productos.proveedores(false));
                Integer id = entero(req, "id");
                if (id != null) {
                    req.setAttribute("proveedor", productos.obtenerProveedor(id));
                }
            });
            default -> mostrar(req, res, "inventario/lista", () -> {
                req.setAttribute("q", texto(req, "q"));
                req.setAttribute("categoriaSel", entero(req, "categoria"));
                req.setAttribute("soloAlertas", marcado(req, "alertas"));
                req.setAttribute("inactivos", marcado(req, "inactivos"));
                req.setAttribute("categorias", productos.categorias());
                req.setAttribute("productos", productos.buscar(texto(req, "q"), entero(req, "categoria"),
                        marcado(req, "alertas"), marcado(req, "inactivos")));
                List<LoteAlerta> alertas = inventario.alertas();
                req.setAttribute("totalStockMinimo", contar(alertas, "STOCK_MINIMO"));
                req.setAttribute("totalPorVencer", contar(alertas, "POR_VENCER"));
                req.setAttribute("totalVencidos", contar(alertas, "VENCIDO"));
            });
        }
    }

    private static long contar(List<LoteAlerta> alertas, String tipo) {
        return alertas.stream().filter(a -> tipo.equals(a.alerta())).count();
    }

    private void datosFormulario(HttpServletRequest req) throws SQLException {
        req.setAttribute("categorias", productos.categorias());
        req.setAttribute("unidades", Producto.UNIDADES);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        switch (req.getServletPath()) {
            case "/inventario/producto/nuevo", "/inventario/producto/editar" -> guardarProducto(req, res, u);
            case "/inventario/categorias" -> procesar(req, res, "/inventario/categorias", "/inventario/categorias",
                    null, () -> {
                        exigirPermiso(req, ProductoService.PERMISO);
                        Integer id = entero(req, "id");
                        if (id == null) {
                            productos.registrarCategoria(texto(req, "nombre"), u);
                            exito(req, "Categoría registrada.");
                        } else {
                            productos.renombrarCategoria(id, texto(req, "nombre"), u);
                            exito(req, "Categoría actualizada.");
                        }
                    });
            case "/inventario/proveedores" -> {
                Integer id = entero(req, "id");
                procesar(req, res, "/inventario/proveedores",
                        id == null ? "/inventario/proveedores" : "/inventario/proveedores?id=" + id,
                        id == null ? "Proveedor registrado." : "Datos del proveedor actualizados.", () -> {
                            exigirPermiso(req, ProductoService.PERMISO);
                            Proveedor p = new Proveedor(id, texto(req, "ruc"), texto(req, "razonSocial"),
                                    texto(req, "telefono"), texto(req, "correo"), id == null || marcado(req, "activo"));
                            if (id == null) {
                                productos.registrarProveedor(p, u);
                            } else {
                                productos.actualizarProveedor(p, u);
                            }
                        });
            }
            default -> res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void guardarProducto(HttpServletRequest req, HttpServletResponse res, UsuarioSesion u)
            throws IOException, ServletException {
        boolean edicion = "/inventario/producto/editar".equals(req.getServletPath());
        Integer id = edicion ? entero(req, "id") : null;
        String codigo = StringUtils.upperCase(StringUtils.defaultString(StringUtils.deleteWhitespace(texto(req, "codigo"))));
        // Un producto nuevo se muestra filtrado en el catálogo; uno editado vuelve a su ficha
        String destino = edicion ? "/inventario/producto?id=" + id
                : "/inventario?q=" + URLEncoder.encode(codigo, StandardCharsets.UTF_8);
        String volver = edicion ? "/inventario/producto/editar?id=" + id : "/inventario/producto/nuevo";
        procesar(req, res, destino, volver, edicion ? "Producto actualizado (los movimientos históricos se conservan)."
                : "Producto registrado. Registre un ingreso para darle stock.", () -> {
                    exigirPermiso(req, ProductoService.PERMISO);
                    Producto datos = Producto.datos(id, entero(req, "idCategoria"), texto(req, "codigo"),
                            texto(req, "nombre"), texto(req, "unidad"), decimal(req, "precioVenta"),
                            decimal(req, "stockMinimo"), marcado(req, "requiereReceta"), !edicion || marcado(req, "activo"));
                    if (edicion) {
                        productos.actualizar(datos, u);
                    } else {
                        productos.registrar(datos, u);
                    }
                });
    }
}
