package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.ParametroDaoEnMemoria;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-24 / RNF-05: parámetros configurables con rangos razonables. */
class AdminParametroServiceTest {

    private static final UsuarioSesion ADMIN = new UsuarioSesion(1, "rparedes", "Rosa Paredes", "ADMINISTRADOR",
            Set.of("USUARIO_ADMINISTRAR"), null, false);

    private ParametroDaoEnMemoria dao;
    private ParametroService parametros;
    private AdminParametroService servicio;

    @BeforeEach
    void preparar() {
        dao = new ParametroDaoEnMemoria();
        parametros = new ParametroService(dao);
        servicio = new AdminParametroService(parametros);
    }

    @Test
    @DisplayName("RF-24: actualiza el parámetro y la caché devuelve el valor nuevo")
    void actualiza() throws Exception {
        assertEquals(30, parametros.entero(ParametroService.DIAS_POR_VENCER, 0));
        servicio.actualizar(ParametroService.DIAS_POR_VENCER, " 45 ", ADMIN);
        assertEquals("45", dao.valores.get(ParametroService.DIAS_POR_VENCER));
        assertEquals(45, parametros.entero(ParametroService.DIAS_POR_VENCER, 0));
    }

    @Test
    @DisplayName("RNF-05: valores fuera de rango o no enteros se rechazan")
    void rangos() {
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("DIAS_POR_VENCER", "0", ADMIN));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("DIAS_POR_VENCER", "366", ADMIN));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("MAX_INTENTOS_LOGIN", "2", ADMIN));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("MAX_INTENTOS_LOGIN", "11", ADMIN));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("DESCUENTO_MAX_PCT", "11", ADMIN));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("MINUTOS_SESION", "4", ADMIN));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("MINUTOS_SESION", "61", ADMIN));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("MINUTOS_SESION", "15.5", ADMIN));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("MINUTOS_SESION", "-5", ADMIN));
        assertEquals("15", dao.valores.get("MINUTOS_SESION"), "no cambió");
    }

    @Test
    @DisplayName("RNF-05: límites incluidos y parámetros desconocidos o sin permiso rechazados")
    void limitesYPermiso() throws Exception {
        servicio.actualizar("DESCUENTO_MAX_PCT", "0", ADMIN);
        servicio.actualizar("MAX_INTENTOS_LOGIN", "10", ADMIN);
        assertEquals("0", dao.valores.get("DESCUENTO_MAX_PCT"));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("OTRO", "5", ADMIN));
        UsuarioSesion cajero = new UsuarioSesion(7, "dramos", "Diego", "CAJERO", Set.of("CAJA_CERRAR"), null, false);
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar("MINUTOS_SESION", "20", cajero));
    }
}
