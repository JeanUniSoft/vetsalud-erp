package pe.vetsalud.erp.log;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * Coloca en el MDC de SLF4J quién hace la solicitud y un identificador único.
 * Logback imprime esos datos en cada línea (%X{usuario}, %X{idSolicitud}), lo que permite
 * seguir una operación completa en los logs (RNF-16). Se usa con try-with-resources.
 */
public final class ContextoSolicitud implements AutoCloseable {

    private final String idSolicitud;

    private ContextoSolicitud(String usuario, String rol) {
        this.idSolicitud = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("idSolicitud", idSolicitud);
        MDC.put("usuario", usuario);
        MDC.put("rol", rol);
    }

    public static ContextoSolicitud iniciar(String usuario, String rol) {
        return new ContextoSolicitud(usuario, rol);
    }

    public String idSolicitud() {
        return idSolicitud;
    }

    @Override
    public void close() {
        MDC.remove("idSolicitud");
        MDC.remove("usuario");
        MDC.remove("rol");
    }
}
