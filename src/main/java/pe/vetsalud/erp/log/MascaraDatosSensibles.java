package pe.vetsalud.erp.log;

import java.util.regex.Pattern;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Conversor de Logback registrado como %msgSeguro en logback.xml.
 * Enmascara datos personales y secretos antes de escribir cualquier log (RNF-15, RNF-16),
 * aunque un desarrollador los incluya por descuido en el mensaje.
 */
public class MascaraDatosSensibles extends ClassicConverter {

    private static final Pattern CLAVE =
            Pattern.compile("(?i)\\b(password|clave|contrase[ñn]a|token)(\\s*[=:]\\s*)\\S+");
    private static final Pattern CORREO =
            Pattern.compile("\\b([A-Za-z0-9])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern RUC = Pattern.compile("\\b(10|20)\\d{7}(\\d{2})\\b");
    private static final Pattern CELULAR = Pattern.compile("\\b9\\d{5}(\\d{3})\\b");
    private static final Pattern DNI = Pattern.compile("\\b(\\d{2})\\d{4}(\\d{2})\\b");

    @Override
    public String convert(ILoggingEvent evento) {
        return enmascarar(evento.getFormattedMessage());
    }

    public static String enmascarar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        String r = CLAVE.matcher(texto).replaceAll("$1$2******");
        r = CORREO.matcher(r).replaceAll("$1***$2");
        r = RUC.matcher(r).replaceAll("$1*******$2");
        r = CELULAR.matcher(r).replaceAll("9*****$1");
        r = DNI.matcher(r).replaceAll("$1****$2");
        return r;
    }
}
