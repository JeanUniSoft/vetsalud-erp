package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.seguridad.FiltroSeguridad;
import pe.vetsalud.erp.seguridad.PoliticaAcceso;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** P01 · Mi cuenta: cambio de contraseña con política (RF-03). */
@WebServlet("/cuenta")
public class CuentaServlet extends ControladorBase {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        req.setAttribute("titulo", "Mi cuenta");
        vista(req, res, "auth/cuenta");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        procesar(req, res, PoliticaAcceso.inicio(u.rol()), "/cuenta", "Contraseña actualizada. Úsela en su próximo ingreso.",
                () -> {
                    Servicios.autenticacion().cambiarClave(u, req.getParameter("actual"), req.getParameter("nueva"),
                            req.getParameter("confirmacion"));
                    req.getSession().setAttribute(FiltroSeguridad.ATRIBUTO_USUARIO, u.conClaveCambiada());
                });
    }
}
