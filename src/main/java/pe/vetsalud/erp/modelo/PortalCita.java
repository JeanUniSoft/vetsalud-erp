package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/**
 * Cita vista desde el portal del cliente (P11): solo fecha, hora, servicio, profesional y estado.
 * No incluye el motivo de la cita, que puede contener información clínica.
 */
public record PortalCita(long idCita, int idMascota, String mascota, LocalDateTime inicio, String servicio,
                         String profesional, String estado) {
}
