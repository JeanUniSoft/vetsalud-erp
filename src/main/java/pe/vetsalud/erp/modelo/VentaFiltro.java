package pe.vetsalud.erp.modelo;

import java.time.LocalDate;

/** Filtros del listado de ventas (P08). idCajero null = todos los cajeros (solo administrador). */
public record VentaFiltro(LocalDate desde, LocalDate hasta, String estado, Integer idCajero, Integer idTurno) {
}
