package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * Resumen de un turno de caja (RF-30): cobros por medio de pago, ventas confirmadas, anulaciones
 * (devoluciones) y efectivo esperado = monto inicial + efectivo cobrado en ventas confirmadas.
 */
public record ResumenTurno(TurnoCaja turno, List<PorMedio> medios, int ventasConfirmadas,
                           BigDecimal totalConfirmado, BigDecimal descuentos, int ventasAnuladas,
                           BigDecimal totalAnulado, BigDecimal efectivoDevuelto, int ventasPendientes,
                           BigDecimal efectivoEsperado) {

    public ResumenTurno {
        medios = ImmutableList.copyOf(medios);
    }

    /** Cobros de un medio de pago en ventas confirmadas del turno. */
    public record PorMedio(String medio, int pagos, BigDecimal monto) {
    }

    public BigDecimal efectivoCobrado() {
        return efectivoEsperado.subtract(turno.montoInicial());
    }
}
