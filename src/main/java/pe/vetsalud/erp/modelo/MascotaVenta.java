package pe.vetsalud.erp.modelo;

/** Mascota activa del cliente para vincular a la venta (RF-26). */
public record MascotaVenta(int idMascota, String nombre, String especie, String raza) {
}
