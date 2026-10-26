package pe.vetsalud.erp.modelo;

import java.time.LocalDate;
import java.util.List;

/** Columna de la vista semanal de la agenda: un día con sus citas (RF-11). */
public record AgendaDia(LocalDate fecha, String nombre, boolean hoy, List<Cita> citas) {

    public AgendaDia {
        citas = List.copyOf(citas);
    }
}
