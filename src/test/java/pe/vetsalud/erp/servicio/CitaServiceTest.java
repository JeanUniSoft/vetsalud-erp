package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.vetsalud.erp.fakes.MascotaClinicaDaoEnMemoria.BRUNO;
import static pe.vetsalud.erp.fakes.MascotaClinicaDaoEnMemoria.FALLECIDA;
import static pe.vetsalud.erp.fakes.MascotaClinicaDaoEnMemoria.MAX;
import static pe.vetsalud.erp.fakes.ProfesionalDaoEnMemoria.MENDOZA;
import static pe.vetsalud.erp.fakes.ProfesionalDaoEnMemoria.RECEPCION;
import static pe.vetsalud.erp.fakes.ProfesionalDaoEnMemoria.SALAZAR;
import static pe.vetsalud.erp.fakes.ProfesionalDaoEnMemoria.VET_INACTIVO;
import static pe.vetsalud.erp.fakes.ServicioDaoEnMemoria.CONSULTA;
import static pe.vetsalud.erp.fakes.ServicioDaoEnMemoria.INACTIVO;
import static pe.vetsalud.erp.fakes.ServicioDaoEnMemoria.VACUNA;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pe.vetsalud.erp.fakes.CitaDaoEnMemoria;
import pe.vetsalud.erp.fakes.MascotaClinicaDaoEnMemoria;
import pe.vetsalud.erp.fakes.ProfesionalDaoEnMemoria;
import pe.vetsalud.erp.fakes.ServicioDaoEnMemoria;
import pe.vetsalud.erp.modelo.AgendaFiltro;
import pe.vetsalud.erp.modelo.AgendaVista;
import pe.vetsalud.erp.modelo.CambioEstadoCita;
import pe.vetsalud.erp.modelo.Cita;
import pe.vetsalud.erp.modelo.CitaSolicitud;
import pe.vetsalud.erp.modelo.ControlProgramado;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-09 a RF-12 y RN-03: programación, estados, reprogramación y agenda de citas. */
class CitaServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** «Ahora» de las pruebas: domingo 25/10/2026 a las 10:00. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 25, 10, 0);
    private static final LocalDateTime MANANA_10 = LocalDateTime.of(2026, 10, 26, 10, 0);

    private final UsuarioSesion recepcion = new UsuarioSesion(RECEPCION, "ltorres", "Lucía Torres Vega", "RECEPCION",
            Set.of("CITA_GESTIONAR", "CLIENTE_GESTIONAR"), null, false);
    private final UsuarioSesion tecnico = new UsuarioSesion(5, "jhuaman", "Jorge Huamán Cruz", "TECNICO", Set.of(), null, false);
    private final UsuarioSesion veterinario = new UsuarioSesion(MENDOZA, "cmendoza", "Carlos Mendoza Ríos", "VETERINARIO",
            Set.of("CITA_GESTIONAR", "CONSULTA_FIRMAR"), null, false);

    private CitaDaoEnMemoria dao;
    private CitaService servicio;

    @BeforeEach
    void preparar() {
        dao = new CitaDaoEnMemoria();
        servicio = new CitaService(dao, new ServicioDaoEnMemoria(), new ProfesionalDaoEnMemoria(),
                new MascotaClinicaDaoEnMemoria(), Clock.fixed(AHORA.atZone(LIMA).toInstant(), LIMA));
    }

    private static CitaSolicitud consultaDeMax(LocalDateTime inicio) {
        return new CitaSolicitud(MAX, CONSULTA, MENDOZA, inicio, "Vómitos desde ayer");
    }

    private Cita citaEn(String estado) {
        return dao.agregar(MAX, "Max", MENDOZA, "Carlos Mendoza Ríos", CONSULTA, MANANA_10, MANANA_10.plusMinutes(30), estado);
    }

    // ------------------------------------------------------------------ RF-09 programar

    @Test
    @DisplayName("RF-09: la duración de la cita es la del servicio (consulta 30 min, vacuna 20 min)")
    void duracionPorServicio() throws Exception {
        Cita consulta = servicio.programar(consultaDeMax(MANANA_10), recepcion);
        assertEquals(MANANA_10.plusMinutes(30), consulta.fin());
        Cita vacuna = servicio.programar(new CitaSolicitud(BRUNO, VACUNA, SALAZAR, MANANA_10, null), recepcion);
        assertEquals(MANANA_10.plusMinutes(20), vacuna.fin());
        assertEquals("PENDIENTE", vacuna.estado());
    }

    @Test
    @DisplayName("RF-09: no se programa una cita en el pasado")
    void noEnElPasado() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.programar(consultaDeMax(AHORA.minusDays(1)), recepcion));
        assertTrue(e.getMessage().contains("pasado"));
        assertThrows(ReglaNegocioException.class, () -> servicio.programar(consultaDeMax(AHORA.minusHours(1)), recepcion));
        assertTrue(dao.citas.isEmpty());
    }

    @Test
    @DisplayName("RF-09: se admite el turno en curso (15 min de tolerancia para quien llega sin cita)")
    void turnoEnCurso() throws Exception {
        Cita c = servicio.programar(consultaDeMax(AHORA.minusMinutes(10)), recepcion);
        assertEquals(AHORA.minusMinutes(10), c.inicio());
    }

    @ParameterizedTest(name = "inicio a las {0}")
    @CsvSource({"07:30", "07:59", "19:45", "20:00", "23:50"})
    @DisplayName("RF-09: la cita debe empezar y terminar dentro del horario de atención 08:00–20:00")
    void fueraDeHorario(String hora) {
        LocalDateTime inicio = LocalDate.of(2026, 10, 26).atTime(LocalTime.parse(hora));
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.programar(consultaDeMax(inicio), recepcion));
        assertTrue(e.getMessage().contains("horario de atención"));
    }

    @Test
    @DisplayName("RF-09: una consulta de 19:30 termina justo a las 20:00 y se acepta; a las 08:00 también")
    void limitesDelHorario() throws Exception {
        assertEquals(LocalTime.of(20, 0), servicio.programar(consultaDeMax(MANANA_10.withHour(19).withMinute(30)), recepcion)
                .fin().toLocalTime());
        assertEquals(LocalTime.of(8, 0), servicio.programar(consultaDeMax(MANANA_10.withHour(8)), recepcion)
                .inicio().toLocalTime());
    }

    @Test
    @DisplayName("RN-03: el mismo veterinario no puede tener dos citas que se crucen")
    void sinSolapamiento() throws Exception {
        servicio.programar(consultaDeMax(MANANA_10), recepcion);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.programar(
                new CitaSolicitud(BRUNO, CONSULTA, MENDOZA, MANANA_10.plusMinutes(15), null), recepcion));
        assertTrue(e.getMessage().contains("RN-03"));
        assertEquals(1, dao.citas.size());
    }

    @Test
    @DisplayName("RN-03: citas contiguas (10:00–10:30 y 10:30–11:00) sí se permiten")
    void citasContiguas() throws Exception {
        servicio.programar(consultaDeMax(MANANA_10), recepcion);
        servicio.programar(new CitaSolicitud(BRUNO, CONSULTA, MENDOZA, MANANA_10.plusMinutes(30), null), recepcion);
        assertEquals(2, dao.citas.size());
    }

    @Test
    @DisplayName("RF-09: la mascota no puede tener dos citas a la misma hora con distintos veterinarios")
    void mascotaSinDobleCita() throws Exception {
        servicio.programar(consultaDeMax(MANANA_10), recepcion);
        assertThrows(ReglaNegocioException.class, () -> servicio.programar(
                new CitaSolicitud(MAX, VACUNA, SALAZAR, MANANA_10.plusMinutes(10), null), recepcion));
    }

    @Test
    @DisplayName("RF-09: solo un médico veterinario activo atiende citas")
    void soloVeterinarioActivo() {
        assertThrows(ReglaNegocioException.class, () -> servicio.programar(
                new CitaSolicitud(MAX, CONSULTA, RECEPCION, MANANA_10, null), recepcion));
        assertThrows(ReglaNegocioException.class, () -> servicio.programar(
                new CitaSolicitud(MAX, CONSULTA, VET_INACTIVO, MANANA_10, null), recepcion));
    }

    @Test
    @DisplayName("RF-09: no se programan citas para mascotas no activas ni con servicios inactivos")
    void mascotaYServicioActivos() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.programar(
                new CitaSolicitud(FALLECIDA, CONSULTA, MENDOZA, MANANA_10, null), recepcion));
        assertTrue(e.getMessage().contains("Kira"));
        assertThrows(ReglaNegocioException.class, () -> servicio.programar(
                new CitaSolicitud(MAX, INACTIVO, MENDOZA, MANANA_10, null), recepcion));
    }

    @Test
    @DisplayName("RF-02: sin el permiso CITA_GESTIONAR (técnico) no se modifica la agenda")
    void sinPermiso() {
        assertThrows(ReglaNegocioException.class, () -> servicio.programar(consultaDeMax(MANANA_10), tecnico));
        Cita c = citaEn("PENDIENTE");
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarEstado(c.idCita(), "CONFIRMADA", null, tecnico));
    }

    // ------------------------------------------------------------------ RF-12 estados

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"PENDIENTE,CONFIRMADA", "PENDIENTE,CANCELADA", "CONFIRMADA,EN_ESPERA", "CONFIRMADA,CANCELADA",
            "CONFIRMADA,NO_ASISTIO", "EN_ESPERA,EN_ATENCION", "EN_ESPERA,CANCELADA", "EN_ATENCION,ATENDIDA"})
    @DisplayName("RF-12: transiciones válidas (las mismas del trigger fn_cita_estado) con historial")
    void transicionValida(String desde, String hacia) throws Exception {
        assertTrue(CitaService.esTransicionValida(desde, hacia));
        Cita c = citaEn(desde);
        Cita nueva = servicio.cambiarEstado(c.idCita(), hacia, "Motivo de prueba", recepcion);
        assertEquals(hacia, nueva.estado());
        CambioEstadoCita ultimo = dao.historial(c.idCita()).getLast();
        assertEquals(desde, ultimo.estadoAnterior());
        assertEquals(hacia, ultimo.estadoNuevo());
        assertEquals("usuario" + RECEPCION, ultimo.usuario());
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"PENDIENTE,EN_ATENCION", "PENDIENTE,ATENDIDA", "CONFIRMADA,ATENDIDA", "EN_ESPERA,NO_ASISTIO",
            "EN_ATENCION,CANCELADA", "ATENDIDA,CONFIRMADA", "CANCELADA,PENDIENTE", "NO_ASISTIO,CONFIRMADA",
            "CONFIRMADA,PENDIENTE"})
    @DisplayName("RF-12: transiciones inválidas rechazadas sin modificar la cita")
    void transicionInvalida(String desde, String hacia) {
        Cita c = citaEn(desde);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarEstado(c.idCita(), hacia, "Motivo", recepcion));
        assertTrue(e.getMessage().contains("RF-12"));
        assertEquals(desde, dao.citas.get(c.idCita()).estado());
        assertTrue(dao.historial(c.idCita()).isEmpty());
    }

    @Test
    @DisplayName("RF-12: atendida, cancelada y no asistió son estados finales: la agenda no ofrece acciones")
    void estadosFinales() throws Exception {
        assertTrue(CitaService.transicionesDesde("ATENDIDA").isEmpty());
        assertTrue(CitaService.transicionesDesde("CANCELADA").isEmpty());
        assertTrue(CitaService.transicionesDesde("NO_ASISTIO").isEmpty());
        Cita c = citaEn("CONFIRMADA");
        assertEquals(List.of("EN_ESPERA", "NO_ASISTIO", "CANCELADA"), servicio.obtener(c.idCita()).transiciones());
    }

    @Test
    @DisplayName("RF-18: no se marca «Atendida» a mano una cita cuya consulta sigue abierta")
    void atendidaConConsultaAbierta() {
        Cita c = citaEn("EN_ATENCION");
        dao.citas.put(c.idCita(), c.conConsulta(7L, "ABIERTA"));
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarEstado(c.idCita(), "ATENDIDA", null, veterinario));
        assertEquals("EN_ATENCION", dao.citas.get(c.idCita()).estado());
    }

    // ------------------------------------------------------------------ RF-10 cancelar y reprogramar

    @Test
    @DisplayName("RF-10: cancelar exige motivo y lo registra junto al historial (usuario, fecha y estados)")
    void cancelarConMotivo() throws Exception {
        Cita c = citaEn("PENDIENTE");
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarEstado(c.idCita(), "CANCELADA", "  ", recepcion));
        Cita cancelada = servicio.cambiarEstado(c.idCita(), "CANCELADA", "El cliente viajó", recepcion);
        assertEquals("CANCELADA", cancelada.estado());
        assertEquals("Motivo original · Cancelada: El cliente viajó", cancelada.motivo());
        CambioEstadoCita h = dao.historial(c.idCita()).getLast();
        assertEquals("PENDIENTE", h.estadoAnterior());
        assertEquals("CANCELADA", h.estadoNuevo());
        assertEquals("usuario" + RECEPCION, h.usuario());
    }

    @Test
    @DisplayName("RF-10: la cita cancelada libera el horario para otra mascota")
    void cancelarLiberaHorario() throws Exception {
        Cita c = servicio.programar(consultaDeMax(MANANA_10), recepcion);
        servicio.cambiarEstado(c.idCita(), "CANCELADA", "Reprogramará por teléfono", recepcion);
        Cita otra = servicio.programar(new CitaSolicitud(BRUNO, CONSULTA, MENDOZA, MANANA_10, null), recepcion);
        assertEquals(MANANA_10, otra.inicio());
    }

    @Test
    @DisplayName("RF-10: reprogramar exige motivo, recalcula el fin, libera el horario y deja constancia en el historial")
    void reprogramar() throws Exception {
        Cita c = servicio.programar(consultaDeMax(MANANA_10), recepcion);
        assertThrows(ReglaNegocioException.class,
                () -> servicio.reprogramar(c.idCita(), MANANA_10.plusDays(1), null, null, recepcion));
        Cita movida = servicio.reprogramar(c.idCita(), MANANA_10.plusDays(1), SALAZAR, "El cliente pidió otro día", recepcion);
        assertEquals(MANANA_10.plusDays(1), movida.inicio());
        assertEquals(MANANA_10.plusDays(1).plusMinutes(30), movida.fin());
        assertEquals(SALAZAR, movida.idVeterinario());
        CambioEstadoCita h = dao.historial(c.idCita()).getLast();
        assertTrue(h.esReprogramacion());
        assertTrue(h.motivo().startsWith("Reprogramada del 26/10/2026 10:00 al 27/10/2026 10:00"));
        assertTrue(h.motivo().endsWith("Motivo: El cliente pidió otro día"));
        // el horario original quedó libre
        servicio.programar(new CitaSolicitud(BRUNO, CONSULTA, MENDOZA, MANANA_10, null), recepcion);
    }

    @Test
    @DisplayName("RF-10: solo se reprograma una cita pendiente o confirmada")
    void reprogramarSoloPendienteOConfirmada() {
        Cita c = citaEn("EN_ESPERA");
        assertThrows(ReglaNegocioException.class,
                () -> servicio.reprogramar(c.idCita(), MANANA_10.plusDays(1), null, "Cambio", recepcion));
    }

    @Test
    @DisplayName("RN-03: al reprogramar se evita cruzar con otra cita, pero no con la propia")
    void reprogramarSinCruces() throws Exception {
        Cita a = servicio.programar(consultaDeMax(MANANA_10), recepcion);
        servicio.programar(new CitaSolicitud(BRUNO, CONSULTA, MENDOZA, MANANA_10.plusHours(1), null), recepcion);
        assertThrows(ReglaNegocioException.class,
                () -> servicio.reprogramar(a.idCita(), MANANA_10.plusMinutes(45), null, "Llega tarde", recepcion));
        Cita movida = servicio.reprogramar(a.idCita(), MANANA_10.plusMinutes(15), null, "Llega tarde", recepcion);
        assertEquals(MANANA_10.plusMinutes(15), movida.inicio());
    }

    @Test
    @DisplayName("RF-10: la nota de cancelación cabe en cita.motivo (200) y se conserva completa")
    void anotarMotivoRespetaLongitud() {
        String nota = "Cancelada: " + "x".repeat(100);
        String r = CitaService.anotarMotivo("m".repeat(200), nota);
        assertTrue(r.length() <= 200);
        assertTrue(r.endsWith(nota));
        assertEquals(nota, CitaService.anotarMotivo(null, nota));
    }

    // ------------------------------------------------------------------ RF-11 agenda y RF-19 controles

    @Test
    @DisplayName("RF-11: la vista semanal va de lunes a domingo y agrupa las citas por día")
    void vistaSemanal() throws Exception {
        servicio.programar(new CitaSolicitud(MAX, CONSULTA, MENDOZA, LocalDateTime.of(2026, 10, 27, 9, 0), null), recepcion);
        AgendaVista v = servicio.agenda(new AgendaFiltro(LocalDate.of(2026, 10, 28), "semana", null, null, null));
        assertEquals(LocalDate.of(2026, 10, 26), v.desde());
        assertEquals(LocalDate.of(2026, 11, 1), v.hasta());
        assertEquals(7, v.dias().size());
        assertEquals("Lun 26", v.dias().getFirst().nombre());
        assertEquals(1, v.dias().get(1).citas().size());
        assertEquals(LocalDate.of(2026, 10, 19), v.anterior());
        assertEquals(LocalDate.of(2026, 11, 2), v.siguiente());
    }

    @Test
    @DisplayName("RF-11: agenda del día por defecto, con filtros de profesional, servicio y estado")
    void filtros() throws Exception {
        servicio.programar(consultaDeMax(AHORA.plusHours(1)), recepcion);
        servicio.programar(new CitaSolicitud(BRUNO, VACUNA, SALAZAR, AHORA.plusHours(1), null), recepcion);
        AgendaVista hoy = servicio.agenda(new AgendaFiltro(null, null, null, null, null));
        assertEquals(AHORA.toLocalDate(), hoy.desde());
        assertEquals(2, hoy.citas().size());
        assertEquals(2L, hoy.conteoPorEstado().get("PENDIENTE"));
        assertEquals(1, servicio.agenda(new AgendaFiltro(null, "dia", SALAZAR, null, null)).citas().size());
        assertEquals(1, servicio.agenda(new AgendaFiltro(null, "dia", null, CONSULTA, null)).citas().size());
        assertEquals(0, servicio.agenda(new AgendaFiltro(null, "dia", null, null, "CONFIRMADA")).citas().size());
    }

    @Test
    @DisplayName("RF-11: un estado o una vista desconocidos en el filtro se ignoran")
    void filtroNormalizado() {
        AgendaFiltro f = servicio.normalizar(new AgendaFiltro(null, "mes", null, null, "'; DROP TABLE cita; --"));
        assertNull(f.estado());
        assertEquals("dia", f.vista());
    }

    @Test
    @DisplayName("RF-11: el veterinario abre por defecto su propia agenda; recepción ve la de todos")
    void agendaPorDefecto() {
        assertEquals(MENDOZA, servicio.veterinarioPorDefecto(veterinario));
        assertNull(servicio.veterinarioPorDefecto(recepcion));
    }

    @Test
    @DisplayName("RF-19: la agenda muestra los controles programados dentro del rango visible")
    void controlesEnLaAgenda() throws Exception {
        dao.controles.add(new ControlProgramado(1, LocalDate.of(2026, 10, 30), MAX, "Max", "Canino", "Jorge Paredes",
                "951234876", MENDOZA, "Carlos Mendoza Ríos", "Gastroenteritis", false));
        dao.controles.add(new ControlProgramado(2, LocalDate.of(2026, 11, 20), BRUNO, "Bruno", "Canino", "Ricardo Flores",
                "934556712", SALAZAR, "Ana Salazar León", "Dermatitis", false));
        assertEquals(0, servicio.agenda(new AgendaFiltro(null, "dia", null, null, null)).controles().size());
        AgendaVista semana = servicio.agenda(new AgendaFiltro(LocalDate.of(2026, 10, 30), "semana", null, null, null));
        assertEquals(1, semana.controles().size());
        assertEquals("Max", semana.controles().getFirst().mascota());
    }
}
