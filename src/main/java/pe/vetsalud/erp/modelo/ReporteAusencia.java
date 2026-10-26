package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Cita cancelada o no asistida (RF-35): detalle para el seguimiento del ausentismo. */
public record ReporteAusencia(LocalDateTime inicio, String mascota, String servicio, String profesional,
                              String estado, String motivo) {
}
