package pe.vetsalud.erp.modelo;

import java.time.LocalDate;

/** Filtros del kardex: producto, lote y periodo (RF-22). Cualquier valor puede ser null. */
public record KardexFiltro(Integer idProducto, Integer idLote, LocalDate desde, LocalDate hasta) {
}
