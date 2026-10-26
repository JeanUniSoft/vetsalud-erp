package pe.vetsalud.erp.web;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;

import pe.vetsalud.erp.dao.jdbc.CitaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.MascotaClinicaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ProfesionalDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ServicioDaoJdbc;
import pe.vetsalud.erp.modelo.AgendaFiltro;
import pe.vetsalud.erp.modelo.Cita;
import pe.vetsalud.erp.modelo.CitaSolicitud;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.CitaService;
import pe.vetsalud.erp.servicio.ConsultaService;

/**
 * P04 · Citas y llegada (RF-09 a RF-12, RF-19).
 * Rutas: /citas (agenda diaria o semanal con filtros) · /citas/nueva (programar) · /citas/ver?id= (detalle,
 * historial, reprogramar, cancelar) · POST /citas/estado (transición de estado) · POST /citas/reprogramar.
 * Toda escritura exige el permiso CITA_GESTIONAR (recepción, veterinario y administrador).
 */
@WebServlet({"/citas", "/citas/nueva", "/citas/ver", "/citas/estado", "/citas/reprogramar"})
public class CitasServlet extends ControladorBase {

    private static final String PERMISO = CitaService.PERMISO;
    /** Destino que se conoce recién al terminar la acción (p. ej. la cita creada). */
    private static final String DESTINO_DINAMICO = "#destino-dinamico";
    /** Solo se vuelve a pantallas de la agenda (evita redirecciones abiertas). */
    private static final Pattern VOLVER = Pattern.compile("^/citas(/ver)?(\\?[A-Za-z0-9_=&%.\\-]*)?$");
    private static final Pattern ID = Pattern.compile("^\\d{1,18}$");
    private static final Escaper ESCAPE = UrlEscapers.urlFormParameterEscaper();

    private CitaService servicio;

