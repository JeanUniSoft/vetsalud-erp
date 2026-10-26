package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/** Servicio de la clínica (consulta, vacuna, procedimiento...): define la duración de la cita y la tarifa (RF-09, RF-16). */
public record Servicio(int idServicio, String nombre, String tipo, int duracionMin, BigDecimal tarifa, boolean activo) {

    public boolean esConsulta() {
        return "CONSULTA".equals(tipo);
    }
}
