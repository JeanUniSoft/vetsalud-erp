package pe.vetsalud.erp.modelo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fila genérica de un reporte (RF-33 a RF-36). Cada valor conserva su tipo (String, Long, BigDecimal,
 * LocalDate, LocalDateTime) para que cada exportador le dé el formato adecuado. Admite valores nulos.
 */
public record FilaReporte(List<Object> valores) {

    public FilaReporte {
        valores = Collections.unmodifiableList(Arrays.asList(valores.toArray()));
    }

    public static FilaReporte de(Object... valores) {
        return new FilaReporte(Arrays.asList(valores));
    }

    public Object valor(int columna) {
        return columna < valores.size() ? valores.get(columna) : null;
    }
}
