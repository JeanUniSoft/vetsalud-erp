package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Evento de la línea de tiempo de la historia clínica (RF-13).
 *
 * @param tipo   CONSULTA | DIAGNOSTICO | PROCEDIMIENTO | RECETA | ADENDA | CONTROL
 * @param lineas detalle adicional (signos, medicamentos...)
 */
public record EventoHistoria(LocalDateTime fechaHora, String tipo, String titulo, String detalle, String autor,
                             String estado, Long idConsulta, List<String> lineas) {

    public EventoHistoria {
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
    }
}
