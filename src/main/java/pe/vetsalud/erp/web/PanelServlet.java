package pe.vetsalud.erp.web;

import java.io.IOException;
import java.time.LocalDate;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.dao.jdbc.PanelDaoJdbc;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.PanelService;

/** P02 · Panel principal (RF-32, RF-38). */
@WebServlet("/panel")
public class PanelServlet extends ControladorBase {

    private PanelService servicio;

    @Override
    public void init() {
        servicio = new PanelService(new PanelDaoJdbc(Db.get()));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        LocalDate hoy = LocalDate.now();
        mostrar(req, res, "panel/panel", () -> {
            req.setAttribute("hoy", hoy);
            req.setAttribute("resumen", servicio.resumen(hoy));
            req.setAttribute("agenda", servicio.agenda(hoy, u));
            req.setAttribute("ventasSemana", servicio.ventasSemana(hoy));
            req.setAttribute("alertasInventario", servicio.alertasInventario());
            req.setAttribute("alertas", Servicios.alertas().pendientes(u));
        });
    }
}
