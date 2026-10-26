package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/** Producto de la receta de una consulta con el lote FEFO sugerido por fn_lote_sugerido (RF-23, RF-26). */
public record ProductoRecetado(int idProducto, String codigo, String nombre, BigDecimal cantidad, String dosis,
                               String frecuencia, int duracionDias, BigDecimal precio, Integer idLoteSugerido,
                               String loteSugerido, BigDecimal disponible) {
}
