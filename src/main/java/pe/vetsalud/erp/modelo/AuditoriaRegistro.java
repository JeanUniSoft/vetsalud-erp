package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/**
 * Registro de la tabla auditoria (RF-04, RNF-06), escrito por el trigger fn_auditar. Solo lectura.
 *
 * @param estadoNuevo valor nuevo del campo «estado» si cambió (sirve para reconocer anulaciones, firmas y cierres)
 * @param cambios     cantidad de campos cuyo valor cambió
 */
public record AuditoriaRegistro(long idAuditoria, LocalDateTime fechaHora, Integer idUsuario, String usuario,
                                String tabla, long idRegistro, String operacion, String estadoNuevo, int cambios) {

    /** Evento de negocio legible: alta, modificación, anulación, firma, cierre, bloqueo... */
    public String evento() {
        if ("INSERT".equals(operacion)) {
            return "Alta";
        }
        if ("DELETE".equals(operacion)) {
            return "Eliminación";
        }
        if (estadoNuevo != null) {
            return switch (estadoNuevo) {
                case "ANULADA" -> "Anulación";
                case "FIRMADA" -> "Firma";
                case "CERRADA" -> "Cierre";
                case "CONFIRMADA" -> "Confirmación";
                default -> "Cambio de estado";
            };
        }
        return "Modificación";
    }
}
