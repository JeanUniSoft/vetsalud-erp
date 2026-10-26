package pe.vetsalud.erp.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import pe.vetsalud.erp.dao.CitaDao;
import pe.vetsalud.erp.dao.ConsultaDao;
import pe.vetsalud.erp.dao.MascotaClinicaDao;
import pe.vetsalud.erp.dao.ProfesionalDao;
import pe.vetsalud.erp.dao.ServicioDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.Adenda;
import pe.vetsalud.erp.modelo.Cita;
import pe.vetsalud.erp.modelo.Consulta;
import pe.vetsalud.erp.modelo.ConsultaDatos;
import pe.vetsalud.erp.modelo.ConsultaFirma;
import pe.vetsalud.erp.modelo.ConsultaPermisos;
import pe.vetsalud.erp.modelo.ConsultaSignos;
import pe.vetsalud.erp.modelo.Diagnostico;
import pe.vetsalud.erp.modelo.MascotaClinica;
import pe.vetsalud.erp.modelo.ProcedimientoConsulta;
import pe.vetsalud.erp.modelo.ProductoReceta;
import pe.vetsalud.erp.modelo.Profesional;
import pe.vetsalud.erp.modelo.Receta;
import pe.vetsalud.erp.modelo.RecetaLinea;
import pe.vetsalud.erp.modelo.Servicio;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * UC-06 Atender consulta (RF-14 a RF-19; RN-04 firma del veterinario con diagnóstico principal,
 * RN-05 consulta firmada inmutable: solo adendas).
 * Reparto por rol: el TÉCNICO registra signos vitales y procedimientos; el VETERINARIO responsable además
 * edita los datos clínicos, diagnostica, receta y firma. Los triggers de la BD vuelven a validar.
 */
public class ConsultaService {

    public static final String VETERINARIO = "VETERINARIO";
    public static final String TECNICO = "TECNICO";
    public static final String PERMISO_FIRMA = "CONSULTA_FIRMAR";

    public static final BigDecimal TEMPERATURA_MIN = new BigDecimal("30");
    public static final BigDecimal TEMPERATURA_MAX = new BigDecimal("45");
    public static final int FC_MAX = 400;
    public static final int FR_MAX = 200;
    public static final BigDecimal PESO_MAX = new BigDecimal("999.99");
    public static final int MAX_TEXTO = 4000;
    public static final int DIAS_CONTROL_MAX = 365;

    /** Vías de administración (CHECK de receta_detalle) con su nombre para la vista. */
    public static final Map<String, String> VIAS = ImmutableMap.<String, String>builder()
            .put("ORAL", "Oral").put("TOPICA", "Tópica").put("SC", "Subcutánea (SC)").put("IM", "Intramuscular (IM)")
            .put("IV", "Intravenosa (IV)").put("OFTALMICA", "Oftálmica").put("OTICA", "Ótica").build();

    private static final Set<String> PERSONAL_CLINICO = Set.of(VETERINARIO, TECNICO);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ConsultaDao dao;
    private final CitaDao citaDao;
    private final MascotaClinicaDao mascotaDao;
    private final ServicioDao servicioDao;
    private final ProfesionalDao profesionalDao;

    public ConsultaService(ConsultaDao dao, CitaDao citaDao, MascotaClinicaDao mascotaDao, ServicioDao servicioDao,
                           ProfesionalDao profesionalDao) {
        this.dao = Preconditions.checkNotNull(dao);
        this.citaDao = Preconditions.checkNotNull(citaDao);
        this.mascotaDao = Preconditions.checkNotNull(mascotaDao);
        this.servicioDao = Preconditions.checkNotNull(servicioDao);
        this.profesionalDao = Preconditions.checkNotNull(profesionalDao);
    }

    // ------------------------------------------------------------------ lectura

    public Consulta obtener(long idConsulta) throws SQLException {
        return dao.buscarPorId(idConsulta)
                .orElseThrow(() -> new ReglaNegocioException("La consulta N.° " + idConsulta + " no existe."));
    }