    @Override
    public void init() {
        Db db = Db.get();
        servicio = new CitaService(new CitaDaoJdbc(db), new ServicioDaoJdbc(db), new ProfesionalDaoJdbc(db),
                new MascotaClinicaDaoJdbc(db));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        switch (req.getServletPath()) {
            case "/citas/nueva" -> mostrar(req, res, "citas/nueva", () -> {
                Integer idMascota = entero(req, "mascota");
                String q = texto(req, "q");
                req.setAttribute("q", q);
                if (idMascota != null) {
                    req.setAttribute("mascota", servicio.mascota(idMascota));
                } else {
                    req.setAttribute("resultados", servicio.buscarMascotas(q));
                }
                LocalDate fecha = fecha(req, "fecha");
                LocalDate dia = fecha == null ? LocalDate.now() : fecha;
                req.setAttribute("fechaSel", fecha == null ? null : fecha.toString());
                req.setAttribute("horaSel", hora(req, "hora"));
                req.setAttribute("servicioSel", entero(req, "servicio"));
                req.setAttribute("vetSel", entero(req, "vet"));
                req.setAttribute("motivoSel", texto(req, "motivo"));
                req.setAttribute("servicios", servicio.servicios());
                req.setAttribute("veterinarios", servicio.veterinarios());
                req.setAttribute("diaOcupacion", dia);
                req.setAttribute("ocupacion", servicio.ocupacion(dia));
                req.setAttribute("hoy", LocalDate.now().toString());
                req.setAttribute("apertura", CitaService.APERTURA);
                req.setAttribute("cierre", CitaService.CIERRE);
            });
            case "/citas/ver" -> mostrar(req, res, "citas/detalle", () -> {
                long id = idObligatorio(req, res, "id");
                req.setAttribute("cita", servicio.obtener(id));
                req.setAttribute("historial", servicio.historial(id));
                req.setAttribute("veterinarios", servicio.veterinarios());
                req.setAttribute("accionesEstado", CitaService.ACCIONES);
                req.setAttribute("conMotivo", CitaService.CON_MOTIVO);
                req.setAttribute("urlActual", "/citas/ver?id=" + id);
                req.setAttribute("puedeAbrirConsulta", ConsultaService.VETERINARIO.equals(u.rol()));
            });
            case "/citas/estado", "/citas/reprogramar" -> {
                String id = idTexto(req);
                redirigir(req, res, id == null ? "/citas" : "/citas/ver?id=" + id);
            }
            default -> mostrar(req, res, "citas/agenda", () -> {
                Integer vet = req.getParameter("vet") == null ? servicio.veterinarioPorDefecto(u) : entero(req, "vet");
                AgendaFiltro filtro = new AgendaFiltro(fecha(req, "fecha"), texto(req, "vista"), vet,
                        entero(req, "servicio"), texto(req, "estado"));
                req.setAttribute("agenda", servicio.agenda(filtro));
                req.setAttribute("servicios", servicio.servicios());
                req.setAttribute("veterinarios", servicio.veterinarios());
                req.setAttribute("estados", CitaService.ESTADOS);
                req.setAttribute("accionesEstado", CitaService.ACCIONES);
                req.setAttribute("conMotivo", CitaService.CON_MOTIVO);
                req.setAttribute("hoy", LocalDate.now());
                req.setAttribute("resaltar", NumberUtils.toLong(req.getParameter("resaltar"), -1L));
                String qs = req.getQueryString();
                req.setAttribute("urlActual", "/citas" + (qs == null ? "" : "?" + qs));
                req.setAttribute("puedeAbrirConsulta", ConsultaService.VETERINARIO.equals(u.rol()));
            });
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        switch (req.getServletPath()) {
            case "/citas/nueva" -> programar(req, res, u);
            case "/citas/estado" -> {
                String id = idTexto(req);
                String volver = volver(req, id == null ? "/citas" : "/citas/ver?id=" + id);
                procesar(req, res, volver, volver, null, () -> {
                    exigirPermiso(req, PERMISO);
                    if (id == null) {
                        throw new IllegalArgumentException("Falta el identificador de la cita.");
                    }
                    Cita c = servicio.cambiarEstado(Long.parseLong(id), texto(req, "estado"), texto(req, "motivo"), u);
                    exito(req, "Cita de " + c.mascota() + " (" + FuncionesVista.hora(c.inicio()) + "): ahora está «"
                            + FuncionesVista.etiqueta(c.estado()) + "».");
                });
            }
            case "/citas/reprogramar" -> {
                String id = idTexto(req);
                String destino = id == null ? "/citas" : "/citas/ver?id=" + id;
                procesar(req, res, destino, destino, null, () -> {
                    exigirPermiso(req, PERMISO);
                    if (id == null) {
                        throw new IllegalArgumentException("Falta el identificador de la cita.");
                    }
                    Cita c = servicio.reprogramar(Long.parseLong(id), unir(fecha(req, "fecha"), hora(req, "hora")),
                            entero(req, "vet"), texto(req, "motivo"), u);
                    exito(req, "Cita reprogramada: " + c.mascota() + " · " + c.servicio() + " el "
                            + FuncionesVista.fecha(c.inicio()) + " de " + FuncionesVista.hora(c.inicio()) + " a "
                            + FuncionesVista.hora(c.fin()) + " con M.V. " + c.veterinario() + ". El horario anterior quedó libre.");
                });
            }
            default -> res.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /** RF-09: al guardar, la agenda del día confirma fecha, hora, mascota, servicio y profesional. */
    private void programar(HttpServletRequest req, HttpServletResponse res, UsuarioSesion u)
            throws IOException, ServletException {
        String volver = "/citas/nueva?" + parametros(req, "mascota", "servicio", "vet", "fecha", "hora", "motivo");
        procesar(req, res, DESTINO_DINAMICO, volver, null, () -> {
            exigirPermiso(req, PERMISO);
            CitaSolicitud s = new CitaSolicitud(entero(req, "mascota"), entero(req, "servicio"), entero(req, "vet"),
                    unir(fecha(req, "fecha"), hora(req, "hora")), texto(req, "motivo"));
            Cita c = servicio.programar(s, u);
            exito(req, "Cita N.° " + c.idCita() + " programada: " + c.mascota() + " · " + c.servicio() + " el "
                    + FuncionesVista.fecha(c.inicio()) + " de " + FuncionesVista.hora(c.inicio()) + " a "
                    + FuncionesVista.hora(c.fin()) + " con M.V. " + c.veterinario() + ".");
            req.setAttribute(DESTINO_DINAMICO, "/citas?fecha=" + c.inicio().toLocalDate() + "&vet=&resaltar=" + c.idCita());
        });
    }

    /** Usa el destino calculado por la acción (cuando lo hay) en lugar del marcador. */
    @Override
    protected void redirigir(HttpServletRequest req, HttpServletResponse res, String ruta) throws IOException {
        Object dinamico = req.getAttribute(DESTINO_DINAMICO);
        if (DESTINO_DINAMICO.equals(ruta)) {
            ruta = dinamico == null ? "/citas" : dinamico.toString();
        }
        super.redirigir(req, res, ruta);
    }

    private static LocalDateTime unir(LocalDate fecha, LocalTime hora) {
        return fecha == null || hora == null ? null : fecha.atTime(hora);
    }

    private static String idTexto(HttpServletRequest req) {
        String v = req.getParameter("id");
        return v != null && ID.matcher(v).matches() ? v : null;
    }

    private static String volver(HttpServletRequest req, String porDefecto) {
        String v = req.getParameter("volver");
        return v != null && VOLVER.matcher(v).matches() ? v : porDefecto;
    }

    /** Conserva lo digitado al volver al formulario tras un error (valores escapados para la URL). */
    private static String parametros(HttpServletRequest req, String... nombres) {
        StringBuilder sb = new StringBuilder();
        for (String n : nombres) {
            String v = StringUtils.trimToNull(req.getParameter(n));
            if (v != null) {
                sb.append(sb.isEmpty() ? "" : "&").append(n).append('=').append(ESCAPE.escape(StringUtils.left(v, 200)));
            }
        }
        return sb.toString();
    }
}
