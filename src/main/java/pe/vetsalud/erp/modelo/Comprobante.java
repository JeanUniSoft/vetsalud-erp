package pe.vetsalud.erp.modelo;

import java.util.List;

import com.google.common.collect.ImmutableList;

/** Comprobante interno (RF-29): cabecera de la venta, detalle y medios de pago. */
public record Comprobante(Venta venta, List<LineaVenta> lineas, List<Pago> pagos) {

    public Comprobante {
        lineas = ImmutableList.copyOf(lineas);
        pagos = ImmutableList.copyOf(pagos);
    }
}
