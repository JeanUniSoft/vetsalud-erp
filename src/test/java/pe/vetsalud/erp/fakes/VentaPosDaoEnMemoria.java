package pe.vetsalud.erp.fakes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import pe.vetsalud.erp.dao.VentaPosDao;
import pe.vetsalud.erp.modelo.ConsultaPorCobrar;
import pe.vetsalud.erp.modelo.LineaVenta;
import pe.vetsalud.erp.modelo.MascotaVenta;
import pe.vetsalud.erp.modelo.Pago;
import pe.vetsalud.erp.modelo.ProductoRecetado;
import pe.vetsalud.erp.modelo.ServicioVendible;
import pe.vetsalud.erp.modelo.TurnoCaja;
import pe.vetsalud.erp.modelo.Venta;
import pe.vetsalud.erp.modelo.VentaFiltro;
import pe.vetsalud.erp.modelo.VentaTotales;

/**
 * Doble de VentaPosDao: guarda ventas, líneas y pagos en memoria. Imita a fn_confirmar_venta (correlativo)
 * y a fn_anular_venta (estado ANULADA) para probar el servicio sin PostgreSQL.
 */
public class VentaPosDaoEnMemoria implements VentaPosDao {

    public final Map<Long, Venta> ventas = new LinkedHashMap<>();
    public final Map<Long, List<LineaVenta>> lineas = new HashMap<>();
    public final Map<Long, List<Pago>> pagos = new HashMap<>();
    public final Set<Integer> clientesActivos = new HashSet<>(Set.of(1, 2));
    public final Map<Integer, List<MascotaVenta>> mascotas = new HashMap<>(Map.of(
            1, List.of(new MascotaVenta(10, "Rocky", "Canino", "Labrador")),
            2, List.of(new MascotaVenta(20, "Misha", "Felino", "Siamés"))));
    public final Map<Integer, ServicioVendible> servicios = new LinkedHashMap<>(Map.of(
            1, new ServicioVendible(1, "Consulta general", "CONSULTA", new BigDecimal("60.00")),
            5, new ServicioVendible(5, "Curación de herida", "PROCEDIMIENTO", new BigDecimal("70.00"))));
    public final Map<Long, ConsultaPorCobrar> consultas = new HashMap<>();
    public final Map<Long, ServicioVendible> servicioBase = new HashMap<>();
    public final Map<Long, List<LineaVenta>> procedimientos = new HashMap<>();
    public final Map<Long, List<ProductoRecetado>> recetas = new HashMap<>();
    public BigDecimal ultimoDescuentoConfirmado;
    public Integer ultimoAutorizador;
    private final CajaDaoEnMemoria caja;
    private long secuencia = 1;
    private long secuenciaDetalle = 1;
    private int correlativo = 0;

    public VentaPosDaoEnMemoria(CajaDaoEnMemoria caja) {
        this.caja = caja;
    }

