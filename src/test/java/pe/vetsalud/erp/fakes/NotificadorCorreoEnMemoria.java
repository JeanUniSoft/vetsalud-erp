package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.List;

import pe.vetsalud.erp.notificacion.NotificadorCorreo;
import pe.vetsalud.erp.notificacion.NotificadorException;

/** Doble del canal de correo: guarda los mensajes "enviados" y puede simular una caída del servidor. */
public class NotificadorCorreoEnMemoria implements NotificadorCorreo {

    public final List<Mensaje> enviados = new ArrayList<>();
    public boolean fallar;

    @Override
    public void enviar(Mensaje mensaje) throws NotificadorException {
        if (fallar) {
            throw new NotificadorException("Servidor de correo no disponible");
        }
        enviados.add(mensaje);
    }
}
