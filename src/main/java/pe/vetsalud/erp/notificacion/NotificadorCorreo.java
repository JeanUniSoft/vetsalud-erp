package pe.vetsalud.erp.notificacion;

import com.google.common.base.Preconditions;

/**
 * Canal de salida de correos (RF-37). Principios O y D de SOLID: el servicio de recordatorios depende de esta
 * interfaz; hoy la implementación registra el envío en Logback ({@link NotificadorCorreoLog}) y mañana puede
 * ser SMTP o un proveedor externo sin modificar el servicio.
 */
public interface NotificadorCorreo {

    /** Envía el mensaje; lanza {@link NotificadorException} si el canal lo rechaza. */
    void enviar(Mensaje mensaje) throws NotificadorException;

    /** Correo a enviar: solo texto genérico, nunca diagnóstico, anamnesis ni receta. */
    record Mensaje(String destinatario, String asunto, String texto) {
        public Mensaje {
            Preconditions.checkArgument(destinatario != null && !destinatario.isBlank(), "Falta el destinatario");
            Preconditions.checkNotNull(asunto, "asunto");
            Preconditions.checkNotNull(texto, "texto");
        }
    }
}
