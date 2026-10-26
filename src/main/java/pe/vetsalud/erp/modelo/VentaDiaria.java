package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila de la vista v_ventas_diarias: ventas confirmadas por día y medio de pago (RF-33). */
public record VentaDiaria(LocalDate dia, String medioPago, int ventas, BigDecimal total) {
}