    private Venta leer(Venta v) {
        TurnoCaja t = caja.turnos.get(v.idTurno());
        BigDecimal pagado = pagos(v.idVenta()).stream().map(Pago::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Venta(v.idVenta(), v.idTurno(), t.idCajero(), t.cajero(), t.abierto(), v.idCliente(), v.cliente(),
                v.documentoCliente(), v.idMascota(), v.mascota(), v.idConsulta(), v.serie(), v.numero(), v.fechaHora(),
                v.subtotal(), v.descuento(), v.total(), v.idAutorizaDescuento(), null, v.estado(), v.motivoAnulacion(),
                v.anuladaEn(), pagado, lineas(v.idVenta()).size());
    }

    private void guardar(Venta v, BigDecimal subtotal, BigDecimal descuento, Integer autoriza, String estado,
                         Integer numero, String motivo) {
        ventas.put(v.idVenta(), new Venta(v.idVenta(), v.idTurno(), v.idCajero(), v.cajero(), v.turnoAbierto(),
                v.idCliente(), v.cliente(), v.documentoCliente(), v.idMascota(), v.mascota(), v.idConsulta(), v.serie(),
                numero, v.fechaHora(), subtotal, descuento, subtotal.subtract(descuento), autoriza, null, estado, motivo,
                motivo == null ? null : LocalDateTime.now(), BigDecimal.ZERO, 0));
    }

    @Override
    public long crear(int idTurno, int idCliente, Integer idMascota, Long idConsulta, List<LineaVenta> nuevas,
                      BigDecimal subtotal, int idUsuario) {
        long id = secuencia++;
        ventas.put(id, new Venta(id, idTurno, 0, null, true, idCliente, "Cliente " + idCliente, "DNI 45678912",
                idMascota, null, idConsulta, "T001", null, LocalDateTime.now(), subtotal, BigDecimal.ZERO, subtotal,
                null, null, Venta.PENDIENTE, null, null, BigDecimal.ZERO, 0));
        lineas.put(id, new ArrayList<>());
        nuevas.forEach(l -> lineas.get(id).add(l.conId(secuenciaDetalle++)));
        return id;
    }

    @Override
    public Optional<Venta> buscarPorId(long idVenta) {
        return Optional.ofNullable(ventas.get(idVenta)).map(this::leer);
    }

    @Override
    public List<Venta> listar(VentaFiltro filtro, int limite) {
        return ventas.values().stream().map(this::leer)
                .filter(v -> filtro.idCajero() == null || v.idCajero() == filtro.idCajero())
                .filter(v -> filtro.estado() == null || v.estado().equals(filtro.estado())).toList();
    }

    @Override
    public List<LineaVenta> lineas(long idVenta) {
        return new ArrayList<>(lineas.getOrDefault(idVenta, List.of()));
    }

    @Override
    public List<Pago> pagos(long idVenta) {
        return new ArrayList<>(pagos.getOrDefault(idVenta, List.of()));
    }

    @Override
    public void agregarLineas(long idVenta, List<LineaVenta> nuevas, VentaTotales t, int idUsuario) {
        nuevas.forEach(l -> lineas.get(idVenta).add(l.conId(secuenciaDetalle++)));
        actualizarTotales(idVenta, t, idUsuario);
    }

    @Override
    public boolean quitarLinea(long idVenta, long idDetalle, VentaTotales t, int idUsuario) {
        boolean quitada = lineas.get(idVenta).removeIf(l -> l.idDetalle() == idDetalle);
        if (quitada) {
            actualizarTotales(idVenta, t, idUsuario);
        }
        return quitada;
    }

    @Override
    public void actualizarTotales(long idVenta, VentaTotales t, int idUsuario) {
        Venta v = ventas.get(idVenta);
        guardar(v, t.subtotal(), t.descuento(), t.idAutoriza(), v.estado(), v.numero(), null);
    }

    @Override
    public long agregarPago(Pago pago, int idUsuario) {
        long id = secuenciaDetalle++;
        pagos.computeIfAbsent(pago.idVenta(), k -> new ArrayList<>()).add(pago.conId(id));
        return id;
    }

    @Override
    public boolean quitarPago(long idVenta, long idPago, int idUsuario) {
        return pagos.getOrDefault(idVenta, new ArrayList<>()).removeIf(p -> p.idPago() == idPago);
    }

    @Override
    public String confirmar(long idVenta, BigDecimal descuento, Integer idAutoriza, int idUsuario) {
        Venta v = ventas.get(idVenta);
        correlativo++;
        ultimoDescuentoConfirmado = descuento;
        ultimoAutorizador = idAutoriza;
        guardar(v, v.subtotal(), descuento, idAutoriza, Venta.CONFIRMADA, correlativo, null);
        return "T001-" + String.format("%08d", correlativo);
    }

    @Override
    public void anular(long idVenta, String motivo, int idUsuario) {
        Venta v = ventas.get(idVenta);
        guardar(v, v.subtotal(), v.descuento(), v.idAutorizaDescuento(), Venta.ANULADA, v.numero(), motivo);
    }

    @Override
    public void descartar(long idVenta, int idUsuario) {
        ventas.remove(idVenta);
        lineas.remove(idVenta);
        pagos.remove(idVenta);
    }

    @Override
    public boolean clienteActivo(int idCliente) {
        return clientesActivos.contains(idCliente);
    }

    @Override
    public List<ServicioVendible> serviciosVendibles() {
        return new ArrayList<>(servicios.values());
    }

    @Override
    public Optional<ServicioVendible> servicio(int idServicio) {
        return Optional.ofNullable(servicios.get(idServicio));
    }

    @Override
    public List<MascotaVenta> mascotasDeCliente(int idCliente) {
        return mascotas.getOrDefault(idCliente, List.of());
    }

    @Override
    public List<ConsultaPorCobrar> consultasPorCobrar(Integer idCliente) {
        return consultas.values().stream().filter(c -> idCliente == null || c.idCliente() == idCliente).toList();
    }

    @Override
    public Optional<ConsultaPorCobrar> consultaPorCobrar(long idConsulta) {
        boolean cobrada = ventas.values().stream()
                .anyMatch(v -> Long.valueOf(idConsulta).equals(v.idConsulta()) && !v.esAnulada());
        return cobrada ? Optional.empty() : Optional.ofNullable(consultas.get(idConsulta));
    }

    @Override
    public Optional<ServicioVendible> servicioBaseDeConsulta(long idConsulta) {
        return Optional.ofNullable(servicioBase.get(idConsulta));
    }

    @Override
    public List<LineaVenta> procedimientosDeConsulta(long idConsulta) {
        return procedimientos.getOrDefault(idConsulta, List.of());
    }

    @Override
    public List<ProductoRecetado> recetaDeConsulta(long idConsulta) {
        return recetas.getOrDefault(idConsulta, List.of());
    }

    @Override
    public boolean recetaIncluye(long idConsulta, int idProducto) {
        return recetaDeConsulta(idConsulta).stream().anyMatch(r -> r.idProducto() == idProducto);
    }
}
