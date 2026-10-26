package pe.vetsalud.erp.modelo;

import java.util.List;

/** Historia clínica completa de una mascota (RF-13): paciente, eventos cronológicos y consultas. */
public record HistoriaClinica(MascotaClinica mascota, List<EventoHistoria> eventos, List<Consulta> consultas,
                              boolean recientesPrimero) {

    public long consultasAbiertas() {
        return consultas.stream().filter(Consulta::abierta).count();
    }
}
