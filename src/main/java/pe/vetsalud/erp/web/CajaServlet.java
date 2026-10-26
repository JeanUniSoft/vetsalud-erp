package pe.vetsalud.erp.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import pe.vetsalud.erp.dao.jdbc.CajaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ProductoDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.UsuarioDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.VentaPosDaoJdbc;
import pe.vetsalud.erp.modelo.TurnoCaja;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.seguridad.ClaveHasher;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.AutorizacionDescuento;
import pe.vetsalud.erp.servicio.CajaService;
import pe.vetsalud.erp.servicio.VentaService;

/**
 * P09 · Cierre de caja (RF-30): estado del turno, apertura, cierre conciliado e historial.
 * Rutas: /caja · /caja/historial · /caja/turno?id=
 */
@WebServlet({"/caja", "/caja/historial", "/caja/turno"})
public class CajaServlet extends ControladorBase {

    private CajaService caja;
    private VentaService ventas;

    @Override
    public void init() {
        Db db = Db.get();
        CajaDaoJdbc cajaDao = new CajaDaoJdbc(db);
        caja = new CajaService(cajaDao);
        ventas = new VentaService(new VentaPosDaoJdbc(db), cajaDao, new ProductoDaoJdbc(db),
                new AutorizacionDescuento(new UsuarioDaoJdbc(db), new ClaveHasher()), Servicios.parametros(),
                Clock.systemDefaultZone());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        switch (req.getServletPath()) {
            case "/caja/historial" -> mostrar(req, res, "caja/historial", () -> {
                boolean sinFiltros = texto(req, "desde") == null && texto(req, "hasta") == null;
                LocalDate desde = sinFiltros ? LocalDate.now().minusDays(30) : fecha(req, "desde");
                LocalDate hasta = fecha(req, "hasta");
                req.setAttribute("desde", desde);
                req.setAttribute("hasta", hasta);
                req.setAttribute("turnos", caja.historial(desde, hasta, u));
            });
            case "/caja/turno" -> mostrar(req, res, "caja/turno", () -> {
                int id = (int) idObligatorio(req, res, "id");
                req.setAttribute("resumen", caja.resumen(id, u));
                req.setAttribute("ventas", ventas.ventasDeTurno(id));
            });
            default -> mostrar(req, res, "caja/caja", () -> {
                Optional<TurnoCaja> turno = caja.turnoAbierto(u);
                req.setAttribute("turno", turno.orElse(null));
                if (turno.isPresent()) {
                    req.setAttribute("resumen", caja.resumen(turno.get().idTurno(), u));
                    req.setAttribute("ventas", ventas.ventasDeTurno(turno.get().idTurno()));
                }
                req.setAttribute("abiertos", caja.abiertos(u));
            });
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        if (!"/caja".equals(req.getServletPath())) {
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        if ("cerrar".equals(texto(req, "accion"))) {
            String turno = StringUtils.defaultString(texto(req, "turno"));
            String volver = StringUtils.isNumeric(turno) && !turno.isEmpty() && !esPropio(req, turno)
                    ? "/caja/turno?id=" + turno : "/caja";
            String destino = StringUtils.isNumeric(turno) && !turno.isEmpty() ? "/caja/turno?id=" + turno : "/caja";
            procesar(req, res, destino, volver, null, () -> {
                exigirPermiso(req, CajaService.PERMISO);
                BigDecimal diferencia = caja.cerrar((int) idObligatorio(req, res, "turno"), decimal(req, "contado"),
                        texto(req, "observacion"), u);
                exito(req, "Turno cerrado. " + (diferencia.signum() == 0 ? "El efectivo cuadra con lo esperado."
                        : (diferencia.signum() > 0 ? "Sobrante" : "Faltante") + " de S/ "
                        + diferencia.abs().setScale(2, RoundingMode.HALF_UP).toPlainString() + " registrado con su observación."));
            });
        } else {
            procesar(req, res, "/caja", "/caja", "Turno de caja abierto. Ya puede registrar ventas.", () -> {
                exigirPermiso(req, CajaService.PERMISO);
                caja.abrir(decimal(req, "monto"), u);
            });
        }
    }

    /** El formulario de cierre del propio turno está en /caja; el de otro cajero (administrador), en su detalle. */
    private boolean esPropio(HttpServletRequest req, String turno) {
        try {
            return caja.turnoAbierto(usuario(req)).map(t -> String.valueOf(t.idTurno()).equals(turno)).orElse(false);
        } catch (java.sql.SQLException e) {
            return true;
        }
    }
}