    public List<Diagnostico> diagnosticos(long idConsulta) throws SQLException {
        return dao.diagnosticos(idConsulta);
    }

    public List<ProcedimientoConsulta> procedimientos(long idConsulta) throws SQLException {
        return dao.procedimientos(idConsulta);
    }

    public Optional<Receta> receta(long idConsulta) throws SQLException {
        return dao.receta(idConsulta);
    }

    public List<Adenda> adendas(long idConsulta) throws SQLException {
        return dao.adendas(idConsulta);
    }

    public MascotaClinica mascota(int idMascota) throws SQLException {
        return mascotaDao.buscarPorId(idMascota)
                .orElseThrow(() -> new ReglaNegocioException("La mascota seleccionada no existe."));
    }

    public Cita cita(long idCita) throws SQLException {
        return citaDao.buscarPorId(idCita).orElseThrow(() -> new ReglaNegocioException("La cita N.° " + idCita + " no existe."));
    }

    public Optional<Consulta> consultaDeCita(long idCita) throws SQLException {
        return dao.buscarPorCita(idCita);
    }

    public Profesional profesional(int idUsuario) throws SQLException {
        return profesionalDao.buscarPorId(idUsuario)
                .orElseThrow(() -> new ReglaNegocioException("El profesional no existe."));
    }

    /** Servicios que se registran como procedimiento (todos los activos menos la consulta en sí). */
    public List<Servicio> serviciosProcedimiento() throws SQLException {
        return servicioDao.activos().stream().filter(s -> !s.esConsulta()).toList();
    }

    public List<Profesional> personalClinico() throws SQLException {
        return profesionalDao.personalClinico();
    }

    public List<ProductoReceta> productos() throws SQLException {
        return dao.productosActivos();
    }

    /** RN-04: solo un médico veterinario abre consultas. */
    public boolean puedeAbrir(UsuarioSesion u) {
        return VETERINARIO.equals(u.rol());
    }

    /** Lo que el usuario puede hacer sobre la consulta; los métodos de escritura aplican las mismas reglas. */
    public ConsultaPermisos permisos(Consulta c, UsuarioSesion u) {
        boolean abierta = c.abierta();
        boolean responsable = esResponsable(c, u);
        boolean tecnico = TECNICO.equals(u.rol());
        return new ConsultaPermisos(abierta && responsable, abierta && (responsable || tecnico), abierta && responsable,
                abierta && (responsable || tecnico), abierta && responsable,
                abierta && responsable && u.puede(PERMISO_FIRMA), c.firmada() && PERSONAL_CLINICO.contains(u.rol()));
    }

    // ------------------------------------------------------------------ abrir y registrar (RF-14)

    /**
     * Abre una consulta desde una cita en atención o directamente para una mascota (atención sin cita).
     * El profesional responsable es el veterinario que la abre.
     */
    public long abrir(Long idCita, Integer idMascota, String motivo, UsuarioSesion u) throws SQLException {
        if (!puedeAbrir(u)) {
            throw new ReglaNegocioException("Solo un médico veterinario puede abrir una consulta (RN-04).");
        }
        Profesional vet = profesional(u.idUsuario());
        if (!vet.esVeterinario() || !vet.activo()) {
            throw new ReglaNegocioException("El profesional responsable debe ser un médico veterinario activo (RN-04).");
        }
        String m = StringUtils.trimToNull(motivo);
        MascotaClinica mascota;
        if (idCita != null) {
            Cita c = cita(idCita);
            if (!"EN_ATENCION".equals(c.estado())) {
                throw new ReglaNegocioException("Para iniciar la consulta, la cita debe estar «En atención» (está «"
                        + CitaService.nombreEstado(c.estado()) + "», RF-12).");
            }
            Optional<Consulta> existente = dao.buscarPorCita(idCita);
            if (existente.isPresent()) {
                throw new ReglaNegocioException("La cita ya tiene la consulta N.° " + existente.get().idConsulta() + ".");
            }
            mascota = mascota(c.idMascota());
            m = m == null ? c.motivo() : m;
        } else {
            if (idMascota == null) {
                throw new ReglaNegocioException("Seleccione la mascota que se atenderá.");
            }
            mascota = mascota(idMascota);
        }
        if (!mascota.activa()) {
            throw new ReglaNegocioException("La mascota «" + mascota.nombre() + "» no está activa.");
        }
        m = Validaciones.requerido(m, "Motivo de consulta");
        Validaciones.longitudMaxima(m, 300, "Motivo de consulta");
        long id = dao.insertar(mascota.idMascota(), idCita, vet.idUsuario(), m, u.idUsuario());
        Auditoria.registrar("CONSULTA_ABRIR", "consulta", "id=" + id + " mascota=" + mascota.idMascota()
                + " cita=" + idCita + " por=" + u.usuario());
        return id;
    }

