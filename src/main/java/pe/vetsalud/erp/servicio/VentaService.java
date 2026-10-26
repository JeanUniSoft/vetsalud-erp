package pe.vetsalud.erp.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import pe.vetsalud.erp.dao.CajaDao;
import pe.vetsalud.erp.dao.ProductoDao;
import pe.vetsalud.erp.dao.VentaPosDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.Comprobante;
import pe.vetsalud.erp.modelo.ConsultaPorCobrar;
import pe.vetsalud.erp.modelo.LineaVenta;
import pe.vetsalud.erp.modelo.Lote;
import pe.vetsalud.erp.modelo.MascotaVenta;
import pe.vetsalud.erp.modelo.Pago;
import pe.vetsalud.erp.modelo.Producto;
import pe.vetsalud.erp.modelo.ProductoRecetado;
import pe.vetsalud.erp.modelo.ServicioVendible;
import pe.vetsalud.erp.modelo.TurnoCaja;
import pe.vetsalud.erp.modelo.Venta;
import pe.vetsalud.erp.modelo.VentaFiltro;
import pe.vetsalud.erp.modelo.VentaTotales;
import pe.vetsalud.erp.seguridad.PoliticaAcceso;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * UC-11 Registrar venta y pago (RF-26 a RF-29, RF-31) con las reglas RN-06 a RN-12:
 * venta PENDIENTE en el turno abierto del cajero, productos por lote FEFO sin vencidos ni stock negativo,
 * descuento con autorización del administrador sobre el límite, pagos que cuadran con el total,
 * comprobante con correlativo único (fn_confirmar_venta) y anulación controlada (fn_anular_venta).
 */
public class VentaService {

    public static final String PERMISO_PROCESAR = "VENTA_PROCESAR";
    public static final String PERMISO_ANULAR = "VENTA_ANULAR";
    public static final List<String> ESTADOS = ImmutableList.of(Venta.PENDIENTE, Venta.CONFIRMADA, Venta.ANULADA);
    public static final int LIMITE_LISTADO = 200;

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal MAXIMO_CANTIDAD = new BigDecimal("10000");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** RN-07 (FEFO): primero el lote que vence antes; los lotes sin vencimiento al final. */
    static final Comparator<Lote> FEFO = Comparator.comparing(Lote::fechaVencimiento,
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(Lote::idLote);

    private final VentaPosDao ventas;
    private final CajaDao caja;
    private final ProductoDao productos;
    private final AutorizacionDescuento autorizacion;
    private final ParametroService parametros;
    private final Clock reloj;

    public VentaService(VentaPosDao ventas, CajaDao caja, ProductoDao productos, AutorizacionDescuento autorizacion,
                        ParametroService parametros, Clock reloj) {
        this.ventas = Preconditions.checkNotNull(ventas);
        this.caja = Preconditions.checkNotNull(caja);
        this.productos = Preconditions.checkNotNull(productos);
        this.autorizacion = Preconditions.checkNotNull(autorizacion);
        this.parametros = Preconditions.checkNotNull(parametros);
        this.reloj = Preconditions.checkNotNull(reloj);
    }

    // ------------------------------------------------------------------ consultas para las pantallas

    public Optional<TurnoCaja> turnoAbierto(UsuarioSesion u) throws SQLException {
        return caja.turnoAbierto(u.idUsuario());
    }

    /** El cajero ve las ventas de sus turnos; el administrador, las de todos. */
    public List<Venta> listar(LocalDate desde, LocalDate hasta, String estado, UsuarioSesion u) throws SQLException {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException("La fecha «desde» no puede ser posterior a «hasta».");
        }
        String e = StringUtils.trimToNull(estado);
        if (e != null && !ESTADOS.contains(e)) {
            throw new ReglaNegocioException("Estado de venta no válido.");
        }
        return ventas.listar(new VentaFiltro(desde, hasta, e, esAdministrador(u) ? null : u.idUsuario(), null),
                LIMITE_LISTADO);
    }

    public List<Venta> ventasDeTurno(int idTurno) throws SQLException {
        return ventas.listar(new VentaFiltro(null, null, null, null, idTurno), 500);
    }

