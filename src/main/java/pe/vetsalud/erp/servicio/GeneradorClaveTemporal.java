package pe.vetsalud.erp.servicio;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.seguridad.PoliticaClave;

/**
 * Genera claves temporales aleatorias para el restablecimiento controlado (RF-03).
 * <ul>
 *   <li>{@link SecureRandom}: no predecible (a diferencia de {@code java.util.Random}).</li>
 *   <li>12 caracteres con al menos una mayúscula, una minúscula, un dígito y un símbolo.</li>
 *   <li>Sin caracteres ambiguos (0/O, 1/l/I) para dictarla sin errores.</li>
 *   <li>Siempre cumple {@link PoliticaClave} (se verifica antes de devolverla).</li>
 * </ul>
 */
public class GeneradorClaveTemporal {

    public static final int LONGITUD = 12;

    static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String MINUSCULAS = "abcdefghijkmnpqrstuvwxyz";
    static final String DIGITOS = "23456789";
    static final String SIMBOLOS = "!#$%*+?@";
    private static final String TODOS = MAYUSCULAS + MINUSCULAS + DIGITOS + SIMBOLOS;

    private final Random aleatorio;

    public GeneradorClaveTemporal() {
        this(new SecureRandom());
    }

    /** Constructor para pruebas (permite una semilla fija). */
    GeneradorClaveTemporal(Random aleatorio) {
        this.aleatorio = Preconditions.checkNotNull(aleatorio);
    }

    /** Clave que cumple la política y no contiene el nombre de usuario. */
    public String generar(String usuario) {
        for (int intento = 0; intento < 20; intento++) {
            String clave = candidata();
            if (PoliticaClave.incumplimientos(clave, usuario).isEmpty()) {
                return clave;
            }
        }
        throw new IllegalStateException("No se pudo generar una clave temporal válida");
    }

    private String candidata() {
        List<Character> c = new ArrayList<>(LONGITUD);
        c.add(elegir(MAYUSCULAS));
        c.add(elegir(MINUSCULAS));
        c.add(elegir(DIGITOS));
        c.add(elegir(SIMBOLOS));
        while (c.size() < LONGITUD) {
            c.add(elegir(TODOS));
        }
        Collections.shuffle(c, aleatorio);
        StringBuilder sb = new StringBuilder(LONGITUD);
        c.forEach(sb::append);
        return sb.toString();
    }

    private char elegir(String alfabeto) {
        return alfabeto.charAt(aleatorio.nextInt(alfabeto.length()));
    }
}
