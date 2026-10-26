package pe.vetsalud.erp.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.CatalogoInventarioDao;
import pe.vetsalud.erp.dao.ProductoDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.Categoria;
import pe.vetsalud.erp.modelo.Lote;
import pe.vetsalud.erp.modelo.Producto;
import pe.vetsalud.erp.modelo.Proveedor;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * UC-08 Mantener catálogo de farmacia (RF-20): productos, categorías, unidades, precios, stock mínimo,
 * condición de venta y proveedores. Nada se elimina: se desactiva para conservar el kardex histórico.
 */
public class ProductoService {

    public static final String PERMISO = "INVENTARIO_INGRESAR";
    public static final int LIMITE_LISTADO = 200;
    private static final BigDecimal MAXIMO_PRECIO = new BigDecimal("100000");

    private final ProductoDao productos;
    private final CatalogoInventarioDao catalogo;
    private final ParametroService parametros;

    public ProductoService(ProductoDao productos, CatalogoInventarioDao catalogo, ParametroService parametros) {
        this.productos = Preconditions.checkNotNull(productos);
        this.catalogo = Preconditions.checkNotNull(catalogo);
        this.parametros = Preconditions.checkNotNull(parametros);
    }

    public int diasPorVencer() {
        return parametros.entero(ParametroService.DIAS_POR_VENCER, 30);
    }

    // ------------------------------------------------------------------ productos

    public List<Producto> buscar(String texto, Integer idCategoria, boolean soloAlertas, boolean incluirInactivos)
            throws SQLException {
        return productos.buscar(StringUtils.trimToNull(texto), idCategoria, soloAlertas, incluirInactivos,
                diasPorVencer(), LIMITE_LISTADO);
    }

    /** Productos activos para listas desplegables (ingresos, ajustes, kardex, ventas). */
    public List<Producto> activos() throws SQLException {
        return productos.buscar(null, null, false, false, diasPorVencer(), LIMITE_LISTADO);
    }

    public Producto obtener(int idProducto) throws SQLException {
        return productos.buscarPorId(idProducto, diasPorVencer())
                .orElseThrow(() -> new ReglaNegocioException("El producto no existe."));
    }

    public List<Lote> lotes(int idProducto) throws SQLException {
        return productos.lotes(idProducto, diasPorVencer());
    }

