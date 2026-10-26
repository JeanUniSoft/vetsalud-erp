package pe.vetsalud.erp.web;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.config.ConexionBD;
import pe.vetsalud.erp.dao.jdbc.ReporteDaoJdbc;
import pe.vetsalud.erp.modelo.ReporteFiltro;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.servicio.ExportadorCsv;
import pe.vetsalud.erp.servicio.ExportadorExcel;
import pe.vetsalud.erp.servicio.ParametroService;
import pe.vetsalud.erp.servicio.ReglaNegocioException;
import pe.vetsalud.erp.servicio.ReportesService;

/**
 * P10 · Reportes (RF-32 a RF-36). Solo lectura; el acceso a /reportes lo limita PoliticaAcceso al administrador.
 * Rutas: /reportes (índice con KPIs) · /reportes/ventas · /reportes/inventario · /reportes/clinica
 */
@WebServlet({"/reportes", "/reportes/ventas", "/reportes/inventario", "/reportes/clinica"})
public class ReportesServlet extends ControladorBase {

    static final ZoneId LIMA = ZoneId.of("America/Lima");

    private ReportesService servicio;

    /**
     * Raíz de composición del módulo: DAO con dos conexiones (aplicación y solo lectura de reportes) y los
     * formatos de exportación disponibles. Un formato nuevo se agrega SOLO en esta lista (abierto/cerrado).
     */
    static ReportesService crearServicio() {
        return new ReportesService(new ReporteDaoJdbc(Db.get(), new Db(ConexionBD.paraReportes())),
                List.of(new ExportadorCsv(), new ExportadorExcel()),
                () -> Servicios.parametros().entero(ParametroService.DIAS_POR_VENCER, 30), Clock.system(LIMA));
    }

    @Override
    public void init() {
        servicio = crearServicio();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String tipo = switch (req.getServletPath()) {
            case "/reportes/ventas" -> "ventas";
            case "/reportes/inventario" -> "inventario";
            case "/reportes/clinica" -> "clinica";
            default -> "indice";
        };
        mostrar(req, res, "reportes/" + tipo, () -> {
            ReporteFiltro f = filtro(req, tipo);
            req.setAttribute("filtro", f);
            req.setAttribute("kpis", servicio.kpis(tipo, f));
            req.setAttribute("formatos", servicio.formatos());
            if (tipo.equals("indice")) {
                req.setAttribute("ventasMedio", servicio.ventas(servicio.filtro("ventas", f.desde(), f.hasta(),
                        null, "medio", null, null)));
                req.setAttribute("citasProfesional", servicio.clinica(servicio.filtro("clinica", f.desde(), f.hasta(),
                        "citas", "profesional", null, null)));
            } else {
                req.setAttribute("tabla", servicio.construir(tipo, f));
                req.setAttribute("vistas", tipo.equals("inventario") ? ReportesService.VISTAS_INVENTARIO
                        : ReportesService.VISTAS_CLINICA);
                req.setAttribute("agrupaciones", tipo.equals("ventas") ? ReportesService.AGRUPACIONES_VENTAS
                        : ReportesService.AGRUPACIONES_CLINICA);
                if (tipo.equals("inventario")) {
                    req.setAttribute("formulaRotacion", ReportesService.FORMULA_ROTACION);
                    req.setAttribute("formulaCobertura", ReportesService.FORMULA_COBERTURA);
                }
            }
        });
    }

    /** Filtros de la solicitud; si son inválidos se avisa y se muestra el periodo por defecto. */
    private ReporteFiltro filtro(HttpServletRequest req, String tipo) {
        try {
            return servicio.filtro(tipo, fecha(req, "desde"), fecha(req, "hasta"), texto(req, "vista"),
                    texto(req, "agrupacion"), texto(req, "producto"), entero(req, "dias"));
        } catch (ReglaNegocioException | IllegalArgumentException e) {
            log.info("Filtro de reporte rechazado: {}", e.getMessage());
            req.setAttribute("flash", new Flash("error", e.getMessage() + " Se muestran los filtros por defecto."));
            return servicio.filtro(tipo, null, null, null, null, null, null);
        }
    }
}
