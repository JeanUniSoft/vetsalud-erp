package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Línea de una venta: un servicio o un producto de un lote concreto, nunca ambos (ck_detalle_servicio_o_producto).
 */
public record LineaVenta(Long idDetalle, Integer idServicio, Integer idLote, Integer idProducto, String codigoProducto,
                         String descripcion, String codigoLote, LocalDate fechaVencimiento, BigDecimal cantidad,
                         BigDecimal precioUnitario, BigDecimal importe) {

    public static LineaVenta servicio(int idServicio, String nombre, BigDecimal cantidad, BigDecimal precio) {
        return new LineaVenta(null, idServicio, null, null, null, nombre, null, null, cantidad, precio,
                importe(cantidad, precio));
    }

    public static LineaVenta producto(Producto p, Lote lote, BigDecimal cantidad, BigDecimal precio) {
        return new LineaVenta(null, null, lote.idLote(), p.idProducto(), p.codigo(), p.nombre(), lote.codigoLote(),
                lote.fechaVencimiento(), cantidad, precio, importe(cantidad, precio));
    }

    public static BigDecimal importe(BigDecimal cantidad, BigDecimal precio) {
        return cantidad.multiply(precio).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean esProducto() {
        return idLote != null;
    }

    public LineaVenta conId(long id) {
        return new LineaVenta(id, idServicio, idLote, idProducto, codigoProducto, descripcion, codigoLote,
                fechaVencimiento, cantidad, precioUnitario, importe);
    }
}
