package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.WordUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Cambio registrado en la bitácora de auditoría (trigger tg_aud_mascota) para una mascota.
 * Permite mostrar en la ficha que "el historial de propietario y cambios permanece disponible" (RF-08).
 *
 * @param operacion INSERT (registro) o UPDATE (modificación)
 * @param campos    campos que cambiaron (vacío en el registro inicial)
 */
public record MascotaCambio(long idAuditoria, LocalDateTime fechaHora, String usuario, String operacion,
                            List<Campo> campos) {

    public MascotaCambio {
        campos = ImmutableList.copyOf(campos);
    }

    public String operacionTexto() {
        return "INSERT".equals(operacion) ? "Registro de la mascota" : "Modificación";
    }

    /** Un campo modificado con su valor anterior y nuevo (tal como los guardó PostgreSQL en JSONB). */
    public record Campo(String nombre, String antes, String despues) {

        private static final Map<String, String> ETIQUETAS = ImmutableMap.<String, String>builder()
                .put("nombre", "Nombre").put("sexo", "Sexo").put("fecha_nacimiento", "Fecha de nacimiento")
                .put("color", "Color").put("peso_kg", "Peso (kg)").put("microchip", "Microchip")
                .put("esterilizado", "Esterilizado").put("alertas", "Alertas clínicas").put("estado", "Estado")
                .put("id_raza", "Raza").put("id_cliente", "Propietario").put("foto_ruta", "Fotografía")
                .build();

        public String etiqueta() {
            return ETIQUETAS.getOrDefault(nombre, nombre);
        }

        /** Texto legible del cambio; los identificadores internos y rutas de archivo no se muestran. */
        public String descripcion() {
            return switch (nombre) {
                case "foto_ruta" -> "Fotografía actualizada";
                case "id_cliente" -> "Cambio de propietario (ver historial de propietarios)";
                case "id_raza" -> "Raza modificada";
                case "esterilizado" -> "Esterilizado: " + siNo(antes) + " → " + siNo(despues);
                default -> etiqueta() + ": " + valor(antes) + " → " + valor(despues);
            };
        }

        private String valor(String v) {
            if (v == null || v.isBlank()) {
                return "(vacío)";
            }
            return switch (nombre) {
                case "estado" -> WordUtils.capitalizeFully(v);
                case "sexo" -> "M".equals(v) ? "Macho" : "H".equals(v) ? "Hembra" : v;
                case "fecha_nacimiento" -> v.length() == 10 ? v.substring(8) + "/" + v.substring(5, 7) + "/" + v.substring(0, 4) : v;
                default -> v;
            };
        }

        private static String siNo(String v) {
            return "true".equals(v) ? "sí" : "no";
        }
    }
}
