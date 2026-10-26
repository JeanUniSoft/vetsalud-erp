package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Consulta FIRMADA que aún no tiene una venta vigente ("Cobrar atención", RF-26). */
public record ConsultaPorCobrar(long idConsulta, LocalDateTime fechaHora, int idMascota, String mascota,
                                int idCliente, String cliente, String veterinario, String motivo,
                                String diagnostico, int procedimientos, boolean tieneReceta) {
}
