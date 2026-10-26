package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.NotificadorCorreoEnMemoria;
import pe.vetsalud.erp.fakes.RecordatorioDaoEnMemoria;
import pe.vetsalud.erp.modelo.Recordatorio;
import pe.vetsalud.erp.notificacion.NotificadorCorreo;

/** RF-37: recordatorios por correo con consentimiento, dirección válida y sin datos clínicos. */
class RecordatorioServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 25, 10, 0);

    private RecordatorioDaoEnMemoria dao;
    private NotificadorCorreoEnMemoria correo;
    private RecordatorioService servicio;

    @BeforeEach
    void preparar() {
        dao = new RecordatorioDaoEnMemoria(AHORA);
        correo = new NotificadorCorreoEnMemoria();
        servicio = new RecordatorioService(dao, correo, Clock.fixed(AHORA.atZone(LIMA).toInstant(), LIMA));
    }

    /** Recordatorio de cita mañana a las 10:00, vencido (programado hace una hora). */
    private static Recordatorio cita(long id, String correo, boolean consiente) {
        return new Recordatorio(id, 1, "María Gonzales", correo, consiente, true, 100L + id, null, 2, "Rocky",
                "ACTIVA", AHORA.plusDays(1), "PENDIENTE", null, "CORREO", AHORA.minusHours(1),
                Recordatorio.PENDIENTE, null);
    }

    private static Recordatorio control(long id, LocalDateTime programado) {
        return new Recordatorio(id, 1, "María Gonzales", "maria.gonzales@gmail.com", true, true, null, 50L + id, 4,
                "Misha", "ACTIVA", null, null, LocalDate.of(2026, 11, 1), "CORREO", programado,
                Recordatorio.PENDIENTE, null);
    }

    @Test
    @DisplayName("RF-37: con consentimiento y correo válido se envía y queda ENVIADO con fecha de envío")
    void envioConConsentimiento() throws Exception {
        dao.agregar(cita(1, "maria.gonzales@gmail.com", true));
        RecordatorioService.Resultado r = servicio.procesarPendientes(false, 1, "rparedes");
        assertEquals(1, r.enviados());
        assertEquals(1, correo.enviados.size());
        assertEquals("maria.gonzales@gmail.com", correo.enviados.get(0).destinatario());
        Recordatorio guardado = dao.recordatorios.get(1L);
        assertEquals(Recordatorio.ENVIADO, guardado.estado());
        assertEquals(AHORA, guardado.enviadoEn());
    }

    @Test
    @DisplayName("RF-37: sin consentimiento o sin correo válido queda FALLIDO y no se envía nada")
    void sinConsentimientoOSinCorreo() throws Exception {
        dao.agregar(cita(1, "maria.gonzales@gmail.com", false));
        dao.agregar(cita(2, null, true));
        dao.agregar(cita(3, "correo-invalido", true));
        RecordatorioService.Resultado r = servicio.procesarPendientes(false, 1, "rparedes");
        assertEquals(3, r.fallidos());
        assertTrue(correo.enviados.isEmpty());
        dao.recordatorios.values().forEach(x -> {
            assertEquals(Recordatorio.FALLIDO, x.estado());
            assertEquals(AHORA, x.enviadoEn(), "registra la fecha del intento");
        });
    }

    @Test
    @DisplayName("RF-37: el texto es genérico (fecha, hora y mascota) y no expone datos clínicos")
    void mensajeSinDatosClinicos() {
        NotificadorCorreo.Mensaje m = servicio.redactar(cita(1, "maria.gonzales@gmail.com", true));
        assertTrue(m.texto().contains("Rocky"));
        assertTrue(m.texto().contains("26/10/2026"));
        assertTrue(m.texto().contains("10:00"));
        for (String sensible : new String[] {"diagn", "receta", "anamnesis", "motivo", "María", "Gonzales"}) {
            assertFalse(m.texto().toLowerCase().contains(sensible.toLowerCase()), sensible);
        }
        NotificadorCorreo.Mensaje c = servicio.redactar(control(2, AHORA));
        assertTrue(c.asunto().contains("control"));
        assertTrue(c.texto().contains("01/11/2026"));
    }

    @Test
    @DisplayName("RF-37: por defecto solo procesa los vencidos; «procesar ahora» incluye los programados a futuro")
    void vencidosYProcesarAhora() throws Exception {
        dao.agregar(control(1, AHORA.minusMinutes(5)));
        dao.agregar(control(2, AHORA.plusDays(5)));
        assertEquals(1, servicio.procesarPendientes(false, 1, "rparedes").enviados());
        assertEquals(Recordatorio.PENDIENTE, dao.recordatorios.get(2L).estado());
        assertEquals(1, servicio.procesarPendientes(true, 1, "rparedes").enviados());
        assertEquals(Recordatorio.ENVIADO, dao.recordatorios.get(2L).estado());
        assertEquals(2, correo.enviados.size());
    }

    @Test
    @DisplayName("RF-37: cita cancelada, evento pasado o mascota fallecida → CANCELADO sin envío")
    void cancelados() throws Exception {
        Recordatorio base = cita(1, "maria.gonzales@gmail.com", true);
        dao.agregar(new Recordatorio(1, 1, "María", base.correo(), true, true, 101L, null, 2, "Rocky", "ACTIVA",
                AHORA.plusDays(1), "CANCELADA", null, "CORREO", AHORA.minusHours(1), Recordatorio.PENDIENTE, null));
        dao.agregar(new Recordatorio(2, 1, "María", base.correo(), true, true, 102L, null, 2, "Rocky", "ACTIVA",
                AHORA.minusHours(2), "PENDIENTE", null, "CORREO", AHORA.minusDays(1), Recordatorio.PENDIENTE, null));
        dao.agregar(new Recordatorio(3, 1, "María", base.correo(), true, true, 103L, null, 2, "Rocky", "FALLECIDA",
                AHORA.plusDays(1), "PENDIENTE", null, "CORREO", AHORA.minusHours(1), Recordatorio.PENDIENTE, null));
        RecordatorioService.Resultado r = servicio.procesarPendientes(false, 1, "rparedes");
        assertEquals(3, r.cancelados());
        assertTrue(correo.enviados.isEmpty());
        dao.recordatorios.values().forEach(x -> {
            assertEquals(Recordatorio.CANCELADO, x.estado());
            assertNull(x.enviadoEn());
        });
    }

    @Test
    @DisplayName("RF-37: si el canal de correo falla, el recordatorio queda FALLIDO")
    void canalCaido() throws Exception {
        dao.agregar(cita(1, "maria.gonzales@gmail.com", true));
        correo.fallar = true;
        RecordatorioService.Resultado r = servicio.procesarPendientes(false, 1, "rparedes");
        assertEquals(1, r.fallidos());
        assertEquals(Recordatorio.FALLIDO, dao.recordatorios.get(1L).estado());
        assertNotNull(dao.recordatorios.get(1L).enviadoEn());
    }

    @Test
    @DisplayName("RF-37: un recordatorio que otro proceso ya tomó no se envía dos veces")
    void sinEnvioDoble() throws Exception {
        dao.agregar(cita(1, "maria.gonzales@gmail.com", true));
        dao.tomadosPorOtro.add(1L);
        RecordatorioService.Resultado r = servicio.procesarPendientes(false, 1, "rparedes");
        assertEquals(0, r.enviados());
        assertEquals(1, r.omitidos());
        assertTrue(correo.enviados.isEmpty());
    }

    @Test
    @DisplayName("RF-37: programa citas 24 h antes y controles el día anterior a las 09:00")
    void programacion() throws Exception {
        dao.citasPorProgramar = 2;
        dao.controlesPorProgramar = 1;
        RecordatorioService.Programacion p = servicio.programar(1, "rparedes");
        assertEquals(24, dao.horasProgramadas);
        assertEquals(LocalTime.of(9, 0), dao.horaControlProgramada);
        assertEquals(3, p.citas() + p.controles());
        assertTrue(p.mensaje().contains("2"));
    }

    @Test
    @DisplayName("RNF-15: el destinatario se muestra enmascarado y el filtro de estado se valida")
    void enmascaradoYFiltro() throws Exception {
        assertEquals("m***@gmail.com", Recordatorio.enmascararCorreo("maria.gonzales@gmail.com"));
        assertEquals("", Recordatorio.enmascararCorreo(null));
        dao.agregar(cita(1, "maria.gonzales@gmail.com", true));
        assertEquals(1, servicio.listar("pendiente").size());
        assertEquals(0, servicio.listar("ENVIADO").size());
        assertThrows(ReglaNegocioException.class, () -> servicio.listar("BORRADO"));
        assertEquals(1, servicio.resumen().pendientesVencidos());
    }
}
