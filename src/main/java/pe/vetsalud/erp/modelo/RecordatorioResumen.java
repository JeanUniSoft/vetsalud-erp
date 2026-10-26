package pe.vetsalud.erp.modelo;

/** Conteo de la cola de recordatorios para el panel de administración (RF-37). */
public record RecordatorioResumen(int pendientesVencidos, int pendientesFuturos, int enviados, int fallidos,
                                  int cancelados) {

    public int pendientes() {
        return pendientesVencidos + pendientesFuturos;
    }
}
