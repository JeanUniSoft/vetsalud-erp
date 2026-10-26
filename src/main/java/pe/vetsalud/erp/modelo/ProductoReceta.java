package pe.vetsalud.erp.modelo;

/** Producto del catálogo que se puede recetar (RF-17). */
public record ProductoReceta(int idProducto, String codigo, String nombre, String unidad, boolean requiereReceta,
                             boolean activo) {
}
