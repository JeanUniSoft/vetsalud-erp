package pe.vetsalud.erp.servicio;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Validaciones reutilizables de datos de entrada (defensa en profundidad: la BD vuelve a validar).
 * Lanzan ReglaNegocioException con un mensaje listo para el usuario.
 */
public final class Validaciones {

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?[0-9]{6,14}$");

    private Validaciones() {
    }

    public static String requerido(String valor, String campo) {
        if (StringUtils.isBlank(valor)) {
            throw new ReglaNegocioException("El campo «" + campo + "» es obligatorio.");
        }
        return valor.trim();
    }

    public static void longitudMaxima(String valor, int maximo, String campo) {
        if (StringUtils.length(valor) > maximo) {
            throw new ReglaNegocioException("El campo «" + campo + "» admite como máximo " + maximo + " caracteres.");
        }
    }

    public static void correo(String valor) {
        if (valor != null && !CORREO.matcher(valor).matches()) {
            throw new ReglaNegocioException("El correo no tiene un formato válido.");
        }
    }

    public static void telefono(String valor) {
        if (valor != null && !TELEFONO.matcher(valor).matches()) {
            throw new ReglaNegocioException("El teléfono debe tener entre 6 y 14 dígitos (puede iniciar con +).");
        }
    }

    /** Reglas del documento según su tipo (las mismas CHECK de la tabla cliente). */
    public static void documento(String tipo, String numero) {
        switch (StringUtils.defaultString(tipo)) {
            case "DNI" -> {
                if (!numero.matches("^[0-9]{8}$")) {
                    throw new ReglaNegocioException("El DNI debe tener 8 dígitos.");
                }
            }
            case "RUC" -> {
                if (!numero.matches("^(10|20)[0-9]{9}$")) {
                    throw new ReglaNegocioException("El RUC debe tener 11 dígitos y empezar con 10 o 20.");
                }
            }
            case "CE", "PASAPORTE" -> {
                if (!numero.matches("^[A-Za-z0-9]{6,12}$")) {
                    throw new ReglaNegocioException("El documento debe tener entre 6 y 12 letras o números.");
                }
            }
            default -> throw new ReglaNegocioException("Seleccione un tipo de documento válido.");
        }
    }
}
