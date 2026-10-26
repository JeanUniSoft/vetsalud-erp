package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/** Servicio activo que se puede cobrar en caja, con su tarifa vigente (RF-26). */
public record ServicioVendible(int idServicio, String nombre, String tipo, BigDecimal tarifa) {
}
