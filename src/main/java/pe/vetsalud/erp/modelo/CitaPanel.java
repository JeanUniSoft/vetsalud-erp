package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Cita resumida para la agenda del panel. */
public record CitaPanel(long idCita, LocalDateTime inicio, String mascota, String especie, String cliente,
                        String veterinario, String servicio, String estado) {
}
