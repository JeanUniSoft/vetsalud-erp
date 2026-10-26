package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.PortalDaoEnMemoria;
import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.modelo.PortalCita;
import pe.vetsalud.erp.modelo.PortalControl;
import pe.vetsalud.erp.modelo.Recordatorio;

/** P11 / RF-37: el cliente solo ve sus mascotas, citas, controles y recordatorios. */
class PortalServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 25);
    private static final int MARIA = 1;
    private static final int JOSE = 2;

    private PortalDaoEnMemoria dao;
    private PortalService servicio;

    private static Mascota mascota(int id, int cliente, String nombre) {
        return new Mascota(id, cliente, "cliente " + cliente, 1, "Canino", 3, "Labrador", nombre, "M", null, null,
                BigDecimal.TEN, null, false, "Alerta interna", null, Mascota.ACTIVA, null);
    }

    @BeforeEach
    void preparar() {
        dao = new PortalDaoEnMemoria();
        servicio = new PortalService(dao, Clock.fixed(HOY.atTime(12, 0).atZone(LIMA).toInstant(), LIMA));
        dao.mascotas.add(mascota(2, MARIA, "Rocky"));
        dao.mascotas.add(mascota(4, MARIA, "Misha"));
        dao.mascotas.add(mascota(1, JOSE, "Toby"));
        dao.citas.add(new PortalCita(10, 4, "Misha", HOY.plusDays(1).atTime(10, 0), "Vacuna", "Dra. Salazar", "PENDIENTE"));
        dao.citas.add(new PortalCita(11, 2, "Rocky", HOY.minusDays(3).atTime(9, 0), "Consulta", "Dr. Mendoza", "ATENDIDA"));
        dao.citas.add(new PortalCita(12, 1, "Toby", HOY.plusDays(2).atTime(9, 0), "Consulta", "Dr. Mendoza", "PENDIENTE"));
        dao.controles.add(new PortalControl(1, 2, "Rocky", HOY.plusDays(7), "Dr. Mendoza"));
        dao.controles.add(new PortalControl(2, 1, "Toby", HOY.plusDays(7), "Dr. Mendoza"));
        dao.recordatorios.add(new Recordatorio(1, MARIA, "María", "m@x.pe", true, true, null, 1L, 2, "Rocky", "ACTIVA",
                null, null, HOY.plusDays(7), "CORREO", LocalDateTime.of(2026, 10, 31, 9, 0), "PENDIENTE", null));
    }

    @Test
    @DisplayName("P11: el cliente ve solo sus mascotas")
    void soloSusMascotas() throws Exception {
        assertEquals(2, servicio.mascotas(MARIA).size());
        assertTrue(servicio.mascotas(MARIA).stream().allMatch(m -> m.idCliente() == MARIA));
    }

    @Test
    @DisplayName("P11: una mascota ajena no se entrega aunque se cambie el id en la URL")
    void mascotaAjena() throws Exception {
        assertTrue(servicio.mascota(MARIA, 2).isPresent());
        assertTrue(servicio.mascota(MARIA, 1).isEmpty());
        assertTrue(servicio.mascota(null, 2).isEmpty());
    }

    @Test
    @DisplayName("P11: una cuenta sin cliente vinculado no accede a datos")
    void sinClienteVinculado() {
        assertThrows(ReglaNegocioException.class, () -> servicio.mascotas(null));
        assertThrows(ReglaNegocioException.class, () -> servicio.proximasCitas(null, null));
        assertThrows(ReglaNegocioException.class, () -> servicio.recordatorios(null));
    }

    @Test
    @DisplayName("RF-09 / RF-19: próximas citas y controles desde hoy, solo de sus mascotas")
    void citasYControles() throws Exception {
        assertEquals(1, servicio.proximasCitas(MARIA, null).size());
        assertEquals("Misha", servicio.proximasCitas(MARIA, null).get(0).mascota());
        assertTrue(servicio.proximasCitas(MARIA, 1).isEmpty(), "filtrar por una mascota ajena no devuelve nada");
        assertEquals(1, servicio.controles(MARIA, null).size());
        assertEquals("Rocky", servicio.controles(MARIA, 2).get(0).mascota());
    }

    @Test
    @DisplayName("RF-37: el cliente ve sus recordatorios con estado y fecha")
    void recordatorios() throws Exception {
        assertEquals(1, servicio.recordatorios(MARIA).size());
        assertTrue(servicio.recordatorios(JOSE).isEmpty());
    }
}
