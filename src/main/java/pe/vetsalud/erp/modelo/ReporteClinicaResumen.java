package pe.vetsalud.erp.modelo;

/** Totales clínicos del periodo para los indicadores de P10 (RF-32, RF-35). */
public record ReporteClinicaResumen(long citas, long atendidas, long ausencias, long consultas,
                                    long procedimientos) {

    public static final ReporteClinicaResumen VACIO = new ReporteClinicaResumen(0, 0, 0, 0, 0);
}
