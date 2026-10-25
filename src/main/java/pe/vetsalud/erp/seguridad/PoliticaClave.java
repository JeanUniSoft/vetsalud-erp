package pe.vetsalud.erp.seguridad;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Política de contraseñas (RF-03): mínimo 8 caracteres con mayúscula, minúscula, dígito y símbolo,
 * y distinta del nombre de usuario.
 */
public final class PoliticaClave {

    public static final int LONGITUD_MINIMA = 8;

    private PoliticaClave() {
    }

    /** Devuelve los incumplimientos; lista vacía = la clave es válida. */
    public static List<String> incumplimientos(String clave, String usuario) {
        List<String> faltas = new ArrayList<>();
        if (StringUtils.length(clave) < LONGITUD_MINIMA) {
            faltas.add("tener al menos " + LONGITUD_MINIMA + " caracteres");
        }
        if (!StringUtils.containsAny(clave, "ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÑ")) {
            faltas.add("incluir una letra mayúscula");
        }
        if (!StringUtils.containsAny(clave, "abcdefghijklmnopqrstuvwxyzáéíóúñ")) {
            faltas.add("incluir una letra minúscula");
        }
        if (!StringUtils.containsAny(clave, "0123456789")) {
            faltas.add("incluir un número");
        }
        if (clave == null || clave.chars().allMatch(Character::isLetterOrDigit)) {
            faltas.add("incluir un símbolo (por ejemplo ! # $ % *)");
        }
        if (usuario != null && StringUtils.containsIgnoreCase(clave, usuario)) {
            faltas.add("no contener el nombre de usuario");
        }
        return faltas;
    }
}
