package pe.vetsalud.erp.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.time.ZonedDateTime;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import pe.vetsalud.erp.config.ConexionBD;
import pe.vetsalud.erp.modelo.FiltroReporte;
import pe.vetsalud.erp.modelo.ReporteFiltro;
import pe.vetsalud.erp.modelo.ReporteTabla;
import pe.vetsalud.erp.persistencia.ErroresBD;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.ExportadorReporte;
import pe.vetsalud.erp.servicio.ReglaNegocioException;
import pe.vetsalud.erp.servicio.ReporteException;
import pe.vetsalud.erp.servicio.ReporteService;
import pe.vetsalud.erp.servicio.ReportesService;

/**
 * RF-36 · Descarga de reportes: GET /reportes/exportar?tipo=ventas|inventario|clinica&formato=csv|xlsx&...filtros
 * Exige el permiso REPORTE_EXPORTAR. El archivo se arma en memoria antes de enviarlo: si algo falla, el
 * usuario vuelve al reporte con un mensaje claro (nunca recibe un archivo a medias).
 * tipo=s10 entrega el libro Excel de inventario y ventas del S10 (ReporteService, reutilizado sin cambios).
 */
@WebServlet("/reportes/exportar")
public class ExportarReporteServlet extends ControladorBase {

    private ReportesService servicio;
    private ReporteService reporteS10;

    @Override
    public void init() {
        servicio = ReportesServlet.crearServicio();
        reporteS10 = new ReporteService(ConexionBD.paraReportes(), ReportesServlet.LIMA);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        String tipo = StringUtils.defaultString(texto(req, "tipo"));
        String volver = (ReportesService.TIPOS.contains(tipo) ? "/reportes/" + tipo : "/reportes")
                + (req.getQueryString() == null ? "" : "?" + req.getQueryString());
        try {
            exigirPermiso(req, "REPORTE_EXPORTAR");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            String nombre;
            String contenido;
            if (tipo.equals("s10")) {
                ReporteFiltro f = servicio.filtro("inventario", fecha(req, "desde"), fecha(req, "hasta"), null, null,
                        null, entero(req, "dias"));
                reporteS10.escribir(buffer, new FiltroReporte(f.desde(), f.hasta(), f.dias()), u.usuario(),
                        ZonedDateTime.now(ReportesServlet.LIMA));
                nombre = "reporte_inventario_ventas_S10.xlsx";
                contenido = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else {
                ReporteFiltro f = servicio.filtro(tipo, fecha(req, "desde"), fecha(req, "hasta"), texto(req, "vista"),
                        texto(req, "agrupacion"), texto(req, "producto"), entero(req, "dias"));
                ExportadorReporte e = servicio.exportador(texto(req, "formato"));
                ReporteTabla t = servicio.construir(tipo, f);
                servicio.exportar(t, f, e, u.usuario() + " (" + u.nombreCompleto() + ")", u.usuario(), buffer);
                nombre = servicio.nombreArchivo(t, f, e);
                contenido = e.tipoContenido();
            }
            res.setContentType(contenido);
            res.setHeader("Content-Disposition", "attachment; filename=\"" + nombre + "\"");
            res.setContentLength(buffer.size());
            buffer.writeTo(res.getOutputStream());
            log.info("Reporte exportado: {} ({} bytes)", nombre, buffer.size());
        } catch (ReglaNegocioException | IllegalArgumentException | ReporteException e) {
            log.info("Exportación rechazada: {}", e.getMessage());
            error(req, e.getMessage());
            redirigir(req, res, volver);
        } catch (SQLException e) {
            log.warn("Error de base de datos al exportar ({}): {}", e.getSQLState(), e.getMessage(), e);
            error(req, ErroresBD.mensaje(e));
            redirigir(req, res, volver);
        }
    }
}
