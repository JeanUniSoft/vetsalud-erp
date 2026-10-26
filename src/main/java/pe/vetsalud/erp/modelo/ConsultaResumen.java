package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Consulta resumida para listados (consultas abiertas pendientes de firma, RF-18). */
public record ConsultaResumen(long idConsulta, LocalDateTime fechaHora, int idMascota, String mascota, String especie,
                              String cliente, int idVeterinario, String veterinario, String motivo, String estado) {
}
