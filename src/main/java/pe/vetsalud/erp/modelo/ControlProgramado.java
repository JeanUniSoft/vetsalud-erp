package pe.vetsalud.erp.modelo;

import java.time.LocalDate;

/** Control posterior programado en una consulta (RF-19) que se muestra en la agenda. */
public record ControlProgramado(long idConsulta, LocalDate fechaControl, int idMascota, String mascota, String especie,
                                String cliente, String telefonoCliente, int idVeterinario, String veterinario,
                                String diagnostico, boolean conCita) {
}
