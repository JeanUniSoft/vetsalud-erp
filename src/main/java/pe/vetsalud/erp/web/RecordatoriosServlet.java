package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.RecordatorioService;
import pe.vetsalud.erp.servicio.ReglaNegocioException;

/**
 * P12 · Recordatorios por correo (RF-37). Solo ADMINISTRADOR (prefijo /admin en PoliticaAcceso).
 * GET lista la cola con filtro por estado; POST accion=procesar (cola vencida o toda la cola) o accion=programar
 * (crea los recordatorios que falten para próximas citas y controles).
 */
@WebServlet("/admin/recordatorios")
public class RecordatoriosServlet extends ControladorBase {

    private static final String RUTA = "/admin/recordatorios";

    private RecordatorioService servicio;

    @Override
    public void init() {
        servicio = MascotaModulo.recordatorios();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        mostrar(req, res, "admin/recordatorios", () -> {
            String estado = texto(req, "estado");
            req.setAttribute("estado", estado);
            req.setAttribute("minutosAuto", RecordatorioPlanificador.minutos());
            req.setAttribute("resumen", servicio.resumen());
            req.setAttribute("recordatorios", servicio.listar(estado));
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        String accion = texto(req, "accion");
        procesar(req, res, RUTA, RUTA, null, () -> {
            if ("procesar".equals(accion)) {
                RecordatorioService.Resultado r = servicio.procesarPendientes(marcado(req, "incluirFuturos"),
                        u.idUsuario(), u.usuario());
                exito(req, r.mensaje());
            } else if ("programar".equals(accion)) {
                exito(req, servicio.programar(u.idUsuario(), u.usuario()).mensaje());
            } else {
                throw new ReglaNegocioException("Acción no reconocida.");
            }
        });
    }
}
