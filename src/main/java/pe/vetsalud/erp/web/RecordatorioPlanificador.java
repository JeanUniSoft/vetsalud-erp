package pe.vetsalud.erp.web;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.log.ContextoSolicitud;
import pe.vetsalud.erp.servicio.RecordatorioService;

/**
 * RF-37 · Envío automático de recordatorios: cada N minutos programa los que falten y procesa la cola vencida.
 * Intervalo: propiedad del sistema {@code vetsalud.recordatorios.minutos} (15 por defecto; 0 lo desactiva).
 * Si dos servidores corren a la vez no hay envíos dobles: el servicio "reclama" cada recordatorio con una
 * actualización condicional antes de enviarlo.
 */
@WebListener
public class RecordatorioPlanificador implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(RecordatorioPlanificador.class);
    private static final String USUARIO_SISTEMA = "sistema";

    private ScheduledExecutorService ejecutor;

    /** Intervalo configurado en minutos (0 o menos = desactivado). */
    static long minutos() {
        return Long.getLong("vetsalud.recordatorios.minutos", 15L);
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        long minutos = minutos();
        if (minutos <= 0) {
            LOG.info("Envío automático de recordatorios desactivado (vetsalud.recordatorios.minutos={})", minutos);
            return;
        }
        ejecutor = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "vetsalud-recordatorios");
            hilo.setDaemon(true);
            return hilo;
        });
        ejecutor.scheduleWithFixedDelay(this::ejecutar, Math.min(2, minutos), minutos, TimeUnit.MINUTES);
        LOG.info("Envío automático de recordatorios cada {} minuto(s)", minutos);
    }

    private void ejecutar() {
        try (ContextoSolicitud ctx = ContextoSolicitud.iniciar(USUARIO_SISTEMA, "PLANIFICADOR")) {
            RecordatorioService servicio = MascotaModulo.recordatorios();
            RecordatorioService.Programacion p = servicio.programar(null, USUARIO_SISTEMA);
            RecordatorioService.Resultado r = servicio.procesarPendientes(false, null, USUARIO_SISTEMA);
            if (p.citas() + p.controles() + r.procesados() > 0) {
                LOG.info("Planificador de recordatorios: {} {}", p.mensaje(), r.mensaje());
            } else {
                LOG.debug("Planificador de recordatorios: sin novedades");
            }
        } catch (Exception e) {
            // Nunca se deja escapar la excepción: detendría las siguientes ejecuciones programadas
            LOG.error("Falló la ejecución automática de recordatorios: {}", e.getMessage(), e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (ejecutor != null) {
            ejecutor.shutdownNow();
        }
    }
}
