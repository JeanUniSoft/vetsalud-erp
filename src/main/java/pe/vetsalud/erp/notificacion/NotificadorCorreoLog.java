package pe.vetsalud.erp.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.modelo.Recordatorio;

/**
 * Implementación de laboratorio de {@link NotificadorCorreo}: no hay servidor SMTP, así que el "envío" se
 * registra en el logger NOTIFICACIONES de Logback con el destinatario enmascarado (RNF-15) y el texto genérico
 * del mensaje (sin datos clínicos). En producción se reemplaza por una implementación SMTP (OCP).
 */
public class NotificadorCorreoLog implements NotificadorCorreo {

    private static final Logger LOG = LoggerFactory.getLogger("NOTIFICACIONES");

    @Override
    public void enviar(Mensaje mensaje) {
        LOG.info("CORREO enviado | para={} | asunto=\"{}\" | texto=\"{}\"",
                Recordatorio.enmascararCorreo(mensaje.destinatario()), mensaje.asunto(), mensaje.texto());
    }
}
