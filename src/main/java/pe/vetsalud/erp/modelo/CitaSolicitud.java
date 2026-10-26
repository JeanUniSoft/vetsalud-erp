package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Datos capturados en el formulario para programar una cita (RF-09). El fin lo calcula el servicio. */
public record CitaSolicitud(Integer idMascota, Integer idServicio, Integer idVeterinario, LocalDateTime inicio,
                            String motivo) {
}
