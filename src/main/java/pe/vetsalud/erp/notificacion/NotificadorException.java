package pe.vetsalud.erp.notificacion;

/** El canal de notificación no pudo entregar el mensaje (servidor caído, dirección rechazada...). */
public class NotificadorException extends Exception {

    public NotificadorException(String mensaje) {
        super(mensaje);
    }

    public NotificadorException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
