package pe.vetsalud.erp.web;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.persistencia.ErroresBD;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.PortalService;

/**
 * P11 · Portal del cliente (rol CLIENTE). Rutas: /portal · /portal/mascota?id=
 * El cliente se toma SIEMPRE de la sesión (usuario(req).idCliente()); nunca se acepta un id de cliente por
 * parámetro. Una mascota ajena responde 403 y el intento queda en el log.
 */
@WebServlet({"/portal", "/portal/mascota"})
public class PortalServlet extends ControladorBase {

    private PortalService servicio;

    @Override
    public void init() {
        servicio = MascotaModulo.portal();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        Integer idCliente = u.idCliente();
        if ("/portal/mascota".equals(req.getServletPath())) {
            Integer id = MascotaModulo.idValido(texto(req, "id"));
            Optional<Mascota> mascota;
            try {
                mascota = id == null ? Optional.empty() : servicio.mascota(idCliente, id);
            } catch (SQLException e) {
                log.error("No se pudo cargar la mascota del portal: {}", e.getMessage(), e);
                req.setAttribute("mensajeError", ErroresBD.mensaje(e));
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                vista(req, res, "error/error");
                return;
            }
            if (mascota.isEmpty()) {
                log.warn("Acceso denegado: el cliente {} pidió la mascota {} que no le pertenece", idCliente, id);
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            Mascota m = mascota.get();
            mostrar(req, res, "portal/mascota", () -> {
                req.setAttribute("mascota", m);
                req.setAttribute("citas", servicio.proximasCitas(idCliente, m.idMascota()));
                req.setAttribute("controles", servicio.controles(idCliente, m.idMascota()));
            });
            return;
        }
        mostrar(req, res, "portal/inicio", () -> {
            req.setAttribute("mascotas", servicio.mascotas(idCliente));
            req.setAttribute("citas", servicio.proximasCitas(idCliente, null));
            req.setAttribute("controles", servicio.controles(idCliente, null));
            req.setAttribute("recordatorios", servicio.recordatorios(idCliente));
        });
    }
}
