package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.dao.jdbc.HistoriaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.MascotaClinicaDaoJdbc;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.ConsultaService;
import pe.vetsalud.erp.servicio.HistoriaService;

/**
 * P05 · Historia clínica (RF-13): buscador de pacientes para el personal clínico y línea de tiempo de la mascota.
 * Rutas: /historia (búsqueda y consultas abiertas) · /historia/mascota?id= (historia completa).
 */
@WebServlet({"/historia", "/historia/mascota"})
public class HistoriaServlet extends ControladorBase {

    private HistoriaService servicio;

    @Override
    public void init() {
        Db db = Db.get();
        servicio = new HistoriaService(new HistoriaDaoJdbc(db), new MascotaClinicaDaoJdbc(db));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        if ("/historia/mascota".equals(req.getServletPath())) {
            mostrar(req, res, "historia/mascota", () -> {
                long id = idObligatorio(req, res, "id");
                if (id > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Identificador de mascota no válido.");
                }
                req.setAttribute("historia", servicio.historia((int) id, "desc".equals(texto(req, "orden"))));
                req.setAttribute("puedeAbrirConsulta", ConsultaService.VETERINARIO.equals(u.rol()));
                req.setAttribute("tiposEvento", HistoriaService.TIPOS);
            });
            return;
        }
        mostrar(req, res, "historia/buscar", () -> {
            String q = texto(req, "q");
            req.setAttribute("q", q);
            req.setAttribute("resultados", servicio.buscar(q));
            req.setAttribute("abiertas", servicio.consultasAbiertas(u));
        });
    }
}