    public Venta obtener(long idVenta) throws SQLException {
        return ventas.buscarPorId(idVenta).orElseThrow(() -> new ReglaNegocioException("La venta no existe."));
    }

    public Comprobante comprobante(long idVenta) throws SQLException {
        return new Comprobante(obtener(idVenta), ventas.lineas(idVenta), ventas.pagos(idVenta));
    }

    public List<ServicioVendible> servicios() throws SQLException {
        return ventas.serviciosVendibles();
    }

    public List<Producto> productosVendibles() throws SQLException {
        return productos.buscar(null, null, false, false, dias(), 300);
    }

    public Producto producto(int idProducto) throws SQLException {
        return productos.buscarPorId(idProducto, dias())
                .orElseThrow(() -> new ReglaNegocioException("El producto no existe."));
    }

    /** RF-23: lotes que se pueden vender (vigentes y con stock) en orden FEFO; el primero es el sugerido. */
    public List<Lote> lotesVendibles(int idProducto) throws SQLException {
        LocalDate hoy = hoy();
        return productos.lotes(idProducto, dias()).stream()
                .filter(l -> l.vigenteEn(hoy) && l.stockActual().signum() > 0).sorted(FEFO).toList();
    }

    public List<MascotaVenta> mascotas(int idCliente) throws SQLException {
        return ventas.mascotasDeCliente(idCliente);
    }

    public List<ConsultaPorCobrar> consultasPorCobrar(Integer idCliente) throws SQLException {
        return ventas.consultasPorCobrar(idCliente);
    }

    public List<ProductoRecetado> receta(Long idConsulta) throws SQLException {
        return idConsulta == null ? List.of() : ventas.recetaDeConsulta(idConsulta);
    }

    public int porcentajeMaximoDescuento() {
        return parametros.entero(ParametroService.DESCUENTO_MAX_PCT, 10);
    }

    /** Descuento máximo que el cajero aplica sin autorización (RN-08). */
    public BigDecimal limiteDescuento(BigDecimal subtotal) {
        return subtotal.multiply(BigDecimal.valueOf(porcentajeMaximoDescuento())).divide(CIEN, 2, RoundingMode.DOWN);
    }

    // ------------------------------------------------------------------ RF-26 creación

    /** Crea la venta PENDIENTE en el turno abierto del cajero; si hay consulta, carga sus servicios. */
    public long crear(Integer idCliente, Integer idMascota, Long idConsulta, UsuarioSesion u) throws SQLException {
        exigir(u, PERMISO_PROCESAR);
        TurnoCaja turno = caja.turnoAbierto(u.idUsuario()).orElseThrow(() -> new ReglaNegocioException(
                "No tiene un turno de caja abierto: abra su caja antes de registrar ventas (RF-30)."));
        if (idCliente == null) {
            throw new ReglaNegocioException("Seleccione el cliente de la venta.");
        }
        if (!ventas.clienteActivo(idCliente)) {
            throw new ReglaNegocioException("El cliente no existe o está inactivo.");
        }
        Integer mascota = idMascota;
        if (mascota != null && ventas.mascotasDeCliente(idCliente).stream().noneMatch(m -> m.idMascota() == idMascota)) {
            throw new ReglaNegocioException("La mascota seleccionada no pertenece al cliente.");
        }
        List<LineaVenta> lineas = List.of();
        if (idConsulta != null) {
            ConsultaPorCobrar c = ventas.consultaPorCobrar(idConsulta).orElseThrow(() -> new ReglaNegocioException(
                    "La consulta no está firmada o ya tiene una venta registrada."));
            if (c.idCliente() != idCliente) {
                throw new ReglaNegocioException("La consulta pertenece a otro cliente (" + c.cliente() + ").");
            }
            if (mascota != null && mascota != c.idMascota()) {
                throw new ReglaNegocioException("La consulta corresponde a otra mascota (" + c.mascota() + ").");
            }
            mascota = c.idMascota();
            lineas = lineasDeAtencion(idConsulta);
        }
        long id = ventas.crear(turno.idTurno(), idCliente, mascota, idConsulta, lineas, suma(lineas), u.idUsuario());
        Auditoria.registrar("VENTA_CREAR", "venta", "id=" + id + " turno=" + turno.idTurno()
                + (idConsulta == null ? "" : " consulta=" + idConsulta) + " por=" + u.usuario());
        return id;
    }

