package pe.vetsalud.erp.modelo;

import java.time.LocalDate;

/**
 * Filtros de la agenda (RF-11): fecha de referencia, vista diaria o semanal, profesional, servicio y estado.
 * Los nulos significan «todos».
 */
public record AgendaFiltro(LocalDate fecha, String vista, Integer idVeterinario, Integer idServicio, String estado) {

    public static final String DIA = "dia";
    public static final String SEMANA = "semana";

    public boolean semanal() {
        return SEMANA.equals(vista);
    }
}
