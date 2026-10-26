package pe.vetsalud.erp.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.dao.jdbc.RolPermisoDaoJdbc;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.servicio.AdminUsuarioService;
import pe.vetsalud.erp.servicio.RolPermisoService;

/** P12 · Roles y permisos (RF-02): matriz rol × permiso con casillas; guardar aplica la diferencia en una transacción. */
@WebServlet("/admin/roles")
public class AdminRolesServlet extends ControladorBase {

    private RolPermisoService servicio;

    @Override
    public void init() {
        servicio = new RolPermisoService(new RolPermisoDaoJdbc(Db.get()));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        mostrar(req, res, "admin/roles", () -> {
            req.setAttribute("roles", servicio.roles());
            req.setAttribute("matriz", servicio.matriz());
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String[] marcadas = req.getParameterValues("asignacion");
        List<String> valores = marcadas == null ? List.of() : Arrays.asList(marcadas);
        procesar(req, res, "/admin/roles", "/admin/roles", null, () -> {
            exigirPermiso(req, AdminUsuarioService.PERMISO);
            int cambios = servicio.guardar(valores, usuario(req));
            exito(req, "Matriz de permisos guardada (" + cambios + " cambio(s)). Cada usuario recibe sus permisos "
                    + "nuevos en su próximo inicio de sesión.");
        });
    }
}