    /** "Cobrar atención": venta del propietario y la mascota de una consulta firmada sin venta. */
    public long cobrarAtencion(Long idConsulta, UsuarioSesion u) throws SQLException {
        if (idConsulta == null) {
            throw new ReglaNegocioException("Seleccione la atención que va a cobrar.");
        }
        ConsultaPorCobrar c = ventas.consultaPorCobrar(idConsulta).orElseThrow(() -> new ReglaNegocioException(
                "La consulta no está firmada o ya tiene una venta registrada."));
        return crear(c.idCliente(), c.idMascota(), idConsulta, u);
    }

    /** Servicio que motivó la atención (o consulta general) + procedimientos realizados, sin duplicar. */
    List<LineaVenta> lineasDeAtencion(long idConsulta) throws SQLException {
        List<LineaVenta> procedimientos = ventas.procedimientosDeConsulta(idConsulta);
        List<LineaVenta> lineas = new ArrayList<>();
        ventas.servicioBaseDeConsulta(idConsulta).ifPresent(s -> {
            if (procedimientos.stream().noneMatch(p -> Objects.equals(p.idServicio(), s.idServicio()))) {
                lineas.add(LineaVenta.servicio(s.idServicio(), s.nombre(), BigDecimal.ONE, s.tarifa()));
            }
        });
        lineas.addAll(procedimientos);
        return lineas;
    }

    // ------------------------------------------------------------------ RF-26 / RF-23 líneas

    public String agregarServicio(long idVenta, Integer idServicio, BigDecimal cantidad, UsuarioSesion u)
            throws SQLException {
        Venta v = editable(idVenta, u);
        if (idServicio == null) {
            throw new ReglaNegocioException("Seleccione el servicio.");
        }
        ServicioVendible s = ventas.servicio(idServicio)
                .orElseThrow(() -> new ReglaNegocioException("El servicio no existe o está inactivo."));
        BigDecimal c = cantidad == null ? BigDecimal.ONE : cantidad;
        if (c.signum() <= 0 || c.stripTrailingZeros().scale() > 0 || c.compareTo(new BigDecimal("100")) > 0) {
            throw new ReglaNegocioException("La cantidad del servicio debe ser un número entero entre 1 y 100.");
        }
        guardarNuevas(v, ventas.lineas(idVenta), List.of(LineaVenta.servicio(s.idServicio(), s.nombre(), c, s.tarifa())), u);
        return "Se agregó el servicio «" + s.nombre() + "».";
    }

    /**
     * Agrega un producto. Sin lote elegido aplica FEFO (puede repartir entre varios lotes); con lote elegido
     * valida que esté vigente y tenga stock. Considera lo ya reservado en la misma venta.
     */
    public String agregarProducto(long idVenta, Integer idProducto, BigDecimal cantidad, Integer idLote,
                                  UsuarioSesion u) throws SQLException {
        Venta v = editable(idVenta, u);
        if (idProducto == null) {
            throw new ReglaNegocioException("Seleccione el producto.");
        }
        Producto p = producto(idProducto);
        if (!p.activo()) {
            throw new ReglaNegocioException("El producto " + p.nombre() + " está inactivo.");
        }
        validarCantidad(cantidad, p);
        BigDecimal c = cantidad.setScale(2, RoundingMode.HALF_UP);
        if (p.requiereReceta() && (v.idConsulta() == null || !ventas.recetaIncluye(v.idConsulta(), p.idProducto()))) {
            throw new ReglaNegocioException(p.nombre() + " requiere receta: solo se vende al cobrar la atención "
                    + "(consulta firmada) que lo prescribe (RF-20).");
        }
        List<LineaVenta> actuales = ventas.lineas(idVenta);
        Map<Integer, BigDecimal> reservado = reservadoPorLote(actuales);
        List<Lote> lotes = productos.lotes(p.idProducto(), dias());
        List<LineaVenta> nuevas;
        if (idLote != null) {
            Lote l = lotes.stream().filter(x -> x.idLote() == idLote).findFirst()
                    .orElseThrow(() -> new ReglaNegocioException("El lote seleccionado no pertenece al producto."));
            if (!l.vigenteEn(hoy())) {
                throw new ReglaNegocioException("El lote " + l.codigoLote() + " venció el "
                        + l.fechaVencimiento().format(FECHA) + ": no se puede vender (RN-06).");
            }
            BigDecimal disponible = l.stockActual().subtract(reservado.getOrDefault(l.idLote(), BigDecimal.ZERO));
            if (c.compareTo(disponible) > 0) {
                throw new ReglaNegocioException("Stock insuficiente en el lote " + l.codigoLote() + ": disponible "
                        + numero(disponible.max(BigDecimal.ZERO)) + " " + p.unidad() + " (RF-23).");
            }
            nuevas = List.of(LineaVenta.producto(p, l, c, p.precioVenta()));
        } else {
            nuevas = asignarFefo(p, lotes, reservado, c, hoy());
        }
        guardarNuevas(v, actuales, nuevas, u);
        String codigos = nuevas.stream().map(LineaVenta::codigoLote).collect(Collectors.joining(", "));
        return "Se agregó " + p.nombre() + " × " + numero(c) + " (lote " + codigos + ").";
    }

