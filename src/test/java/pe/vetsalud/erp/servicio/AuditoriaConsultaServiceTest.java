package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.AuditoriaConsultaDaoEnMemoria;
import pe.vetsalud.erp.modelo.AccesoRegistro;
import pe.vetsalud.erp.modelo.AuditoriaRegistro;
import pe.vetsalud.erp.modelo.CambioCampo;

/** RF-01 / RF-04: consulta de la bitácora de accesos y de la auditoría (solo lectura). */
class AuditoriaConsultaServiceTest {

    private AuditoriaConsultaDaoEnMemoria dao;
    private AuditoriaConsultaService servicio;

    @BeforeEach
    void preparar() {
        dao = new AuditoriaConsultaDaoEnMemoria();
        servicio = new AuditoriaConsultaService(dao);
    }

    @Test
    @DisplayName("RF-04: la diferencia de JSONB muestra solo lo que cambió y omite clave_hash")
    void diferenciaSinClave() {
        Map<String, String> antes = new LinkedHashMap<>();
        antes.put("activo", "true");
        antes.put("bloqueado", "true");
        antes.put("correo", "a@vetsalud.pe");
        antes.put("clave_hash", "pbkdf2_sha256$1$x$y");
        Map<String, String> despues = new LinkedHashMap<>(antes);
        despues.put("bloqueado", "false");
        despues.put("clave_hash", "pbkdf2_sha256$1$z$w");
        List<CambioCampo> cambios = AuditoriaConsultaService.diferencias(antes, despues);
        assertEquals(1, cambios.size());
        assertEquals(new CambioCampo("bloqueado", "true", "false", "MODIFICADO"), cambios.get(0));
        assertTrue(cambios.stream().noneMatch(c -> c.campo().contains("clave")));
    }

    @Test
    @DisplayName("RF-04: un alta muestra todos los campos como agregados y una baja como eliminados")
    void altaYBaja() {
        Map<String, String> fila = Map.of("id_cliente", "5", "nombres", "Ana", "CLAVE_HASH", "x");
        List<CambioCampo> alta = AuditoriaConsultaService.diferencias(Map.of(), fila);
        assertEquals(2, alta.size());
        assertEquals("AGREGADO", alta.get(0).tipo());
        assertEquals("id_cliente", alta.get(0).campo(), "orden alfabético");
        List<CambioCampo> baja = AuditoriaConsultaService.diferencias(fila, null);
        assertTrue(baja.stream().allMatch(c -> c.tipo().equals("ELIMINADO") && c.despues() == null));
        // un valor que pasa a null es una modificación
        assertEquals("MODIFICADO", AuditoriaConsultaService.diferencias(Map.of("motivo", "x"),
                new LinkedHashMap<>(java.util.Collections.singletonMap("motivo", null))).get(0).tipo());
    }

    @Test
    @DisplayName("RF-04: el detalle reconoce anulaciones, firmas y cierres por el cambio de estado")
    void eventos() throws Exception {
        dao.registros.add(new AuditoriaRegistro(9, LocalDateTime.now(), 7, "dramos", "venta", 6, "UPDATE", "ANULADA", 3));
        dao.antes.put(9L, Map.of("estado", "CONFIRMADA", "motivo_anulacion", ""));
        dao.despues.put(9L, Map.of("estado", "ANULADA", "motivo_anulacion", "Cliente desistió"));
        assertEquals("Anulación", servicio.obtener(9).evento());
        assertEquals(2, servicio.cambios(9).size());
        assertEquals("Firma", new AuditoriaRegistro(1, null, 4, "c", "consulta", 1, "UPDATE", "FIRMADA", 2).evento());
        assertEquals("Cierre", new AuditoriaRegistro(1, null, 7, "d", "caja_turno", 1, "UPDATE", "CERRADA", 5).evento());
        assertEquals("Alta", new AuditoriaRegistro(1, null, 1, "r", "cliente", 1, "INSERT", null, 9).evento());
    }

    @Test
    @DisplayName("RF-04: filtros validados (periodo, operación y tabla de una lista blanca)")
    void filtros() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 10, 25);
        LocalDate[] p = servicio.periodo(null, null, hoy);
        assertEquals(LocalDate.of(2026, 10, 19), p[0]);
        assertThrows(ReglaNegocioException.class, () -> servicio.periodo(hoy, hoy.minusDays(1), hoy));
        assertThrows(ReglaNegocioException.class, () -> servicio.periodo(hoy.minusDays(400), hoy, hoy));
        assertThrows(ReglaNegocioException.class, () -> servicio.buscar(null, "TRUNCATE", null, p[0], p[1]));
        assertThrows(ReglaNegocioException.class, () -> servicio.buscar("pg_shadow", null, null, p[0], p[1]));
        servicio.buscar(null, "update", null, p[0], p[1]);
        assertEquals("null|UPDATE|null|2026-10-19..2026-10-26", dao.ultimaConsulta);
    }

    @Test
    @DisplayName("RF-01: la bitácora de accesos filtra por resultado y resume exitosos y fallidos")
    void bitacoraDeAccesos() throws Exception {
        dao.accesos.add(new AccesoRegistro(1, LocalDateTime.now(), "rparedes", 1, "Rosa", "ADMINISTRADOR", "::1", true));
        dao.accesos.add(new AccesoRegistro(2, LocalDateTime.now(), "RPAREDES", 1, "Rosa", "ADMINISTRADOR", "::1", false));
        dao.accesos.add(new AccesoRegistro(3, LocalDateTime.now(), "fantasma", null, null, null, "10.0.0.8", false));
        LocalDate hoy = LocalDate.of(2026, 10, 25);
        List<AccesoRegistro> fallidos = servicio.accesos(null, "fallido", hoy, hoy);
        assertEquals(2, fallidos.size());
        assertEquals("null|false|2026-10-25..2026-10-26", dao.ultimaConsulta);
        assertThrows(ReglaNegocioException.class, () -> servicio.accesos(null, "quizas", hoy, hoy));
        Map<String, Long> r = AuditoriaConsultaService.resumenAccesos(dao.accesos);
        assertEquals(3L, r.get("total"));
        assertEquals(1L, r.get("exitosos"));
        assertEquals(2L, r.get("fallidos"));
        assertEquals(2L, r.get("usuarios"));
    }
}
