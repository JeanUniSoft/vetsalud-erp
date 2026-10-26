package pe.vetsalud.erp.web;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.dao.jdbc.AuditoriaConsultaDaoJdbc;
import pe.vetsalud.erp.modelo.AccesoRegistro;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.servicio.AuditoriaConsultaService;
import pe.vetsalud.erp.servicio.ReglaNegocioException;

/** P12 · Bitácora de accesos (RF-01): fecha, hora, equipo (IP), usuario ingresado y resultado. Solo lectura. */
@WebServlet("/admin/accesos")
public class AdminAccesosServlet extends ControladorBase {

    private AuditoriaConsultaService servicio;

    @Override
    public void init() {
        servicio = new AuditoriaConsultaService(new AuditoriaConsultaDaoJdbc(Db.get()));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        mostrar(req, res, "admin/accesos", () -> {
            LocalDate hoy = LocalDate.now(ReportesServlet.LIMA);
            LocalDate[] periodo;
            try {
                periodo = servicio.periodo(fecha(req, "desde"), fecha(req, "hasta"), hoy);
            } catch (ReglaNegocioException | IllegalArgumentException e) {
                req.setAttribute("flash", new Flash("error", e.getMessage() + " Se muestran los últimos 7 días."));
                periodo = servicio.periodo(null, null, hoy);
            }
            req.setAttribute("desde", periodo[0]);
            req.setAttribute("hasta", periodo[1]);
            req.setAttribute("usuarioFiltro", texto(req, "usuario"));
            req.setAttribute("resultadoFiltro", texto(req, "resultado"));
            req.setAttribute("limite", AuditoriaConsultaService.LIMITE);
            List<AccesoRegistro> accesos = servicio.accesos(texto(req, "usuario"), texto(req, "resultado"),
                    periodo[0], periodo[1]);
            req.setAttribute("accesos", accesos);
            req.setAttribute("resumen", AuditoriaConsultaService.resumenAccesos(accesos));
        });
    }
}