    /** RF-14 y RF-19: motivo, anamnesis, examen clínico, indicaciones y fecha de control. */
    public void actualizarDatos(long idConsulta, ConsultaDatos d, UsuarioSesion u) throws SQLException {
        Consulta c = editable(idConsulta, u, false, "modificar los datos clínicos");
        String motivo = Validaciones.requerido(d.motivo(), "Motivo de consulta");
        Validaciones.longitudMaxima(motivo, 300, "Motivo de consulta");
        String anamnesis = texto(d.anamnesis(), "Anamnesis");
        String examen = texto(d.examenClinico(), "Examen clínico");
        String indicaciones = texto(d.indicaciones(), "Indicaciones");
        validarControl(d.fechaControl(), c);
        if (!dao.actualizarDatos(idConsulta, new ConsultaDatos(motivo, anamnesis, examen, indicaciones, d.fechaControl()),
                u.idUsuario())) {
            throw new ReglaNegocioException("La consulta ya no está abierta; recargue la página.");
        }
        Auditoria.registrar("CONSULTA_ACTUALIZAR", "consulta", "id=" + idConsulta + " por=" + u.usuario());
    }

    /** RF-14: signos vitales dentro de rangos fisiológicos (los mismos CHECK de la tabla consulta). */
    public void registrarSignos(long idConsulta, ConsultaSignos s, UsuarioSesion u) throws SQLException {
        editable(idConsulta, u, true, "registrar signos vitales");
        ConsultaSignos v = validarSignos(s);
        if (!dao.actualizarSignos(idConsulta, v, u.idUsuario())) {
            throw new ReglaNegocioException("La consulta ya no está abierta; recargue la página.");
        }
        Auditoria.registrar("CONSULTA_SIGNOS", "consulta", "id=" + idConsulta + " por=" + u.usuario());
    }

    static ConsultaSignos validarSignos(ConsultaSignos s) {
        if (s.temperaturaC() == null && s.frecCardiaca() == null && s.frecRespiratoria() == null && s.pesoKg() == null) {
            throw new ReglaNegocioException("Ingrese al menos un signo vital.");
        }
        BigDecimal t = s.temperaturaC() == null ? null : s.temperaturaC().setScale(1, RoundingMode.HALF_UP);
        if (t != null && (t.compareTo(TEMPERATURA_MIN) < 0 || t.compareTo(TEMPERATURA_MAX) > 0)) {
            throw new ReglaNegocioException("La temperatura debe estar entre 30 y 45 °C (RF-14).");
        }
        if (s.frecCardiaca() != null && (s.frecCardiaca() < 1 || s.frecCardiaca() > FC_MAX)) {
            throw new ReglaNegocioException("La frecuencia cardiaca debe estar entre 1 y " + FC_MAX + " latidos por minuto (RF-14).");
        }
        if (s.frecRespiratoria() != null && (s.frecRespiratoria() < 1 || s.frecRespiratoria() > FR_MAX)) {
            throw new ReglaNegocioException("La frecuencia respiratoria debe estar entre 1 y " + FR_MAX + " respiraciones por minuto (RF-14).");
        }
        BigDecimal p = s.pesoKg() == null ? null : s.pesoKg().setScale(2, RoundingMode.HALF_UP);
        if (p != null && (p.signum() <= 0 || p.compareTo(PESO_MAX) > 0)) {
            throw new ReglaNegocioException("El peso debe ser mayor que 0 y menor que 1000 kg (RF-14).");
        }
        return new ConsultaSignos(t, s.frecCardiaca(), s.frecRespiratoria(), p);
    }

