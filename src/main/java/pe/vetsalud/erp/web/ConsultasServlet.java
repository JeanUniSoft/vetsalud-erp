package pe.vetsalud.erp.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.dao.jdbc.CitaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ConsultaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.MascotaClinicaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ProfesionalDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ServicioDaoJdbc;
import pe.vetsalud.erp.modelo.Cita;
import pe.vetsalud.erp.modelo.Consulta;
import pe.vetsalud.erp.modelo.ConsultaDatos;
import pe.vetsalud.erp.modelo.ConsultaFirma;
import pe.vetsalud.erp.modelo.ConsultaPermisos;
import pe.vetsalud.erp.modelo.ConsultaSignos;
import pe.vetsalud.erp.modelo.Diagnostico;
import pe.vetsalud.erp.modelo.ProcedimientoConsulta;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.ConsultaService;

/**
 * P05 / P06 · Consulta clínica, procedimientos y receta (RF-14 a RF-19).
 * GET: /consultas/nueva?cita= | ?mascota= · /consultas/ver?id= · /consultas/receta?id= (vista imprimible).
 * POST: /consultas/nueva · /datos · /signos · /diagnostico · /procedimiento · /receta · /medicamento · /firmar · /adenda.
 * Los permisos por rol los decide {@link ConsultaService}; la firma exige además el permiso CONSULTA_FIRMAR.
 */
@WebServlet({"/consultas", "/consultas/nueva", "/consultas/ver", "/consultas/receta", "/consultas/datos",
        "/consultas/signos", "/consultas/diagnostico", "/consultas/procedimiento", "/consultas/medicamento",
        "/consultas/firmar", "/consultas/adenda"})
public class ConsultasServlet extends ControladorBase {

    private static final String DESTINO_DINAMICO = "#destino-dinamico";
    private static final Pattern ID = Pattern.compile("^\\d{1,18}$");

    private ConsultaService servicio;

