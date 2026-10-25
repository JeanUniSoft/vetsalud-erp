package pe.vetsalud.erp.servicio;

/**
 * Violación de una regla de negocio detectada en la capa de servicio.
 * El controlador muestra el mensaje tal cual al usuario (debe estar redactado para él).
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