    /** RF-19: el control es posterior a la fecha de la consulta y dentro de un año. */
    static void validarControl(LocalDate control, Consulta c) {
        if (control == null) {
            return;
        }
        LocalDate dia = c.fechaHora().toLocalDate();
        if (!control.isAfter(dia)) {
            throw new ReglaNegocioException("La fecha de control debe ser posterior a la fecha de la consulta ("
                    + dia.format(FECHA) + ") (RF-19).");
        }
        if (control.isAfter(dia.plusDays(DIAS_CONTROL_MAX))) {
            throw new ReglaNegocioException("La fecha de control debe estar dentro del año siguiente a la consulta.");
        }
    }

    // ------------------------------------------------------------------ diagnósticos y procedimientos (RF-15, RF-16)

    public long agregarDiagnostico(long idConsulta, String descripcion, boolean principal, UsuarioSesion u)
            throws SQLException {
        editable(idConsulta, u, false, "registrar diagnósticos");
        String d = Validaciones.requerido(descripcion, "Diagnóstico");
        Validaciones.longitudMaxima(d, 200, "Diagnóstico");
        List<Diagnostico> actuales = dao.diagnosticos(idConsulta);
        Optional<Diagnostico> yaPrincipal = actuales.stream().filter(Diagnostico::esPrincipal).findFirst();
        if (principal && yaPrincipal.isPresent()) {
            throw new ReglaNegocioException("La consulta ya tiene un diagnóstico principal («" + yaPrincipal.get().descripcion()
                    + "»); registre este como secundario (RF-15).");
        }
        if (actuales.stream().anyMatch(x -> x.descripcion().equalsIgnoreCase(d))) {
            throw new ReglaNegocioException("Ese diagnóstico ya está registrado en la consulta.");
        }
        long id = dao.insertarDiagnostico(idConsulta, d, principal, u.idUsuario());
        Auditoria.registrar("DIAGNOSTICO_AGREGAR", "consulta_diagnostico", "id=" + id + " consulta=" + idConsulta
                + " principal=" + principal + " por=" + u.usuario());
        return id;
    }

    /** RF-16: responsable, cantidad, resultado e importe (la tarifa vigente se copia como precio). */
    public long agregarProcedimiento(long idConsulta, Integer idServicio, Integer idResponsable, Integer cantidad,
                                     String resultado, UsuarioSesion u) throws SQLException {
        editable(idConsulta, u, true, "registrar procedimientos");
        if (idServicio == null) {
            throw new ReglaNegocioException("Seleccione el procedimiento realizado.");
        }
        Servicio s = servicioDao.buscarPorId(idServicio).filter(x -> x.activo() && !x.esConsulta())
                .orElseThrow(() -> new ReglaNegocioException("Seleccione un procedimiento activo del catálogo."));
        if (idResponsable == null) {
            throw new ReglaNegocioException("Seleccione el responsable del procedimiento.");
        }
        Profesional r = profesionalDao.buscarPorId(idResponsable)
                .filter(p -> p.activo() && PERSONAL_CLINICO.contains(p.rol()))
                .orElseThrow(() -> new ReglaNegocioException("El responsable debe ser un veterinario o técnico activo (RF-16)."));
        int n = cantidad == null ? 1 : cantidad;
        if (n < 1 || n > 99) {
            throw new ReglaNegocioException("La cantidad debe estar entre 1 y 99.");
        }
        String res = StringUtils.trimToNull(resultado);
        Validaciones.longitudMaxima(res, 2000, "Resultado");
        long id = dao.insertarProcedimiento(new ProcedimientoConsulta(0, idConsulta, s.idServicio(), s.nombre(),
                r.idUsuario(), r.nombreCompleto(), n, s.tarifa(), res), u.idUsuario());
        Auditoria.registrar("PROCEDIMIENTO_AGREGAR", "consulta_procedimiento", "id=" + id + " consulta=" + idConsulta
                + " servicio=" + s.idServicio() + " por=" + u.usuario());
        return id;
    }

