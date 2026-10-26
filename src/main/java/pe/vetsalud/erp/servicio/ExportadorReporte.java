package pe.vetsalud.erp.servicio;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;

import pe.vetsalud.erp.modelo.ReporteTabla;

/**
 * Estrategia de exportación de un reporte (RF-36). Principio abierto/cerrado: para agregar un formato
 * (PDF, ODS...) se crea otra implementación y se registra en la raíz de composición; ni
 * {@link ReportesService} ni las vistas cambian.
 * <p>Todo archivo conserva en su cabecera el título, la fecha y hora de emisión, el usuario emisor y los
 * filtros aplicados, seguidos de los datos visibles en pantalla.</p>
 */
public interface ExportadorReporte {

    /** Código del formato en la URL (?formato=csv). */
    String formato();

    /** Tipo MIME de la respuesta HTTP. */
    String tipoContenido();

    /** Extensión del archivo descargado (sin punto). */
    String extension();

    void exportar(ReporteTabla reporte, String emitidoPor, LocalDateTime emision, OutputStream salida)
            throws IOException;
}
