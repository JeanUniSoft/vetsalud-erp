package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.servicio.ReglaNegocioException;

/** P01 · Recuperación controlada: notifica al administrador (RF-03). */
@WebServlet("/recuperar")
public class RecuperarClaveServlet extends ControladorBase {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        vista(req, res, "auth/recuperar");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            Servicios.autenticacion().solicitarRecuperacion(texto(req, "dato"), req.getRemoteAddr());
            req.setAttribute("enviado", true);
        } catch (ReglaNegocioException e) {
            req.setAttribute("error", e.getMessage());
        } catch (java.sql.SQLException e) {
            log.error("No se registró la solicitud de recuperación: {}", e.getMessage(), e);
            req.setAttribute("error", "No se pudo registrar la solicitud. Intente más tarde.");
        }
        vista(req, res, "auth/recuperar");
    }
}
