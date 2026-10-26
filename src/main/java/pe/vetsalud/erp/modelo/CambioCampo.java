package pe.vetsalud.erp.modelo;

/**
 * Diferencia de un campo entre valores_antes y valores_despues (JSONB) de la auditoría (RF-04).
 *
 * @param tipo AGREGADO (solo después), ELIMINADO (solo antes) o MODIFICADO
 */
public record CambioCampo(String campo, String antes, String despues, String tipo) {
}
