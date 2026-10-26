package pe.vetsalud.erp.modelo;

import java.time.LocalDate;

/**
 * Control programado (consulta.fecha_control) visto desde el portal del cliente (P11, RF-19).
 * Solo la fecha y el profesional: nunca anamnesis, diagnóstico ni receta.
 */
public record PortalControl(long idConsulta, int idMascota, String mascota, LocalDate fechaControl,
                            String profesional) {
}
