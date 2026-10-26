package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** Cierre de sesión (solo por POST con token CSRF, para que un enlace externo no cierre la sesión). */
@WebServlet("/logout")
public class LogoutServlet extends ControladorBase {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession sesion = req.getSession(false);
        if (sesion != null) {
            UsuarioSesion u = usuario(req);
            if (u != null) {
                Auditoria.registrar("LOGOUT", "usuario", "usuario=" + u.usuario());
            }
            sesion.invalidate();
        }
        redirigir(req, res, "/login?salida=1");
    }
}
