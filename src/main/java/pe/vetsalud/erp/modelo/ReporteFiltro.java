package pe.vetsalud.erp.modelo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Filtros validados de un reporte de P10 (RF-32 a RF-35).
 *
 * @param vista      sub-reporte (stock, kardex, citas...) o null
 * @param agrupacion criterio de agrupación (dia, servicio, cajero...) o null
 * @param producto   texto para filtrar el kardex por código o nombre (opcional)
 * @param dias       días de anticipación para "por vencer" (RF-24)
 */
public record ReporteFiltro(LocalDate desde, LocalDate hasta, String vista, String agrupacion, String producto,
                            int dias) {

    /** Días calendario del periodo (incluye ambos extremos). */
    public long diasPeriodo() {
        return ChronoUnit.DAYS.between(desde, hasta) + 1;
    }

    /** Límite superior exclusivo para comparar con TIMESTAMPTZ (fecha_hora &lt; hasta + 1). */
    public LocalDate hastaExclusivo() {
        return hasta.plusDays(1);
    }
}
