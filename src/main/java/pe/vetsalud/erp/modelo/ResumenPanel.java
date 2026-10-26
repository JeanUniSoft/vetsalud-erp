package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/** Indicadores del día para el panel principal (RF-32). */
public record ResumenPanel(int citasHoy, int citasPorAtender, int citasEnSala, int citasAtendidas,
                           int consultasHoy, int consultasAbiertas, int ventasHoy, BigDecimal montoVentasHoy,
                           int turnosAbiertos, int alertasInventario) {
}
