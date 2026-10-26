package pe.vetsalud.erp.web;

import java.io.IOException;
import java.time.LocalDate;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.dao.jdbc.AuditoriaConsultaDaoJdbc;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.servicio.AuditoriaConsultaService;
import pe.vetsalud.erp.servicio.ReglaNegocioException;

/**
 * P12 · Auditoría (RF-04): consulta de la tabla auditoria con filtros y detalle de campos modificados.
 * Solo lectura (RNF-06): no hay doPost; cualquier POST recibe 405.
 */
@WebServlet({"/admin/auditoria", "/admin/auditoria/detalle"})
public class AdminAuditoriaServlet extends ControladorBase {

    private AuditoriaConsultaService servicio;

    @Override
    public void init() {
        servicio = new AuditoriaConsultaService(new AuditoriaConsultaDaoJdbc(Db.get()));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        if ("/admin/auditoria/detalle".equals(req.getServletPath())) {
            mostrar(req, res, "admin/auditoria-detalle", () -> {
                long id = idObligatorio(req, res, "id");
                req.setAttribute("registro", servicio.obtener(id));
                req.setAttribute("cambios", servicio.cambios(id));
            });
            return;
        }
        mostrar(req, res, "admin/auditoria", () -> {
            LocalDate[] periodo = periodo(req);
            req.setAttribute("desde", periodo[0]);
            req.setAttribute("hasta", periodo[1]);
            req.setAttribute("tablaFiltro", texto(req, "tabla"));
            req.setAttribute("operacionFiltro", texto(req, "operacion"));
            req.setAttribute("usuarioFiltro", entero(req, "usuario"));
            req.setAttribute("tablas", servicio.tablas());
            req.setAttribute("usuarios", servicio.usuarios());
            req.setAttribute("operaciones", AuditoriaConsultaService.OPERACIONES);
            req.setAttribute("limite", AuditoriaConsultaService.LIMITE);
            req.setAttribute("registros", servicio.buscar(texto(req, "tabla"), texto(req, "operacion"),
                    entero(req, "usuario"), periodo[0], periodo[1]));
        });
    }

    private LocalDate[] periodo(HttpServletRequest req) {
        try {
            return servicio.periodo(fecha(req, "desde"), fecha(req, "hasta"), LocalDate.now(ReportesServlet.LIMA));
        } catch (ReglaNegocioException | IllegalArgumentException e) {
            req.setAttribute("flash", new Flash("error", e.getMessage() + " Se muestran los últimos 7 días."));
            return servicio.periodo(null, null, LocalDate.now(ReportesServlet.LIMA));
        }
    }
}
