package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.servicio.AdminParametroService;
import pe.vetsalud.erp.servicio.AdminUsuarioService;

/** P12 · Parámetros del sistema (RF-24 configurable, RNF-05) con validación de rangos. */
@WebServlet("/admin/parametros")
public class AdminParametrosServlet extends ControladorBase {

    private AdminParametroService servicio;

    @Override
    public void init() {
        // Usa el ParametroService compartido: al guardar se invalida la misma caché que leen login y alertas
        servicio = new AdminParametroService(Servicios.parametros());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        mostrar(req, res, "admin/parametros", () -> {
            req.setAttribute("parametros", servicio.todos());
            req.setAttribute("rangos", AdminParametroService.RANGOS);
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String clave = texto(req, "clave");
        procesar(req, res, "/admin/parametros", "/admin/parametros", "Parámetro " + clave + " actualizado.", () -> {
            exigirPermiso(req, AdminUsuarioService.PERMISO);
            servicio.actualizar(clave, texto(req, "valor"), usuario(req));
        });
    }
}
