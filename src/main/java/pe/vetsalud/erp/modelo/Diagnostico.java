package pe.vetsalud.erp.modelo;

/** Diagnóstico de una consulta (RF-15). Solo uno es principal (índice uq_diagnostico_principal). */
public record Diagnostico(long idDiagnostico, long idConsulta, String descripcion, boolean esPrincipal) {
}
