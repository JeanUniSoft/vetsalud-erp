package pe.vetsalud.erp.dao;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.ConsultaPorCobrar;
import pe.vetsalud.erp.modelo.LineaVenta;
import pe.vetsalud.erp.modelo.MascotaVenta;
import pe.vetsalud.erp.modelo.Pago;
import pe.vetsalud.erp.modelo.ProductoRecetado;
import pe.vetsalud.erp.modelo.ServicioVendible;
import pe.vetsalud.erp.modelo.Venta;
import pe.vetsalud.erp.modelo.VentaFiltro;
import pe.vetsalud.erp.modelo.VentaTotales;

/**
 * Punto de venta (P08): ventas, detalle y pagos (RF-26 a RF-31).
 * Se llama VentaPosDao para no confundirse con el VentaDAO de reportes (S10).
 */
public interface VentaPosDao {

    /** Crea la venta PENDIENTE con sus líneas iniciales en una sola transacción. */
    long crear(int idTurno, int idCliente, Integer idMascota, Long idConsulta, List<LineaVenta> lineas,
               BigDecimal subtotal, int idUsuario) throws SQLException;

    Optional<Venta> buscarPorId(long idVenta) throws SQLException;

    List<Venta> listar(VentaFiltro filtro, int limite) throws SQLException;

    List<LineaVenta> lineas(long idVenta) throws SQLException;

    List<Pago> pagos(long idVenta) throws SQLException;

    /** Agrega líneas y actualiza subtotal/descuento/total de la venta pendiente (misma transacción). */
    void agregarLineas(long idVenta, List<LineaVenta> lineas, VentaTotales totales, int idUsuario) throws SQLException;

    boolean quitarLinea(long idVenta, long idDetalle, VentaTotales totales, int idUsuario) throws SQLException;

    void actualizarTotales(long idVenta, VentaTotales totales, int idUsuario) throws SQLException;

    long agregarPago(Pago pago, int idUsuario) throws SQLException;

    boolean quitarPago(long idVenta, long idPago, int idUsuario) throws SQLException;

    /** fn_confirmar_venta: valida pagos, stock y vencimiento; devuelve el comprobante (T001-00000003). */
    String confirmar(long idVenta, BigDecimal descuento, Integer idAutoriza, int idUsuario) throws SQLException;

    /** fn_anular_venta: estado ANULADA y movimientos DEVOLUCION en el kardex (RN-09). */
    void anular(long idVenta, String motivo, int idUsuario) throws SQLException;

    /** fn_descartar_venta: borra una venta PENDIENTE de un turno abierto con sus líneas y pagos. */
    void descartar(long idVenta, int idUsuario) throws SQLException;

    boolean clienteActivo(int idCliente) throws SQLException;

    List<ServicioVendible> serviciosVendibles() throws SQLException;

    Optional<ServicioVendible> servicio(int idServicio) throws SQLException;

    List<MascotaVenta> mascotasDeCliente(int idCliente) throws SQLException;

    /** Consultas FIRMADAS sin venta vigente (idCliente null = de todos los clientes). */
    List<ConsultaPorCobrar> consultasPorCobrar(Integer idCliente) throws SQLException;

    Optional<ConsultaPorCobrar> consultaPorCobrar(long idConsulta) throws SQLException;

    /** Servicio de la cita que originó la consulta, o la consulta general si fue sin cita. */
    Optional<ServicioVendible> servicioBaseDeConsulta(long idConsulta) throws SQLException;

    /** Procedimientos realizados en la consulta como líneas de servicio (precio copiado del momento). */
    List<LineaVenta> procedimientosDeConsulta(long idConsulta) throws SQLException;

    List<ProductoRecetado> recetaDeConsulta(long idConsulta) throws SQLException;

    boolean recetaIncluye(long idConsulta, int idProducto) throws SQLException;
}
