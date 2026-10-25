package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import pe.vetsalud.erp.seguridad.FiltroSeguridad;
import pe.vetsalud.erp.seguridad.PoliticaAcceso;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.ReglaNegocioException;

/** P01 · Acceso: inicio de sesión (UC-01, RF-01). */
@WebServlet("/login")
public class LoginServlet extends ControladorBase {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion actual = usuario(req);
        if (actual != null) {
            redirigir(req, res, PoliticaAcceso.inicio(actual.rol()));
            return;
        }
        vista(req, res, "auth/login");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String usuario = texto(req, "usuario");
        try {
            UsuarioSesion sesion = Servicios.autenticacion().autenticar(usuario, req.getParameter("clave"),
                    req.getRemoteAddr());
            // Nuevo identificador de sesión: evita la fijación de sesión
            req.changeSessionId();
            HttpSession s = req.getSession();
            s.setAttribute(FiltroSeguridad.ATRIBUTO_USUARIO, sesion);
            s.setMaxInactiveInterval(Servicios.parametros().entero("MINUTOS_SESION", 15) * 60);
            log.info("Ingreso exitoso de {} ({})", sesion.usuario(), sesion.rol());
            redirigir(req, res, sesion.debeCambiarClave() ? "/cuenta?obligatorio=1" : PoliticaAcceso.inicio(sesion.rol()));
        } catch (ReglaNegocioException e) {
            req.setAttribute("error", e.getMessage());
            req.setAttribute("usuarioIngresado", usuario);
            vista(req, res, "auth/login");
        } catch (java.sql.SQLException e) {
            log.error("Fallo de BD en el login: {}", e.getMessage(), e);
            req.setAttribute("error", "El servicio no está disponible en este momento. Intente nuevamente.");
            vista(req, res, "auth/login");
        }
    }
}
