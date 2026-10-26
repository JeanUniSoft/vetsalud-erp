package pe.vetsalud.erp.web;

import java.time.ZoneId;
import java.util.TimeZone;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.jsp.JspFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configuración al desplegar el WAR: zona horaria de Perú (RNF-17) y soporte de records en JSP. */
@WebListener
public class InicioAplicacion implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(InicioAplicacion.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Lima")));
        JspFactory.getDefaultFactory().getJspApplicationContext(sce.getServletContext())
                .addELResolver(new RecordELResolver());
        LOG.info("VetSalud ERP iniciado (Java {}, zona America/Lima)", Runtime.version());
    }
}