    // ------------------------------------------------------------------ receta (RF-17)

    /** Emite la receta de la consulta o actualiza sus indicaciones generales. */
    public long guardarReceta(long idConsulta, String indicaciones, UsuarioSesion u) throws SQLException {
        editable(idConsulta, u, false, "emitir recetas");
        String ind = StringUtils.trimToNull(indicaciones);
        Validaciones.longitudMaxima(ind, 2000, "Indicaciones de la receta");
        Optional<Receta> actual = dao.receta(idConsulta);
        long id;
        if (actual.isPresent()) {
            id = actual.get().idReceta();
            dao.actualizarReceta(id, ind, u.idUsuario());
        } else {
            id = dao.insertarReceta(idConsulta, ind, u.idUsuario());
        }
        Auditoria.registrar("RECETA_EMITIR", "receta", "id=" + id + " consulta=" + idConsulta + " por=" + u.usuario());
        return id;
    }

    /** Agrega un medicamento; si la consulta aún no tiene receta, la crea en la misma transacción. */
    public long agregarMedicamento(long idConsulta, Integer idProducto, String dosis, String frecuencia,
                                   Integer duracionDias, String via, BigDecimal cantidad, UsuarioSesion u)
            throws SQLException {
        editable(idConsulta, u, false, "recetar");
        if (idProducto == null) {
            throw new ReglaNegocioException("Seleccione el producto.");
        }
        ProductoReceta p = dao.producto(idProducto).filter(ProductoReceta::activo)
                .orElseThrow(() -> new ReglaNegocioException("El producto no existe o no está activo."));
        String ds = Validaciones.requerido(dosis, "Dosis");
        Validaciones.longitudMaxima(ds, 60, "Dosis");
        String fr = Validaciones.requerido(frecuencia, "Frecuencia");
        Validaciones.longitudMaxima(fr, 60, "Frecuencia");
        if (duracionDias == null || duracionDias < 1 || duracionDias > 365) {
            throw new ReglaNegocioException("La duración del tratamiento debe estar entre 1 y 365 días (RF-17).");
        }
        if (via == null || !VIAS.containsKey(via)) {
            throw new ReglaNegocioException("Seleccione una vía de administración válida: " + String.join(", ", VIAS.values()) + ".");
        }
        if (cantidad == null || cantidad.signum() <= 0 || cantidad.compareTo(new BigDecimal("9999")) > 0) {
            throw new ReglaNegocioException("La cantidad a dispensar debe ser mayor que cero.");
        }
        Optional<Receta> receta = dao.receta(idConsulta);
        if (receta.isPresent() && receta.get().lineas().stream().anyMatch(l -> l.idProducto() == p.idProducto())) {
            throw new ReglaNegocioException("«" + p.nombre() + "» ya está en la receta.");
        }
        long id = dao.insertarLineaReceta(idConsulta, new RecetaLinea(0, receta.map(Receta::idReceta).orElse(0L),
                p.idProducto(), p.nombre(), p.unidad(), ds, fr, duracionDias, via,
                cantidad.setScale(2, RoundingMode.HALF_UP)), u.idUsuario());
        Auditoria.registrar("RECETA_MEDICAMENTO", "receta_detalle", "id=" + id + " consulta=" + idConsulta
                + " producto=" + p.idProducto() + " por=" + u.usuario());
        return id;
    }

    // ------------------------------------------------------------------ firma y adendas (RF-18, RF-19, RN-04, RN-05)