    /** RF-23 / RN-07: lote vigente con vencimiento más próximo que cubre la cantidad (fn_lote_sugerido). */
    public Optional<Lote> loteSugerido(int idProducto, BigDecimal cantidad) throws SQLException {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ReglaNegocioException("Indique una cantidad mayor que cero para sugerir el lote.");
        }
        Optional<Integer> id = productos.loteSugerido(idProducto, cantidad);
        return id.isEmpty() ? Optional.empty() : productos.lotePorId(id.get(), diasPorVencer());
    }

    public int registrar(Producto datos, UsuarioSesion u) throws SQLException {
        exigirPermiso(u);
        Producto p = normalizarYValidar(datos);
        if (productos.existeCodigo(p.codigo(), null)) {
            throw new ReglaNegocioException("Ya existe un producto con el código " + p.codigo() + ".");
        }
        int id = productos.insertar(p, u.idUsuario());
        Auditoria.registrar("PRODUCTO_REGISTRAR", "producto", "id=" + id + " codigo=" + p.codigo() + " por=" + u.usuario());
        return id;
    }

    public void actualizar(Producto datos, UsuarioSesion u) throws SQLException {
        exigirPermiso(u);
        Preconditions.checkArgument(datos.idProducto() != null, "Falta el identificador del producto.");
        Producto actual = obtener(datos.idProducto());
        Producto p = normalizarYValidar(datos);
        if (productos.existeCodigo(p.codigo(), p.idProducto())) {
            throw new ReglaNegocioException("Otro producto ya usa el código " + p.codigo() + ".");
        }
        productos.actualizar(p, u.idUsuario());
        Auditoria.registrar(actual.activo() && !p.activo() ? "PRODUCTO_DESACTIVAR" : "PRODUCTO_ACTUALIZAR", "producto",
                "id=" + p.idProducto() + " codigo=" + p.codigo() + " por=" + u.usuario());
    }

    Producto normalizarYValidar(Producto d) throws SQLException {
        String codigo = StringUtils.upperCase(StringUtils.deleteWhitespace(Validaciones.requerido(d.codigo(), "Código")));
        if (!codigo.matches("^[A-Z0-9][A-Z0-9-]{2,19}$")) {
            throw new ReglaNegocioException("El código debe tener de 3 a 20 letras, números o guiones (ej. MED-006).");
        }
        String nombre = StringUtils.normalizeSpace(Validaciones.requerido(d.nombre(), "Nombre del producto"));
        Validaciones.longitudMaxima(nombre, 120, "Nombre del producto");
        if (d.idCategoria() == null || catalogo.categorias().stream().noneMatch(c -> c.idCategoria() == d.idCategoria())) {
            throw new ReglaNegocioException("Seleccione una categoría válida.");
        }
        String unidad = StringUtils.upperCase(StringUtils.trimToNull(d.unidad()));
        if (!Producto.UNIDADES.contains(unidad)) {
            throw new ReglaNegocioException("Seleccione una unidad válida: " + String.join(", ", Producto.UNIDADES) + ".");
        }
        BigDecimal precio = d.precioVenta();
        if (precio == null || precio.signum() < 0 || precio.compareTo(MAXIMO_PRECIO) >= 0) {
            throw new ReglaNegocioException("El precio de venta debe ser un monto entre S/ 0.00 y S/ 99,999.99.");
        }
        BigDecimal minimo = d.stockMinimo() == null ? BigDecimal.ZERO : d.stockMinimo();
        if (minimo.signum() < 0) {
            throw new ReglaNegocioException("El stock mínimo no puede ser negativo.");
        }
        return Producto.datos(d.idProducto(), d.idCategoria(), codigo, nombre, unidad,
                precio.setScale(2, RoundingMode.HALF_UP), minimo.setScale(2, RoundingMode.HALF_UP), d.requiereReceta(),
                d.activo());
    }

    // ------------------------------------------------------------------ categorías

    public List<Categoria> categorias() throws SQLException {
        return catalogo.categorias();
    }

    public int registrarCategoria(String nombre, UsuarioSesion u) throws SQLException {
        exigirPermiso(u);
        String n = validarNombreCategoria(nombre, null);
        int id = catalogo.insertarCategoria(n, u.idUsuario());
        Auditoria.registrar("CATEGORIA_REGISTRAR", "categoria", "id=" + id + " por=" + u.usuario());
        return id;
    }

    public void renombrarCategoria(int idCategoria, String nombre, UsuarioSesion u) throws SQLException {
        exigirPermiso(u);
        if (catalogo.categorias().stream().noneMatch(c -> c.idCategoria() == idCategoria)) {
            throw new ReglaNegocioException("La categoría no existe.");
        }
        catalogo.renombrarCategoria(idCategoria, validarNombreCategoria(nombre, idCategoria), u.idUsuario());
        Auditoria.registrar("CATEGORIA_RENOMBRAR", "categoria", "id=" + idCategoria + " por=" + u.usuario());
    }

    private String validarNombreCategoria(String nombre, Integer exceptoId) throws SQLException {
        String n = StringUtils.normalizeSpace(Validaciones.requerido(nombre, "Nombre de la categoría"));
        Validaciones.longitudMaxima(n, 60, "Nombre de la categoría");
        if (catalogo.existeCategoria(n, exceptoId)) {
            throw new ReglaNegocioException("Ya existe la categoría «" + n + "».");
        }
        return n;
    }

    // ------------------------------------------------------------------ proveedores

    public List<Proveedor> proveedores(boolean soloActivos) throws SQLException {
        return catalogo.proveedores(soloActivos);
    }

    public Proveedor obtenerProveedor(int idProveedor) throws SQLException {
        return catalogo.proveedorPorId(idProveedor)
                .orElseThrow(() -> new ReglaNegocioException("El proveedor no existe."));
    }

    public int registrarProveedor(Proveedor datos, UsuarioSesion u) throws SQLException {
        exigirPermiso(u);
        Proveedor p = validarProveedor(datos);
        if (catalogo.existeRuc(p.ruc(), null)) {
            throw new ReglaNegocioException("Ya existe un proveedor con el RUC " + p.ruc() + ".");
        }
        int id = catalogo.insertarProveedor(p, u.idUsuario());
        Auditoria.registrar("PROVEEDOR_REGISTRAR", "proveedor", "id=" + id + " por=" + u.usuario());
        return id;
    }

    public void actualizarProveedor(Proveedor datos, UsuarioSesion u) throws SQLException {
        exigirPermiso(u);
        Preconditions.checkArgument(datos.idProveedor() != null, "Falta el identificador del proveedor.");
        obtenerProveedor(datos.idProveedor());
        Proveedor p = validarProveedor(datos);
        if (catalogo.existeRuc(p.ruc(), p.idProveedor())) {
            throw new ReglaNegocioException("Otro proveedor ya usa el RUC " + p.ruc() + ".");
        }
        catalogo.actualizarProveedor(p, u.idUsuario());
        Auditoria.registrar("PROVEEDOR_ACTUALIZAR", "proveedor", "id=" + p.idProveedor() + " por=" + u.usuario());
    }

    private static Proveedor validarProveedor(Proveedor d) {
        String ruc = StringUtils.deleteWhitespace(Validaciones.requerido(d.ruc(), "RUC"));
        Validaciones.documento("RUC", ruc);
        String razon = StringUtils.normalizeSpace(Validaciones.requerido(d.razonSocial(), "Razón social"));
        Validaciones.longitudMaxima(razon, 120, "Razón social");
        String telefono = StringUtils.deleteWhitespace(StringUtils.trimToNull(d.telefono()));
        Validaciones.telefono(telefono);
        String correo = StringUtils.lowerCase(StringUtils.trimToNull(d.correo()));
        Validaciones.correo(correo);
        return new Proveedor(d.idProveedor(), ruc, razon, telefono, correo, d.activo());
    }

    private static void exigirPermiso(UsuarioSesion u) {
        if (!u.puede(PERMISO)) {
            throw new ReglaNegocioException("Su rol no tiene el permiso " + PERMISO + " para mantener el catálogo.");
        }
    }
}
