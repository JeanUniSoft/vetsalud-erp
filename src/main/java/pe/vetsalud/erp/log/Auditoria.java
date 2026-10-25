package pe.vetsalud.erp.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registro de auditoría (RF-04): usa el logger "AUDITORIA", que Logback envía a auditoria.log
 * con retención de 24 meses (RNF-06). Usuario, rol y solicitud se toman del MDC.
 */
public final class Auditoria {

    private static final Logger AUD = LoggerFactory.getLogger("AUDITORIA");

    private Auditoria() {
    }

    public static void registrar(String operacion, String entidad, String detalle) {
        AUD.info("operacion={} entidad={} {}", operacion, entidad, detalle);
    }
}
