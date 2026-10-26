package pe.vetsalud.erp;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.config.ConexionBD;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.log.ContextoSolicitud;
import pe.vetsalud.erp.modelo.FiltroReporte;
import pe.vetsalud.erp.servicio.ReporteException;
import pe.vetsalud.erp.servicio.ReporteService;

/**
 * Demostración por consola del módulo de reportes con Apache POI y Logback.
 * Ejecuta los mismos servicios que usa el servlet, con cuatro escenarios de prueba.
 */
public final class ReporteConsola {

    private static final Logger LOG = LoggerFactory.getLogger(ReporteConsola.class);

    private ReporteConsola() {
    }

    public static void main(String[] args) {
        ZoneId lima = ZoneId.of(ConexionBD.Configuracion.cargar().getProperty("app.zona", "America/Lima"));
        Path salida = Path.of(args.length > 0 ? args[0] : "salida");
        LocalDate hoy = LocalDate.now(lima);
        LOG.info("VetSalud ERP iniciado (Java {}, zona {})", Runtime.version().feature(), lima);

        ReporteService servicio = new ReporteService(ConexionBD.paraReportes(), lima);

        // 1) Caso normal: la responsable de farmacia descarga el reporte del periodo
        try (ContextoSolicitud ctx = ContextoSolicitud.iniciar("mccori", "FARMACIA")) {
            servicio.generarArchivo(new FiltroReporte(LocalDate.of(2026, 9, 1), hoy, 30), "mccori (Farmacia)", salida);
        }

        // 2) Filtro inválido: la fecha inicial es posterior a la final
        try (ContextoSolicitud ctx = ContextoSolicitud.iniciar("rparedes", "ADMINISTRADOR")) {
            try {
                servicio.generarArchivo(new FiltroReporte(hoy, LocalDate.of(2026, 9, 1), 30), "rparedes", salida);
            } catch (IllegalArgumentException e) {
                LOG.warn("Solicitud de reporte rechazada: {}", e.getMessage());
            }
        }

        // 3) Base de datos no disponible (puerto equivocado): error con traza y código de seguimiento
        ConexionBD caida = new ConexionBD("jdbc:postgresql://localhost:5499/vetsalud_erp?connectTimeout=2",
                "vetsalud_reportes", "x", true);
        try (ContextoSolicitud ctx = ContextoSolicitud.iniciar("dramos", "CAJERO")) {
            try {
                new ReporteService(caida, lima).generarArchivo(new FiltroReporte(hoy.withDayOfMonth(1), hoy, 30),
                        "dramos", salida);
            } catch (ReporteException e) {
                LOG.info("Mensaje mostrado al usuario: \"{} Código de seguimiento: {}\"", e.getMessage(),
                        ctx.idSolicitud());
            }
        }

        // 4) Protección de datos en logs: un mensaje descuidado con datos personales se enmascara
        try (ContextoSolicitud ctx = ContextoSolicitud.iniciar("ltorres", "RECEPCION")) {
            LOG.info("Cliente DNI 45678912, correo maria.gonzales@gmail.com, celular 987654321, clave=Secreta123");
            Auditoria.registrar("ACTUALIZAR", "cliente", "id=1 campo=correo nuevo=maria.gonzales@gmail.com");
        }
        LOG.info("Ejecución finalizada");
    }
}
