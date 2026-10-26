package pe.vetsalud.erp.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import pe.vetsalud.erp.dao.CatalogoInventarioDao;
import pe.vetsalud.erp.dao.IngresoDao;
import pe.vetsalud.erp.dao.KardexDao;
import pe.vetsalud.erp.dao.ProductoDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.AjusteInventario;
import pe.vetsalud.erp.modelo.IngresoDocumento;
import pe.vetsalud.erp.modelo.IngresoResumen;
import pe.vetsalud.erp.modelo.KardexFiltro;
import pe.vetsalud.erp.modelo.LineaIngreso;
import pe.vetsalud.erp.modelo.Lote;
import pe.vetsalud.erp.modelo.LoteAlerta;
import pe.vetsalud.erp.modelo.LoteConciliacion;
import pe.vetsalud.erp.modelo.MovimientoKardex;
import pe.vetsalud.erp.modelo.Producto;
import pe.vetsalud.erp.modelo.Proveedor;
import pe.vetsalud.erp.seguridad.PoliticaAcceso;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * UC-09 / UC-10: ingresos por lote (RF-21), kardex (RF-22), alertas (RF-24) y ajustes autorizados (RF-25).
 * El stock solo cambia con movimientos del kardex; el trigger fn_kardex_aplicar vuelve a validar que
 * nunca quede negativo (RN-06): esta clase valida antes para dar mensajes claros al usuario.
 */
public class InventarioService {

