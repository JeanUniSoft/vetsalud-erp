package pe.vetsalud.erp.servicio;

/** Error controlado del módulo de reportes; el mensaje es apto para mostrarse al usuario. */
public class ReporteException extends RuntimeException {

    public ReporteException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
