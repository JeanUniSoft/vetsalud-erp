package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.RolPermisoDaoEnMemoria;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-02: administración de la matriz de permisos por rol. */
class RolPermisoServiceTest {

    private static final UsuarioSesion ADMIN = new UsuarioSesion(1, "rparedes", "Rosa Paredes", "ADMINISTRADOR",
            Set.of("USUARIO_ADMINISTRAR"), null, false);

    private RolPermisoDaoEnMemoria dao;
    private RolPermisoService servicio;

    @BeforeEach
    void preparar() {
        dao = new RolPermisoDaoEnMemoria();
        dao.roles.addAll(List.of("ADMINISTRADOR", "RECEPCION", "TECNICO"));
        dao.permisos.put("USUARIO_ADMINISTRAR", "SEGURIDAD");
        dao.permisos.put("CITA_GESTIONAR", "AGENDA");
        dao.permisos.put("ALERTA_ATENDER", "ALERTAS");
        dao.asignacion.put("ADMINISTRADOR", "USUARIO_ADMINISTRAR");
        dao.asignacion.put("ADMINISTRADOR", "CITA_GESTIONAR");
        dao.asignacion.put("RECEPCION", "CITA_GESTIONAR");
        servicio = new RolPermisoService(dao);
    }

    private List<String> actualMas(String... extra) {
        List<String> m = new ArrayList<>();
        dao.asignacion.entries().forEach(e -> m.add(e.getKey() + "|" + e.getValue()));
        m.addAll(List.of(extra));
        return m;
    }

    @Test
    @DisplayName("RF-02: el administrador no puede quitarle USUARIO_ADMINISTRAR a su propio rol")
    void noSeQuitaAdministracion() {
        List<String> sinAdmin = new ArrayList<>(actualMas());
        sinAdmin.remove("ADMINISTRADOR|USUARIO_ADMINISTRAR");
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.guardar(sinAdmin, ADMIN));
        assertTrue(e.getMessage().contains("USUARIO_ADMINISTRAR"));
        assertTrue(dao.asignacion.containsEntry("ADMINISTRADOR", "USUARIO_ADMINISTRAR"), "no se aplicó nada");
    }

    @Test
    @DisplayName("RF-02: guardar aplica solo la diferencia (altas y bajas) en una operación")
    void aplicaDiferencia() throws Exception {
        List<String> nueva = actualMas("TECNICO|ALERTA_ATENDER");
        nueva.remove("RECEPCION|CITA_GESTIONAR");
        assertEquals(2, servicio.guardar(nueva, ADMIN));
        assertTrue(dao.ultimoAgregar.containsEntry("TECNICO", "ALERTA_ATENDER"));
        assertTrue(dao.ultimoQuitar.containsEntry("RECEPCION", "CITA_GESTIONAR"));
        assertEquals(1, dao.ultimoAgregar.size());
        assertEquals(1, dao.ultimoQuitar.size());
    }

    @Test
    @DisplayName("Seguridad: un rol o permiso que no existe en la BD se rechaza (casilla manipulada)")
    void valoresDesconocidos() {
        assertThrows(ReglaNegocioException.class, () -> servicio.guardar(actualMas("ROOT|USUARIO_ADMINISTRAR"), ADMIN));
        assertThrows(ReglaNegocioException.class, () -> servicio.guardar(actualMas("TECNICO|BORRAR_TODO"), ADMIN));
        assertThrows(ReglaNegocioException.class, () -> servicio.guardar(actualMas("TECNICO"), ADMIN));
    }

    @Test
    @DisplayName("RF-02: sin cambios o sin permiso no se guarda nada")
    void sinCambiosNiPermiso() {
        assertThrows(ReglaNegocioException.class, () -> servicio.guardar(actualMas(), ADMIN));
        UsuarioSesion recepcion = new UsuarioSesion(2, "ltorres", "Lucía", "RECEPCION", Set.of(), null, false);
        assertThrows(ReglaNegocioException.class, () -> servicio.guardar(actualMas("TECNICO|ALERTA_ATENDER"), recepcion));
    }
}
