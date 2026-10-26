package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.CajaDaoEnMemoria;
import pe.vetsalud.erp.modelo.TurnoCaja;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-30 / RN-10: apertura y cierre de caja. */
class CajaServiceTest {

    private CajaDaoEnMemoria dao;
    private CajaService servicio;

    private final UsuarioSesion cajero = new UsuarioSesion(7, "dramos", "Diego Ramos", "CAJERO",
            Set.of("VENTA_PROCESAR", "CAJA_CERRAR"), null, false);
    private final UsuarioSesion otroCajero = new UsuarioSesion(8, "pcajas", "Pedro Cajas", "CAJERO",
            Set.of("VENTA_PROCESAR", "CAJA_CERRAR"), null, false);
    private final UsuarioSesion administrador = new UsuarioSesion(1, "rparedes", "Rosa Paredes", "ADMINISTRADOR",
            Set.of("CAJA_CERRAR"), null, false);
    private final UsuarioSesion farmacia = new UsuarioSesion(6, "mccori", "Milagros Ccori", "FARMACIA",
            Set.of("INVENTARIO_AJUSTAR"), null, false);

    @BeforeEach
    void preparar() {
        dao = new CajaDaoEnMemoria();
        servicio = new CajaService(dao);
    }

    @Test
    @DisplayName("RF-30: abre el turno con el monto inicial")
    void abre() throws Exception {
        int id = servicio.abrir(new BigDecimal("150"), cajero);
        TurnoCaja t = servicio.turnoAbierto(cajero).orElseThrow();
        assertEquals(id, t.idTurno());
        assertEquals(new BigDecimal("150.00"), t.montoInicial());
    }

    @Test
    @DisplayName("RN-10: un cajero no puede abrir dos turnos a la vez")
    void noDosTurnos() throws Exception {
        servicio.abrir(new BigDecimal("150"), cajero);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.abrir(new BigDecimal("100"), cajero));
        assertTrue(e.getMessage().contains("RN-10"));
        servicio.abrir(new BigDecimal("100"), otroCajero);   // otro cajero sí puede
        assertEquals(2, dao.abiertos().size());
    }

    @Test
    @DisplayName("RF-30: el monto inicial es obligatorio y no negativo; requiere permiso CAJA_CERRAR")
    void validacionesDeApertura() {
        assertThrows(ReglaNegocioException.class, () -> servicio.abrir(null, cajero));
        assertThrows(ReglaNegocioException.class, () -> servicio.abrir(new BigDecimal("-1"), cajero));
        assertThrows(ReglaNegocioException.class, () -> servicio.abrir(new BigDecimal("150"), farmacia));
    }

    @Test
    @DisplayName("RN-10: cerrar con diferencia y sin observación es rechazado")
    void diferenciaSinObservacion() throws Exception {
        int id = servicio.abrir(new BigDecimal("150"), cajero);
        dao.efectivoCobrado = new BigDecimal("195.00");     // esperado 345.00
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.cerrar(id, new BigDecimal("340.00"), "  ", cajero));
        assertTrue(e.getMessage().contains("observación"));
        assertTrue(servicio.turnoAbierto(cajero).isPresent());
    }

    @Test
    @DisplayName("RN-10: con observación se cierra y se devuelve la diferencia; si cuadra no la exige")
    void cierres() throws Exception {
        int id = servicio.abrir(new BigDecimal("150"), cajero);
        dao.efectivoCobrado = new BigDecimal("195.00");
        assertEquals(new BigDecimal("-5.00"), servicio.cerrar(id, new BigDecimal("340"), "Vuelto mal entregado", cajero));
        assertFalse(servicio.turnoAbierto(cajero).isPresent());

        int otro = servicio.abrir(new BigDecimal("100"), cajero);
        dao.efectivoCobrado = BigDecimal.ZERO;
        assertEquals(0, servicio.cerrar(otro, new BigDecimal("100.00"), null, cajero).signum());
        assertThrows(ReglaNegocioException.class, () -> servicio.cerrar(otro, BigDecimal.TEN, "x", cajero));
    }

    @Test
    @DisplayName("RF-30: no se cierra la caja con ventas pendientes")
    void pendientes() throws Exception {
        int id = servicio.abrir(new BigDecimal("150"), cajero);
        dao.ventasPendientes = 1;
        assertThrows(ReglaNegocioException.class, () -> servicio.cerrar(id, new BigDecimal("150"), null, cajero));
    }

    @Test
    @DisplayName("RF-02: el cajero solo ve sus turnos; el administrador ve y cierra cualquiera")
    void accesoATurnos() throws Exception {
        int id = servicio.abrir(new BigDecimal("150"), cajero);
        assertThrows(ReglaNegocioException.class, () -> servicio.obtener(id, otroCajero));
        assertThrows(ReglaNegocioException.class, () -> servicio.cerrar(id, new BigDecimal("150"), null, otroCajero));
        assertEquals(id, servicio.obtener(id, administrador).idTurno());
        assertEquals(1, servicio.abiertos(administrador).size());
        assertTrue(servicio.abiertos(cajero).isEmpty());
        servicio.cerrar(id, new BigDecimal("150"), null, administrador);
        assertEquals(1, servicio.historial(null, null, cajero).size());
        assertTrue(servicio.historial(null, null, otroCajero).isEmpty());
    }
}
