package pe.vetsalud.erp.modelo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Resultado de la agenda (RF-11, RF-19) listo para la vista: rango visible, citas, días de la semana,
 * controles programados y el conteo por estado.
 */
public record AgendaVista(AgendaFiltro filtro, LocalDate desde, LocalDate hasta, LocalDate anterior,
                          LocalDate siguiente, List<Cita> citas, List<AgendaDia> dias,
                          List<ControlProgramado> controles, Map<String, Long> conteoPorEstado) {
}
