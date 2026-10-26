package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/**
 * Fila del historial de la cita (tabla cita_estado_hist, RF-10 / RF-12): quién, cuándo y qué cambió.
 * Cuando el estado anterior y el nuevo coinciden, la fila es una reprogramación (el motivo describe el cambio).
 */
public record CambioEstadoCita(long idHist, String estadoAnterior, String estadoNuevo, String motivo,
                               String usuario, String nombreUsuario, LocalDateTime fechaHora) {

    public boolean esReprogramacion() {
        return estadoAnterior != null && estadoAnterior.equals(estadoNuevo);
    }
}
