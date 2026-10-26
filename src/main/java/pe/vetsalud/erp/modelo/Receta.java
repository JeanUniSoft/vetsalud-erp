package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;
import java.util.List;

/** Receta vinculada a una consulta (RF-17) con sus medicamentos. */
public record Receta(long idReceta, long idConsulta, LocalDateTime fecha, String indicaciones, List<RecetaLinea> lineas) {

    public Receta {
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
    }

    /** Número visible de la receta (N.° 000012). */
    public String numero() {
        return String.format("%06d", idReceta);
    }
}
