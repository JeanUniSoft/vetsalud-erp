package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Citas de un grupo (profesional, servicio o periodo) por estado (RF-35). */
public record ReporteCitas(String grupo, long total, long atendidas, long enCurso, long programadas,
                           long canceladas, long noAsistio) {

    /** Ausencias = canceladas + no asistió. */
    public long ausencias() {
        return canceladas + noAsistio;
    }

    /** Porcentaje de ausentismo sobre el total de citas del grupo (1 decimal). */
    public BigDecimal porcentajeAusentismo() {
        return total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(ausencias() * 100).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
