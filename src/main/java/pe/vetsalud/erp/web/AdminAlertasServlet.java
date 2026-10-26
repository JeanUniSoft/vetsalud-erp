package pe.vetsalud.erp.web;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.modelo.Alerta;

/** P12 · Alertas (RF-38, vista de administración): recientes de todos los roles, pendientes y atendidas. */
@WebServlet("/admin/alertas")
public class AdminAlertasServlet extends ControladorBase {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        mostrar(req, res, "admin/alertas", () -> {
            List<Alerta> alertas = Servicios.alertas().recientes();
            req.setAttribute("alertas", alertas);
            req.setAttribute("pendientes", alertas.stream().filter(Alerta::pendiente).count());
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        procesar(req, res, "/admin/alertas", "/admin/alertas", "Alerta marcada como atendida.", () -> {
            exigirPermiso(req, "ALERTA_ATENDER");
            Servicios.alertas().atender(idObligatorio(req, res, "id"), usuario(req));
        });
    }
}
