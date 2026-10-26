package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-38: alertas internas pendientes del rol y su atención. */
@WebServlet("/alertas")
public class AlertasServlet extends ControladorBase {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        mostrar(req, res, "panel/alertas", () -> req.setAttribute("alertas", Servicios.alertas().pendientes(u)));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        String volver = "panel".equals(texto(req, "volver")) ? "/panel" : "/alertas";
        procesar(req, res, volver, volver, "Alerta marcada como atendida.",
                () -> Servicios.alertas().atender(idObligatorio(req, res, "id"), u));
    }
}