    public ConsultaFirma firmar(long idConsulta, UsuarioSesion u) throws SQLException {
        if (!u.puede(PERMISO_FIRMA)) {
            throw new ReglaNegocioException("Su rol no tiene el permiso " + PERMISO_FIRMA + ".");
        }
        Consulta c = editable(idConsulta, u, false, "firmar la consulta");
        if (dao.diagnosticos(idConsulta).stream().noneMatch(Diagnostico::esPrincipal)) {
            throw new ReglaNegocioException("No se puede firmar sin un diagnóstico principal (RN-04, RF-15).");
        }
        validarControl(c.fechaControl(), c);
        MascotaClinica mascota = mascota(c.idMascota());
        Integer cliente = null;
        LocalDateTime para = null;
        if (c.fechaControl() != null && mascota.admiteRecordatorios()) {
            cliente = mascota.idCliente();
            para = recordatorioPara(c.fechaControl());
        }
        Long cita = null;
        if (c.idCita() != null) {
            cita = citaDao.buscarPorId(c.idCita()).filter(x -> "EN_ATENCION".equals(x.estado()))
                    .map(Cita::idCita).orElse(null);
        }
        ConsultaFirma firma = new ConsultaFirma(idConsulta, c.fechaControl(), cliente, para, cita);
        if (!dao.firmar(firma, u.idUsuario())) {
            throw new ReglaNegocioException("La consulta ya fue firmada; recargue la página.");
        }
        Auditoria.registrar("CONSULTA_FIRMAR", "consulta", "id=" + idConsulta + " recordatorio=" + firma.conRecordatorio()
                + " cita=" + cita + " por=" + u.usuario());
        return firma;
    }

    /** RF-19: el recordatorio sale el día anterior al control, a las 09:00. */
    public static LocalDateTime recordatorioPara(LocalDate control) {
        return control.minusDays(1).atTime(9, 0);
    }

    /** RN-05: sobre una consulta firmada solo se agregan adendas, con autor y fecha. */
    public long agregarAdenda(long idConsulta, String texto, UsuarioSesion u) throws SQLException {
        Consulta c = obtener(idConsulta);
        if (!c.firmada()) {
            throw new ReglaNegocioException("La consulta aún está abierta: modifique sus datos directamente; "
                    + "las adendas son para consultas firmadas (RN-05).");
        }
        if (!PERSONAL_CLINICO.contains(u.rol())) {
            throw new ReglaNegocioException("Solo el personal clínico (veterinario o técnico) registra adendas.");
        }
        String t = Validaciones.requerido(texto, "Texto de la adenda");
        Validaciones.longitudMaxima(t, 2000, "Texto de la adenda");
        long id = dao.insertarAdenda(idConsulta, t, u.idUsuario());
        Auditoria.registrar("ADENDA_AGREGAR", "adenda", "id=" + id + " consulta=" + idConsulta + " por=" + u.usuario());
        return id;
    }

    // ------------------------------------------------------------------ apoyo

    private static boolean esResponsable(Consulta c, UsuarioSesion u) {
        return VETERINARIO.equals(u.rol()) && c.idVeterinario() == u.idUsuario();
    }

    /** Devuelve la consulta si sigue abierta y el usuario puede modificarla (RN-04, RN-05). */
    private Consulta editable(long idConsulta, UsuarioSesion u, boolean admiteTecnico, String accion) throws SQLException {
        Consulta c = obtener(idConsulta);
        if (c.firmada()) {
            throw new ReglaNegocioException("La consulta N.° " + idConsulta
                    + " está firmada y ya no se modifica; registre una adenda (RN-05).");
        }
        boolean tecnico = admiteTecnico && TECNICO.equals(u.rol());
        if (!esResponsable(c, u) && !tecnico) {
            throw new ReglaNegocioException(admiteTecnico
                    ? "Solo el veterinario responsable (" + c.veterinario() + ") o un técnico puede " + accion + "."
                    : "Solo el veterinario responsable (" + c.veterinario() + ") puede " + accion + " (RN-04).");
        }
        return c;
    }

    private static String texto(String valor, String campo) {
        String v = StringUtils.trimToNull(valor);
        Validaciones.longitudMaxima(v, MAX_TEXTO, campo);
        return v;
    }
}
