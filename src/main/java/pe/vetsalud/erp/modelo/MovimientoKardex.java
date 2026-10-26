package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Fila del kardex (vista v_kardex) con el usuario que registró el movimiento (RF-22). */
public record MovimientoKardex(long idMovimiento, String codigo, String producto, String codigoLote,
                               LocalDate fechaVencimiento, LocalDateTime fechaHora, String tipo,
                               BigDecimal entrada, BigDecimal salida, BigDecimal saldoLote, String motivo,
                               String usuario) {
}
