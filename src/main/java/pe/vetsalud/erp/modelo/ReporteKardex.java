package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Movimiento de la vista v_kardex (RF-22, RF-34): entrada o salida con el saldo del lote. */
public record ReporteKardex(LocalDateTime fecha, String codigo, String producto, String lote, LocalDate vencimiento,
                            String tipo, BigDecimal entrada, BigDecimal salida, BigDecimal saldo, String motivo) {
}