    public static final String PERMISO_INGRESAR = "INVENTARIO_INGRESAR";
    public static final String PERMISO_AJUSTAR = "INVENTARIO_AJUSTAR";
    public static final List<String> TIPOS_DOCUMENTO = ImmutableList.of("FACTURA", "BOLETA", "GUIA");
    public static final List<String> TIPOS_AJUSTE = ImmutableList.of("AJUSTE_ENTRADA", "AJUSTE_SALIDA");
    /** Motivos tipificados de ajuste (RF-25); la evidencia (acta u observación) se escribe aparte. */
    public static final List<String> MOTIVOS_AJUSTE = ImmutableList.of("Conteo físico (inventario)",
            "Merma o rotura", "Baja por vencimiento", "Uso interno / muestra médica", "Devolución a proveedor",
            "Corrección de registro");
    public static final int MAXIMO_LINEAS = 15;
    public static final int LIMITE_KARDEX = 500;
    private static final Set<String> TIPOS_ALERTA = ImmutableSet.of("STOCK_MINIMO", "POR_VENCER", "VENCIDO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal MAXIMO_CANTIDAD = new BigDecimal("100000");

    private final IngresoDao ingresos;
    private final KardexDao kardex;
    private final ProductoDao productos;
    private final CatalogoInventarioDao catalogo;
    private final ParametroService parametros;
    private final Clock reloj;

    public InventarioService(IngresoDao ingresos, KardexDao kardex, ProductoDao productos,
                             CatalogoInventarioDao catalogo, ParametroService parametros, Clock reloj) {
        this.ingresos = Preconditions.checkNotNull(ingresos);
        this.kardex = Preconditions.checkNotNull(kardex);
        this.productos = Preconditions.checkNotNull(productos);
        this.catalogo = Preconditions.checkNotNull(catalogo);
        this.parametros = Preconditions.checkNotNull(parametros);
        this.reloj = Preconditions.checkNotNull(reloj);
    }

    private int dias() {
        return parametros.entero(ParametroService.DIAS_POR_VENCER, 30);
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    // ------------------------------------------------------------------ RF-21 ingresos

    /** Registra el documento completo en una sola transacción y devuelve el id del ingreso. */
    public long registrarIngreso(IngresoDocumento d, UsuarioSesion u) throws SQLException {
        if (!u.puede(PERMISO_INGRESAR)) {
            throw new ReglaNegocioException("Su rol no tiene el permiso " + PERMISO_INGRESAR + " para registrar ingresos.");
        }
        IngresoDocumento doc = validarIngreso(d);
        long id = ingresos.registrar(doc, u.idUsuario());
        BigDecimal unidades = doc.lineas().stream().map(LineaIngreso::cantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
        Auditoria.registrar("INGRESO_REGISTRAR", "ingreso", "id=" + id + " doc=" + doc.tipoDoc() + " " + doc.numDoc()
                + " lineas=" + doc.lineas().size() + " unidades=" + unidades.toPlainString() + " por=" + u.usuario());
        return id;
    }

    IngresoDocumento validarIngreso(IngresoDocumento d) throws SQLException {
        if (d.idProveedor() == null) {
            throw new ReglaNegocioException("Seleccione el proveedor.");
        }
        Proveedor proveedor = catalogo.proveedorPorId(d.idProveedor())
                .orElseThrow(() -> new ReglaNegocioException("El proveedor no existe."));
        if (!proveedor.activo()) {
            throw new ReglaNegocioException("El proveedor " + proveedor.razonSocial() + " está inactivo.");
        }
        String tipo = StringUtils.upperCase(StringUtils.trimToNull(d.tipoDoc()));
        if (!TIPOS_DOCUMENTO.contains(tipo)) {
            throw new ReglaNegocioException("Seleccione el tipo de documento: factura, boleta o guía.");
        }
        String numero = StringUtils.upperCase(StringUtils.deleteWhitespace(Validaciones.requerido(d.numDoc(),
                "Número de documento")));
        if (!numero.matches("^[A-Z0-9][A-Z0-9-]{0,19}$")) {
            throw new ReglaNegocioException("El número de documento admite hasta 20 letras, números o guiones (ej. F001-00012345).");
        }
        LocalDate fecha = d.fecha() == null ? hoy() : d.fecha();
        if (fecha.isAfter(hoy())) {
            throw new ReglaNegocioException("La fecha del documento no puede ser futura.");
        }
        if (ingresos.existeDocumento(proveedor.idProveedor(), tipo, numero)) {
            throw new ReglaNegocioException("El documento " + tipo + " " + numero + " de " + proveedor.razonSocial()
                    + " ya fue registrado (documento único por proveedor).");
        }
        if (d.lineas().isEmpty()) {
            throw new ReglaNegocioException("Agregue al menos una línea con producto, lote, cantidad y costo.");
        }
        if (d.lineas().size() > MAXIMO_LINEAS) {
            throw new ReglaNegocioException("Un ingreso admite como máximo " + MAXIMO_LINEAS + " líneas.");
        }
        List<LineaIngreso> lineas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        int n = 0;
        for (LineaIngreso l : d.lineas()) {
            n++;
            LineaIngreso v = validarLinea(n, l);
            if (!vistos.add(v.idProducto() + "|" + v.codigoLote())) {
                throw new ReglaNegocioException("Línea " + n + ": el lote " + v.codigoLote()
                        + " ya está en otra línea del documento; sume las cantidades en una sola.");
            }
            lineas.add(v);
        }
        return new IngresoDocumento(proveedor.idProveedor(), tipo, numero, fecha, lineas);
    }

    private LineaIngreso validarLinea(int n, LineaIngreso l) throws SQLException {
        String pre = "Línea " + n + ": ";
        if (l.idProducto() == null) {
            throw new ReglaNegocioException(pre + "seleccione el producto.");
        }
        Producto p = productos.buscarPorId(l.idProducto(), dias())
                .orElseThrow(() -> new ReglaNegocioException(pre + "el producto no existe."));
        if (!p.activo()) {
            throw new ReglaNegocioException(pre + p.nombre() + " está inactivo; actívelo antes de ingresar stock.");
        }
        String lote = StringUtils.upperCase(StringUtils.deleteWhitespace(StringUtils.trimToNull(l.codigoLote())));
        if (lote == null) {
            throw new ReglaNegocioException(pre + "indique el código de lote de " + p.nombre()
                    + " (use SIN-LOTE para accesorios sin lote).");
        }
        if (!lote.matches("^[A-Z0-9][A-Z0-9-]{0,29}$")) {
            throw new ReglaNegocioException(pre + "el código de lote admite hasta 30 letras, números o guiones.");
        }
        validarCantidad(pre, l.cantidad(), p);
        if (l.costoUnitario() == null || l.costoUnitario().signum() < 0) {
            throw new ReglaNegocioException(pre + "el costo unitario es obligatorio y no puede ser negativo.");
        }
        LocalDate vence = l.fechaVencimiento();
        if (vence == null && !esAccesorio(p)) {
            throw new ReglaNegocioException(pre + p.nombre() + " requiere fecha de vencimiento (solo los accesorios "
                    + "pueden ingresar sin vencimiento).");
        }
        if (vence != null && !vence.isAfter(hoy())) {
            throw new ReglaNegocioException(pre + "el lote " + lote + " vence el " + vence.format(FECHA)
                    + ": no se puede ingresar mercadería vencida o que vence hoy.");
        }
        Optional<Lote> existente = ingresos.lotePorCodigo(p.idProducto(), lote);
        if (existente.isPresent() && !Objects.equals(existente.get().fechaVencimiento(), vence)) {
            String registrado = existente.get().fechaVencimiento() == null ? "sin vencimiento"
                    : "con vencimiento " + existente.get().fechaVencimiento().format(FECHA);
            throw new ReglaNegocioException(pre + "el lote " + lote + " ya existe " + registrado
                    + "; verifique el código o la fecha.");
        }
        return new LineaIngreso(p.idProducto(), existente.map(Lote::codigoLote).orElse(lote), vence,
                l.cantidad().setScale(2, RoundingMode.HALF_UP), l.costoUnitario().setScale(2, RoundingMode.HALF_UP));
    }

    /** Los accesorios (collares, correas...) no son perecibles y pueden no tener vencimiento. */
    static boolean esAccesorio(Producto p) {
        return StringUtils.containsIgnoreCase(p.categoria(), "accesorio");
    }

    private static void validarCantidad(String pre, BigDecimal cantidad, Producto p) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ReglaNegocioException(pre + "la cantidad debe ser mayor que cero.");
        }
        if (cantidad.compareTo(MAXIMO_CANTIDAD) >= 0) {
            throw new ReglaNegocioException(pre + "la cantidad es demasiado grande.");
        }
        if (!p.admiteDecimales() && cantidad.stripTrailingZeros().scale() > 0) {
            throw new ReglaNegocioException(pre + "la cantidad de " + p.nombre() + " debe ser entera (unidad "
                    + p.unidad() + ").");
        }
    }

    public List<IngresoResumen> ingresosRecientes() throws SQLException {
        return ingresos.recientes(10);
    }

    // ------------------------------------------------------------------ RF-25 ajustes

    public long registrarAjuste(AjusteInventario a, UsuarioSesion u) throws SQLException {
        if (!u.puede(PERMISO_AJUSTAR)) {
            throw new ReglaNegocioException("Solo los usuarios autorizados (permiso " + PERMISO_AJUSTAR
                    + ") pueden ajustar el stock (RF-25).");
        }
        String tipo = StringUtils.trimToNull(a.tipo());
        if (!TIPOS_AJUSTE.contains(tipo)) {
            throw new ReglaNegocioException("Seleccione si el ajuste es de entrada (+) o de salida (−).");
        }
        if (a.idLote() == null) {
            throw new ReglaNegocioException("Seleccione el lote que se ajusta.");
        }
        Lote lote = productos.lotePorId(a.idLote(), dias())
                .orElseThrow(() -> new ReglaNegocioException("El lote no existe."));
        Producto p = productos.buscarPorId(lote.idProducto(), dias()).orElseThrow();
        validarCantidad("", a.cantidad(), p);
        String motivo = StringUtils.normalizeSpace(StringUtils.trimToNull(a.motivo()));
        if (motivo == null) {
            throw new ReglaNegocioException("Todo ajuste requiere un motivo (RF-25).");
        }
        String evidencia = StringUtils.normalizeSpace(StringUtils.trimToNull(a.evidencia()));
        if (evidencia == null || evidencia.length() < 4) {
            throw new ReglaNegocioException("Registre la evidencia del ajuste: n.° de acta u observación (RF-25).");
        }
        AjusteInventario ajuste = new AjusteInventario(lote.idLote(), tipo, a.cantidad().setScale(2, RoundingMode.HALF_UP),
                motivo, evidencia);
        Validaciones.longitudMaxima(ajuste.textoMotivo(), 200, "Motivo y evidencia");
        if (tipo.equals("AJUSTE_SALIDA") && ajuste.cantidad().compareTo(lote.stockActual()) > 0) {
            throw new ReglaNegocioException("Stock insuficiente en el lote " + lote.codigoLote() + ": disponible "
                    + lote.stockActual().stripTrailingZeros().toPlainString() + " (RN-06).");
        }
        if (tipo.equals("AJUSTE_ENTRADA") && !lote.vigenteEn(hoy())) {
            throw new ReglaNegocioException("El lote " + lote.codigoLote() + " está vencido: no se puede aumentar su stock.");
        }
        long id = kardex.registrarAjuste(ajuste, u.idUsuario());
        Auditoria.registrar("AJUSTE_INVENTARIO", "movimiento_inventario", "id=" + id + " lote=" + lote.codigoLote()
                + " tipo=" + tipo + " cantidad=" + ajuste.cantidad().toPlainString() + " motivo=" + motivo
                + " por=" + u.usuario());
        return id;
    }

    public List<MovimientoKardex> ajustesRecientes() throws SQLException {
        return kardex.ajustesRecientes(15);
    }

    // ------------------------------------------------------------------ RF-22 kardex

    public List<MovimientoKardex> kardex(KardexFiltro f) throws SQLException {
        if (f.desde() != null && f.hasta() != null && f.desde().isAfter(f.hasta())) {
            throw new ReglaNegocioException("La fecha «desde» no puede ser posterior a «hasta».");
        }
        return kardex.movimientos(f, LIMITE_KARDEX);
    }

    public List<MovimientoKardex> ultimosMovimientos(int idProducto) throws SQLException {
        return kardex.movimientos(new KardexFiltro(idProducto, null, null, null), 15);
    }

    public List<LoteConciliacion> conciliacion(int idProducto) throws SQLException {
        return kardex.conciliacion(idProducto);
    }

    // ------------------------------------------------------------------ RF-24 alertas

    public List<LoteAlerta> alertas() throws SQLException {
        return kardex.alertas();
    }

    /**
     * Crea alertas internas para FARMACIA a partir de v_alerta_inventario sin duplicar las que siguen
     * pendientes (mismo tipo y mensaje). Devuelve cuántas se crearon.
     */
    public int generarAlertas(UsuarioSesion u) throws SQLException {
        Set<String> pendientes = new HashSet<>(kardex.alertasPendientes(PoliticaAcceso.FARMACIA));
        int creadas = 0;
        for (LoteAlerta a : kardex.alertas()) {
            if (!TIPOS_ALERTA.contains(a.alerta())) {
                continue;
            }
            String mensaje = mensajeAlerta(a);
            if (pendientes.add(a.alerta() + "|" + mensaje)) {
                kardex.crearAlerta(PoliticaAcceso.FARMACIA, a.alerta(), mensaje, u.idUsuario());
                creadas++;
            }
        }
        Auditoria.registrar("ALERTAS_INVENTARIO_GENERAR", "alerta", "creadas=" + creadas + " por=" + u.usuario());
        return creadas;
    }

    /** Mensaje estable (sin cantidades que cambian) para reconocer una alerta ya pendiente. */
    static String mensajeAlerta(LoteAlerta a) {
        String producto = a.producto() + " (" + a.codigo() + ")";
        String lotes = a.lotes() == null ? "" : " lote " + a.lotes();
        String vence = a.vence() == null ? "" : a.vence().format(FECHA);
        String m = switch (a.alerta()) {
            case "STOCK_MINIMO" -> producto + " está en o por debajo de su stock mínimo";
            case "POR_VENCER" -> producto + lotes + " vence el " + vence;
            default -> producto + lotes + " venció el " + vence + ": retírelo con un ajuste de salida";
        };
        return StringUtils.abbreviate(m, 300);
    }
}