    @Override
    public void init() {
        Db db = Db.get();
        servicio = new ConsultaService(new ConsultaDaoJdbc(db), new CitaDaoJdbc(db), new MascotaClinicaDaoJdbc(db),
                new ServicioDaoJdbc(db), new ProfesionalDaoJdbc(db));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        switch (req.getServletPath()) {
            case "/consultas/nueva" -> mostrar(req, res, "consultas/nueva", () -> {
                boolean puede = servicio.puedeAbrir(u);
                req.setAttribute("puedeAbrir", puede);
                Long idCita = largo(req, "cita");
                Integer idMascota = entero(req, "mascota");
                if (idCita != null) {
                    Cita c = servicio.cita(idCita);
                    req.setAttribute("cita", c);
                    req.setAttribute("mascota", servicio.mascota(c.idMascota()));
                    servicio.consultaDeCita(idCita).ifPresent(x -> req.setAttribute("consultaExistente", x));
                } else if (idMascota != null) {
                    req.setAttribute("mascota", servicio.mascota(idMascota));
                }
                if (puede) {
                    req.setAttribute("responsable", servicio.profesional(u.idUsuario()));
                }
            });
            case "/consultas/ver" -> mostrar(req, res, "consultas/detalle", () -> {
                long id = idObligatorio(req, res, "id");
                Consulta c = servicio.obtener(id);
                ConsultaPermisos permisos = servicio.permisos(c, u);
                req.setAttribute("consulta", c);
                req.setAttribute("permisos", permisos);
                req.setAttribute("mascota", servicio.mascota(c.idMascota()));
                List<Diagnostico> diagnosticos = servicio.diagnosticos(id);
                req.setAttribute("diagnosticos", diagnosticos);
                req.setAttribute("tienePrincipal", diagnosticos.stream().anyMatch(Diagnostico::esPrincipal));
                if (c.fechaControl() != null) {
                    req.setAttribute("recordatorioPara", ConsultaService.recordatorioPara(c.fechaControl()));
                }
                List<ProcedimientoConsulta> procedimientos = servicio.procedimientos(id);
                req.setAttribute("procedimientos", procedimientos);
                req.setAttribute("totalProcedimientos", procedimientos.stream()
                        .map(ProcedimientoConsulta::importe).reduce(BigDecimal.ZERO, BigDecimal::add));
                req.setAttribute("receta", servicio.receta(id).orElse(null));
                req.setAttribute("adendas", servicio.adendas(id));
                req.setAttribute("vias", ConsultaService.VIAS);
                if (permisos.procedimientos()) {
                    req.setAttribute("serviciosProcedimiento", servicio.serviciosProcedimiento());
                    req.setAttribute("personal", servicio.personalClinico());
                }
                if (permisos.recetar()) {
                    req.setAttribute("productos", servicio.productos());
                }
            });
            case "/consultas/receta" -> mostrar(req, res, "consultas/receta", () -> {
                long id = idObligatorio(req, res, "id");
                Consulta c = servicio.obtener(id);
                req.setAttribute("consulta", c);
                req.setAttribute("mascota", servicio.mascota(c.idMascota()));
                req.setAttribute("receta", servicio.receta(id).orElse(null));
                req.setAttribute("principal", servicio.diagnosticos(id).stream().filter(Diagnostico::esPrincipal)
                        .findFirst().orElse(null));
                req.setAttribute("veterinario", servicio.profesional(c.idVeterinario()));
                req.setAttribute("vias", ConsultaService.VIAS);
            });
            case "/consultas" -> redirigir(req, res, "/historia");
            default -> {
                String id = idTexto(req);
                redirigir(req, res, id == null ? "/historia" : "/consultas/ver?id=" + id);
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        String ruta = req.getServletPath();
        if ("/consultas/nueva".equals(ruta)) {
            abrir(req, res, u);
            return;
        }
        String id = idTexto(req);
        if (id == null) {
            error(req, "Falta el identificador de la consulta.");
            redirigir(req, res, "/historia");
            return;
        }
        long idConsulta = Long.parseLong(id);
        String ver = "/consultas/ver?id=" + id;
        switch (ruta) {
            case "/consultas/datos" -> procesar(req, res, ver + "#datos", ver + "#datos", "Datos clínicos guardados.",
                    () -> servicio.actualizarDatos(idConsulta, new ConsultaDatos(texto(req, "motivo"),
                            texto(req, "anamnesis"), texto(req, "examenClinico"), texto(req, "indicaciones"),
                            fecha(req, "fechaControl")), u));
            case "/consultas/signos" -> procesar(req, res, ver + "#signos", ver + "#signos", "Signos vitales registrados.",
                    () -> servicio.registrarSignos(idConsulta, new ConsultaSignos(decimal(req, "temperatura"),
                            entero(req, "frecuenciaCardiaca"), entero(req, "frecuenciaRespiratoria"), decimal(req, "peso")), u));
            case "/consultas/diagnostico" -> procesar(req, res, ver + "#diagnosticos", ver + "#diagnosticos",
                    "Diagnóstico registrado.", () -> servicio.agregarDiagnostico(idConsulta, texto(req, "descripcion"),
                            marcado(req, "principal"), u));
            case "/consultas/procedimiento" -> procesar(req, res, ver + "#procedimientos", ver + "#procedimientos",
                    "Procedimiento registrado con la tarifa vigente.", () -> servicio.agregarProcedimiento(idConsulta,
                            entero(req, "servicio"), entero(req, "responsable"), entero(req, "cantidad"),
                            texto(req, "resultado"), u));
            case "/consultas/receta" -> procesar(req, res, ver + "#receta", ver + "#receta", "Receta guardada.",
                    () -> servicio.guardarReceta(idConsulta, texto(req, "indicaciones"), u));
            case "/consultas/medicamento" -> procesar(req, res, ver + "#receta", ver + "#receta",
                    "Medicamento agregado a la receta.", () -> servicio.agregarMedicamento(idConsulta,
                            entero(req, "producto"), texto(req, "dosis"), texto(req, "frecuencia"), entero(req, "duracion"),
                            texto(req, "via"), decimal(req, "cantidad"), u));
            case "/consultas/firmar" -> procesar(req, res, ver, ver, null, () -> {
                exigirPermiso(req, ConsultaService.PERMISO_FIRMA);
                ConsultaFirma f = servicio.firmar(idConsulta, u);
                exito(req, mensajeFirma(f));
            });
            case "/consultas/adenda" -> procesar(req, res, ver + "#adendas", ver + "#adendas", "Adenda registrada con su autor y fecha.",
                    () -> servicio.agregarAdenda(idConsulta, texto(req, "texto"), u));
            default -> res.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /** RF-14: abre la consulta y lleva directamente a su registro. */
    private void abrir(HttpServletRequest req, HttpServletResponse res, UsuarioSesion u) throws IOException, ServletException {
        String cita = req.getParameter("cita");
        String mascota = req.getParameter("mascota");
        String volver = cita != null && ID.matcher(cita).matches() ? "/consultas/nueva?cita=" + cita
                : mascota != null && ID.matcher(mascota).matches() ? "/consultas/nueva?mascota=" + mascota : "/historia";
        procesar(req, res, DESTINO_DINAMICO, volver, "Consulta abierta. Registre signos, diagnóstico y tratamiento.", () -> {
            long id = servicio.abrir(largo(req, "cita"), entero(req, "mascota"), texto(req, "motivo"), u);
            req.setAttribute(DESTINO_DINAMICO, "/consultas/ver?id=" + id);
        });
    }

    private static String mensajeFirma(ConsultaFirma f) {
        StringBuilder m = new StringBuilder("Consulta firmada: desde ahora solo admite adendas (RN-05).");
        if (f.idCitaAtendida() != null) {
            m.append(" La cita pasó a «Atendida».");
        }
        if (f.conRecordatorio()) {
            m.append(" Recordatorio del control del ").append(FuncionesVista.fecha(f.fechaControl()))
                    .append(" programado para el ").append(FuncionesVista.fechaHora(f.recordatorioPara())).append('.');
        } else if (f.fechaControl() != null) {
            m.append(" Control el ").append(FuncionesVista.fecha(f.fechaControl()))
                    .append(": el propietario no aceptó recordatorios por correo, avísele por teléfono.");
        }
        return m.toString();
    }

    @Override
    protected void redirigir(HttpServletRequest req, HttpServletResponse res, String ruta) throws IOException {
        Object dinamico = req.getAttribute(DESTINO_DINAMICO);
        if (DESTINO_DINAMICO.equals(ruta)) {
            ruta = dinamico == null ? "/historia" : dinamico.toString();
        }
        super.redirigir(req, res, ruta);
    }

    private static String idTexto(HttpServletRequest req) {
        String v = req.getParameter("id");
        return v != null && ID.matcher(v).matches() ? v : null;
    }
}
