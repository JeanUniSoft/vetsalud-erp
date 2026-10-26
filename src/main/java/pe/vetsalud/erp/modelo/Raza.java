package pe.vetsalud.erp.modelo;

/** Raza del catálogo; pertenece a una sola especie (la raza determina la especie, 3FN). RF-07. */
public record Raza(int idRaza, int idEspecie, String especie, String nombre) {
}