    /** RN-07 FEFO: reparte la cantidad entre los lotes vigentes que vencen primero. */
    static List<LineaVenta> asignarFefo(Producto p, List<Lote> lotes, Map<Integer, BigDecimal> reservado,
                                        BigDecimal cantidad, LocalDate hoy) {
        List<LineaVenta> lineas = new ArrayList<>();
        BigDecimal pendiente = cantidad;
        for (Lote l : lotes.stream().filter(x -> x.vigenteEn(hoy)).sorted(FEFO).toList()) {
            BigDecimal disponible = l.stockActual().subtract(reservado.getOrDefault(l.idLote(), BigDecimal.ZERO));
            if (disponible.signum() <= 0) {
                continue;
            }
            BigDecimal toma = pendiente.min(disponible);
            lineas.add(LineaVenta.producto(p, l, toma, p.precioVenta()));
            pendiente = pendiente.subtract(toma);
            if (pendiente.signum() == 0) {
                return lineas;
            }
        }
        BigDecimal vencido = lotes.stream().filter(x -> !x.vigenteEn(hoy)).map(Lote::stockActual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        throw new ReglaNegocioException("Stock insuficiente de " + p.nombre() + ": disponible "
                + numero(cantidad.subtract(pendiente)) + " " + p.unidad() + " en lotes vigentes"
                + (vencido.signum() > 0 ? " (hay " + numero(vencido) + " en lotes vencidos, que no se venden)" : "")
                + " (RF-23).");
    }

    /** Agrega a la venta los productos de la receta de su consulta que aún no estén en ella. */
    public String agregarReceta(long idVenta, UsuarioSesion u) throws SQLException {
        Venta v = editable(idVenta, u);
        if (v.idConsulta() == null) {
            throw new ReglaNegocioException("La venta no está vinculada a una consulta con receta.");
        }
        List<ProductoRecetado> receta = ventas.recetaDeConsulta(v.idConsulta());
        if (receta.isEmpty()) {
            throw new ReglaNegocioException("La consulta no tiene productos recetados.");
        }
        Set<Integer> enVenta = ventas.lineas(idVenta).stream().filter(LineaVenta::esProducto)
                .map(LineaVenta::idProducto).collect(Collectors.toSet());
        List<String> agregados = new ArrayList<>();
        List<String> omitidos = new ArrayList<>();
        for (ProductoRecetado r : receta) {
            if (enVenta.contains(r.idProducto())) {
                omitidos.add(r.nombre() + " ya está en la venta");
                continue;
            }
            try {
                agregarProducto(idVenta, r.idProducto(), r.cantidad(), null, u);
                agregados.add(r.nombre());
            } catch (ReglaNegocioException e) {
                omitidos.add(e.getMessage());
            }
        }
        if (agregados.isEmpty()) {
            throw new ReglaNegocioException("No se agregó ningún producto de la receta: " + Joiner.on("; ").join(omitidos));
        }
        return "Productos de la receta agregados: " + Joiner.on(", ").join(agregados) + "."
                + (omitidos.isEmpty() ? "" : " Omitidos: " + Joiner.on("; ").join(omitidos));
    }

    public String quitarLinea(long idVenta, Long idDetalle, UsuarioSesion u) throws SQLException {
        Venta v = editable(idVenta, u);
        List<LineaVenta> actuales = ventas.lineas(idVenta);
        LineaVenta linea = actuales.stream().filter(l -> Objects.equals(l.idDetalle(), idDetalle)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("La línea no existe en esta venta."));
        Recalculo r = recalcular(v, suma(actuales).subtract(linea.importe()));
        if (v.pagado().compareTo(r.totales().total()) > 0) {
            throw new ReglaNegocioException("Los pagos registrados (" + soles(v.pagado()) + ") superan el total que "
                    + "quedaría (" + soles(r.totales().total()) + "): quite primero un pago.");
        }
        ventas.quitarLinea(idVenta, idDetalle, r.totales(), u.idUsuario());
        return "Se quitó «" + linea.descripcion() + "»." + (r.descuentoRetirado()
                ? " Se retiró el descuento porque ya no cumple la regla (RN-08); vuelva a aplicarlo si corresponde." : "");
    }

    // ------------------------------------------------------------------ RF-27 descuento

    /**
     * Aplica (o retira con monto 0) el descuento. Hasta el límite lo aplica el cajero; por encima exige
     * usuario y contraseña de un administrador, salvo que quien opera ya tenga DESCUENTO_AUTORIZAR (RN-08).
     */
    public String aplicarDescuento(long idVenta, BigDecimal monto, String usuarioAdmin, char[] claveAdmin,
                                   UsuarioSesion u) throws SQLException {
        try {
            Venta v = editable(idVenta, u);
            BigDecimal subtotal = suma(ventas.lineas(idVenta));
            if (subtotal.signum() == 0) {
                throw new ReglaNegocioException("Agregue servicios o productos antes de aplicar un descuento.");
            }
            BigDecimal d = monto == null ? BigDecimal.ZERO : monto.setScale(2, RoundingMode.HALF_UP);
            if (d.signum() < 0) {
                throw new ReglaNegocioException("El descuento no puede ser negativo.");
            }
            if (d.compareTo(subtotal) > 0) {
                throw new ReglaNegocioException("El descuento no puede superar el subtotal (" + soles(subtotal) + ").");
            }
            Integer autoriza = null;
            if (d.compareTo(limiteDescuento(subtotal)) > 0) {
                if (StringUtils.isBlank(usuarioAdmin) && u.puede(AutorizacionDescuento.PERMISO)) {
                    autoriza = u.idUsuario();
                } else {
                    autoriza = autorizacion.autorizar(usuarioAdmin, claveAdmin);
                }
            }
            BigDecimal total = subtotal.subtract(d);
            if (v.pagado().compareTo(total) > 0) {
                throw new ReglaNegocioException("Los pagos registrados (" + soles(v.pagado()) + ") superan el nuevo total ("
                        + soles(total) + "): quite un pago primero.");
            }
            ventas.actualizarTotales(idVenta, new VentaTotales(subtotal, d, autoriza), u.idUsuario());
            Auditoria.registrar("DESCUENTO_APLICAR", "venta", "id=" + idVenta + " monto=" + d.toPlainString()
                    + " autoriza=" + (autoriza == null ? "-" : autoriza) + " por=" + u.usuario());
            if (d.signum() == 0) {
                return "Se retiró el descuento de la venta.";
            }
            return "Descuento de " + soles(d) + " aplicado" + (autoriza == null ? "." : " con autorización del administrador (RN-08).");
        } finally {
            if (claveAdmin != null) {
                Arrays.fill(claveAdmin, '\0');
            }
        }
    }

    // ------------------------------------------------------------------ RF-28 pagos

    public String registrarPago(long idVenta, String medio, BigDecimal monto, String referencia, UsuarioSesion u)
            throws SQLException {
        Venta v = editable(idVenta, u);
        String m = StringUtils.upperCase(StringUtils.trimToNull(medio));
        if (!Pago.MEDIOS.contains(m)) {
            throw new ReglaNegocioException("Seleccione un medio de pago válido.");
        }
        if (v.numLineas() == 0 || v.total().signum() <= 0) {
            throw new ReglaNegocioException("Agregue servicios o productos antes de registrar pagos.");
        }
        BigDecimal saldo = v.saldo();
        if (saldo.signum() <= 0) {
            throw new ReglaNegocioException("La venta ya está pagada por completo: confírmela para emitir el comprobante.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException("El monto del pago debe ser mayor que cero.");
        }
        String ref = StringUtils.normalizeSpace(StringUtils.trimToNull(referencia));
        if (!Pago.EFECTIVO.equals(m) && ref == null) {
            throw new ReglaNegocioException("Los pagos con tarjeta, Yape/Plin o transferencia requieren el número de "
                    + "operación (RF-28).");
        }
        Validaciones.longitudMaxima(ref, 40, "N.° de operación");
        BigDecimal importe = monto.setScale(2, RoundingMode.HALF_UP);
        BigDecimal vuelto = BigDecimal.ZERO;
        if (importe.compareTo(saldo) > 0) {
            if (!Pago.EFECTIVO.equals(m)) {
                throw new ReglaNegocioException("El pago (" + soles(importe) + ") supera el saldo pendiente ("
                        + soles(saldo) + "); solo el efectivo admite vuelto.");
            }
            vuelto = importe.subtract(saldo);
            importe = saldo;
        }
        long id = ventas.agregarPago(new Pago(null, idVenta, m, importe, ref, null), u.idUsuario());
        Auditoria.registrar("PAGO_REGISTRAR", "pago", "id=" + id + " venta=" + idVenta + " medio=" + m
                + " monto=" + importe.toPlainString() + " por=" + u.usuario());
        BigDecimal nuevoSaldo = saldo.subtract(importe);
        return "Pago de " + soles(importe) + " registrado."
                + (vuelto.signum() > 0 ? " Vuelto para el cliente: " + soles(vuelto) + "." : "")
                + (nuevoSaldo.signum() == 0 ? " La venta está cubierta: ya puede confirmarla."
                : " Saldo pendiente: " + soles(nuevoSaldo) + ".");
    }

    public void quitarPago(long idVenta, Long idPago, UsuarioSesion u) throws SQLException {
        editable(idVenta, u);
        if (idPago == null || !ventas.quitarPago(idVenta, idPago, u.idUsuario())) {
            throw new ReglaNegocioException("El pago no existe en esta venta.");
        }
        Auditoria.registrar("PAGO_QUITAR", "pago", "id=" + idPago + " venta=" + idVenta + " por=" + u.usuario());
    }

    // ------------------------------------------------------------------ RF-28 / RF-29 confirmación

    /** Confirma la venta y devuelve el número de comprobante (RN-11, RN-12). */
    public String confirmar(long idVenta, UsuarioSesion u) throws SQLException {
        Venta v = editable(idVenta, u);
        List<LineaVenta> lineas = ventas.lineas(idVenta);
        if (lineas.isEmpty()) {
            throw new ReglaNegocioException("La venta no tiene líneas: agregue servicios o productos (RF-26).");
        }
        BigDecimal subtotal = suma(lineas);
        BigDecimal descuento = v.descuento();
        if (descuento.compareTo(subtotal) > 0) {
            throw new ReglaNegocioException("El descuento supera el subtotal: corríjalo antes de confirmar.");
        }
        if (descuento.compareTo(limiteDescuento(subtotal)) > 0 && v.idAutorizaDescuento() == null) {
            throw new ReglaNegocioException("El descuento supera el " + porcentajeMaximoDescuento()
                    + " % y no tiene autorización de un administrador (RN-08).");
        }
        BigDecimal total = subtotal.subtract(descuento);
        BigDecimal pagado = ventas.pagos(idVenta).stream().map(Pago::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pagado.compareTo(total) != 0) {
            BigDecimal diferencia = total.subtract(pagado);
            throw new ReglaNegocioException("Los pagos (" + soles(pagado) + ") no coinciden con el total ("
                    + soles(total) + "): " + (diferencia.signum() > 0 ? "falta " + soles(diferencia)
                    : "sobra " + soles(diferencia.negate())) + " (RN-12).");
        }
        validarStock(lineas);
        String comprobante = ventas.confirmar(idVenta, descuento, v.idAutorizaDescuento(), u.idUsuario());
        Auditoria.registrar("VENTA_CONFIRMAR", "venta", "id=" + idVenta + " comprobante=" + comprobante
                + " total=" + total.toPlainString() + " por=" + u.usuario());
        return comprobante;
    }

    /** RF-23: vuelve a comprobar vencimiento y stock de cada lote justo antes de confirmar. */
    void validarStock(List<LineaVenta> lineas) throws SQLException {
        for (Map.Entry<Integer, BigDecimal> e : reservadoPorLote(lineas).entrySet()) {
            Lote l = productos.lotePorId(e.getKey(), dias())
                    .orElseThrow(() -> new ReglaNegocioException("Un lote de la venta ya no existe."));
            if (!l.vigenteEn(hoy())) {
                throw new ReglaNegocioException("El lote " + l.codigoLote() + " venció el "
                        + l.fechaVencimiento().format(FECHA) + ": quítelo de la venta (RN-06).");
            }
            if (l.stockActual().compareTo(e.getValue()) < 0) {
                throw new ReglaNegocioException("Stock insuficiente en el lote " + l.codigoLote() + ": disponible "
                        + numero(l.stockActual()) + ", en la venta " + numero(e.getValue())
                        + ". Quite la línea y vuelva a agregar el producto (RF-23).");
            }
        }
    }

    // ------------------------------------------------------------------ RF-31 anulación

    public void anular(long idVenta, String motivo, UsuarioSesion u) throws SQLException {
        if (!u.puede(PERMISO_ANULAR)) {
            throw new ReglaNegocioException("Solo un usuario con el permiso " + PERMISO_ANULAR
                    + " (administrador) puede anular ventas (RF-31).");
        }
        String m = StringUtils.normalizeSpace(StringUtils.trimToNull(motivo));
        if (m == null || m.length() < 5) {
            throw new ReglaNegocioException("Indique el motivo de la anulación (al menos 5 caracteres).");
        }
        // fn_anular_venta antepone "Anulación: " al motivo del kardex (VARCHAR 200)
        Validaciones.longitudMaxima(m, 180, "Motivo de anulación");
        Venta v = obtener(idVenta);
        if (!v.esConfirmada()) {
            throw new ReglaNegocioException("Solo se anula una venta confirmada (estado actual: "
                    + v.estado().toLowerCase() + ").");
        }
        if (!v.turnoAbierto()) {
            throw new ReglaNegocioException("El turno de caja de esta venta ya se cerró: la anulación debe hacerse "
                    + "antes del cierre (RN-09).");
        }
        ventas.anular(idVenta, m, u.idUsuario());
        Auditoria.registrar("VENTA_ANULAR", "venta", "id=" + idVenta + " comprobante=" + v.comprobante()
                + " motivo=" + m + " por=" + u.usuario());
    }

    // ------------------------------------------------------------------ descarte de pendientes

    /**
     * Descarta una venta PENDIENTE (sin comprobante ni kardex) para que no bloquee el cierre de caja.
     * Solo el cajero del turno o un administrador; las confirmadas se anulan (RN-09).
     */
    public void descartar(long idVenta, UsuarioSesion u) throws SQLException {
        exigir(u, PERMISO_PROCESAR);
        Venta v = obtener(idVenta);
        if (!v.esPendiente()) {
            throw new ReglaNegocioException("Solo se descarta una venta pendiente (estado actual: "
                    + v.estado().toLowerCase() + "); una venta confirmada se anula (RN-09).");
        }
        if (!esAdministrador(u) && v.idCajero() != u.idUsuario()) {
            throw new ReglaNegocioException("Solo el cajero del turno (" + v.cajero()
                    + ") o un administrador puede descartar esta venta.");
        }
        if (!v.turnoAbierto()) {
            throw new ReglaNegocioException("El turno de caja de la venta está cerrado.");
        }
        ventas.descartar(idVenta, u.idUsuario());
        Auditoria.registrar("VENTA_DESCARTAR", "venta", "id=" + idVenta + " turno=" + v.idTurno()
                + " lineas=" + v.numLineas() + " pagado=" + v.pagado().toPlainString() + " por=" + u.usuario());
    }

    // ------------------------------------------------------------------ apoyo

    /** Venta que el usuario puede modificar: PENDIENTE, de su propio turno y con el turno abierto. */
    Venta editable(long idVenta, UsuarioSesion u) throws SQLException {
        exigir(u, PERMISO_PROCESAR);
        Venta v = obtener(idVenta);
        if (!v.esPendiente()) {
            throw new ReglaNegocioException("La venta ya está " + v.estado().toLowerCase() + ": no se puede modificar.");
        }
        if (v.idCajero() != u.idUsuario()) {
            throw new ReglaNegocioException("Solo el cajero del turno (" + v.cajero() + ") puede modificar esta venta.");
        }
        if (!v.turnoAbierto()) {
            throw new ReglaNegocioException("El turno de caja de la venta está cerrado.");
        }
        return v;
    }

    private void guardarNuevas(Venta v, List<LineaVenta> actuales, List<LineaVenta> nuevas, UsuarioSesion u)
            throws SQLException {
        Recalculo r = recalcular(v, suma(actuales).add(suma(nuevas)));
        ventas.agregarLineas(v.idVenta(), nuevas, r.totales(), u.idUsuario());
    }

    /** Si al cambiar el subtotal el descuento deja de cumplir RN-08, se retira (el cajero lo vuelve a aplicar). */
    private Recalculo recalcular(Venta v, BigDecimal subtotal) {
        BigDecimal d = v.descuento();
        Integer autoriza = v.idAutorizaDescuento();
        boolean invalido = d.compareTo(subtotal) > 0
                || (d.compareTo(limiteDescuento(subtotal)) > 0 && autoriza == null);
        if (d.signum() > 0 && invalido) {
            return new Recalculo(new VentaTotales(subtotal, BigDecimal.ZERO, null), true);
        }
        return new Recalculo(new VentaTotales(subtotal, d, d.signum() == 0 ? null : autoriza), false);
    }

    private record Recalculo(VentaTotales totales, boolean descuentoRetirado) {
    }

    private static Map<Integer, BigDecimal> reservadoPorLote(List<LineaVenta> lineas) {
        Map<Integer, BigDecimal> r = new HashMap<>();
        lineas.stream().filter(LineaVenta::esProducto)
                .forEach(l -> r.merge(l.idLote(), l.cantidad(), BigDecimal::add));
        return r;
    }

    private static void validarCantidad(BigDecimal cantidad, Producto p) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ReglaNegocioException("La cantidad debe ser mayor que cero.");
        }
        if (cantidad.compareTo(MAXIMO_CANTIDAD) > 0) {
            throw new ReglaNegocioException("La cantidad es demasiado grande.");
        }
        if (!p.admiteDecimales() && cantidad.stripTrailingZeros().scale() > 0) {
            throw new ReglaNegocioException("La cantidad de " + p.nombre() + " debe ser entera (unidad " + p.unidad() + ").");
        }
    }

    static BigDecimal suma(List<LineaVenta> lineas) {
        return lineas.stream().map(LineaVenta::importe).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean esAdministrador(UsuarioSesion u) {
        return PoliticaAcceso.ADMINISTRADOR.equals(u.rol());
    }

    private static void exigir(UsuarioSesion u, String permiso) {
        if (!u.puede(permiso)) {
            throw new ReglaNegocioException("Su rol no tiene el permiso " + permiso + " para esta acción.");
        }
    }

    private int dias() {
        return parametros.entero(ParametroService.DIAS_POR_VENCER, 30);
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    static String soles(BigDecimal monto) {
        return "S/ " + monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}
