package pe.vetsalud.erp.web;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.config.ConexionBD;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.log.ContextoSolicitud;
import pe.vetsalud.erp.modelo.FiltroReporte;
import pe.vetsalud.erp.servicio.ReporteException;
import pe.vetsalud.erp.servicio.ReporteService;

/**
 * Capa de control (Tomcat 9): descarga del reporte Excel desde la pantalla "Reportes".
 * GET /reportes/inventario.xlsx?desde=2026-09-01&hasta=2026-10-16&dias=30
 */
@WebServlet("/reportes/inventario.xlsx")
public class ReporteInventarioServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ReporteInventarioServlet.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private transient ReporteService servicio;

    @Override
    public void init() {
        servicio = new ReporteService(ConexionBD.paraReportes(), LIMA);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession sesion = req.getSession(false);
        String usuario = sesion == null ? null : (String) sesion.getAttribute("usuario");
        String rol = sesion == null ? null : (String) sesion.getAttribute("rol");
        if (usuario == null) {                       // el filtro de seguridad exige sesión (RF-01)
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try (ContextoSolicitud ctx = ContextoSolicitud.iniciar(usuario, rol)) {
            FiltroReporte filtro;
            try {
                LocalDate hoy = LocalDate.now(LIMA);
                filtro = new FiltroReporte(fecha(req, "desde", hoy.withDayOfMonth(1)), fecha(req, "hasta", hoy),
                        Integer.parseInt(valor(req, "dias", "30")));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                LOG.warn("Solicitud de reporte rechazada: {}", e.getMessage());
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
                return;
            }
            resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            resp.setHeader("Content-Disposition", "attachment; filename=\"reporte_inventario.xlsx\"");
            try {
                servicio.escribir(resp.getOutputStream(), filtro, usuario, ZonedDateTime.now(LIMA));
            } catch (ReporteException e) {
                Auditoria.registrar("EXPORTAR_REPORTE_FALLIDO", "reporte_inventario", "solicitud=" + ctx.idSolicitud());
                resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        e.getMessage() + " Código de seguimiento: " + ctx.idSolicitud());
            }
        }
    }

    private static String valor(HttpServletRequest req, String nombre, String porDefecto) {
        String v = req.getParameter(nombre);
        return v == null || v.isBlank() ? porDefecto : v.trim();
    }

    private static LocalDate fecha(HttpServletRequest req, String nombre, LocalDate porDefecto) {
        String v = req.getParameter(nombre);
        return v == null || v.isBlank() ? porDefecto : LocalDate.parse(v.trim());
    }
}
