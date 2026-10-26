package pe.vetsalud.erp.modelo;

/** Rol con su descripción y totales para la matriz de permisos (RF-02). */
public record RolResumen(int idRol, String nombre, String descripcion, boolean activo, int usuarios, int permisos) {
}
