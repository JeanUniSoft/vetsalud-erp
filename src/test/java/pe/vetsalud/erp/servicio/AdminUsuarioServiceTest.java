package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.UsuarioAdminDaoEnMemoria;
import pe.vetsalud.erp.modelo.AdminCredencial;
import pe.vetsalud.erp.modelo.Opcion;
import pe.vetsalud.erp.modelo.UsuarioAdmin;
import pe.vetsalud.erp.seguridad.ClaveHasher;
import pe.vetsalud.erp.seguridad.PoliticaClave;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** P12 · Administración de usuarios (RF-02, RF-03). */
class AdminUsuarioServiceTest {

    private static final UsuarioSesion ADMIN = new UsuarioSesion(1, "rparedes", "Rosa Paredes", "ADMINISTRADOR",
            Set.of("USUARIO_ADMINISTRAR"), null, false);
    private static final UsuarioSesion SIN_PERMISO = new UsuarioSesion(2, "ltorres", "Lucía Torres", "RECEPCION",
            Set.of("CLIENTE_GESTIONAR"), null, false);

    private UsuarioAdminDaoEnMemoria dao;
    private ClaveHasher hasher;
    private AdminUsuarioService servicio;

    @BeforeEach
    void preparar() {
        dao = new UsuarioAdminDaoEnMemoria();
        dao.agregar(1, "ADMINISTRADOR", "rparedes", "rparedes@vetsalud.pe");
        dao.agregar(2, "RECEPCION", "ltorres", "ltorres@vetsalud.pe");
        dao.clientes.put("45678912", new Opcion(10, "María Gonzales"));
        dao.clientes.put("70123456", new Opcion(11, "José Pérez"));
        dao.vinculos.put(8, 10);                                     // María ya tiene usuario del portal
        hasher = new ClaveHasher();
        servicio = new AdminUsuarioService(dao, hasher, new GeneradorClaveTemporal());
    }

    private static UsuarioAdmin datos(String rol, String usuario, String correo, String colegiatura, String documento) {
        return UsuarioAdmin.formulario(null, rol, "  juan  carlos ", "pérez  soto", usuario, correo, colegiatura, documento);
    }

    @Test
    @DisplayName("RF-03: la clave temporal es aleatoria y siempre cumple la política de contraseñas")
    void claveTemporalCumplePolitica() {
        GeneradorClaveTemporal g = new GeneradorClaveTemporal();
        Set<String> generadas = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            String clave = g.generar("usuario.prueba");
            assertTrue(PoliticaClave.incumplimientos(clave, "usuario.prueba").isEmpty(), clave);
            assertEquals(GeneradorClaveTemporal.LONGITUD, clave.length());
            generadas.add(clave);
        }
        assertEquals(300, generadas.size(), "no se repiten");
        // con una semilla fija también cumple (no depende de la suerte)
        assertTrue(PoliticaClave.incumplimientos(new GeneradorClaveTemporal(new Random(7)).generar("abc"), "abc").isEmpty());
    }

    @Test
    @DisplayName("RF-03: el alta guarda solo el hash de la clave temporal y obliga a cambiarla")
    void altaConClaveTemporal() throws Exception {
        AdminCredencial c = servicio.crear(datos("RECEPCION", "JPerez", "JPerez@VetSalud.pe", null, null), ADMIN);
        UsuarioAdmin creado = dao.usuarios.get(c.idUsuario());
        assertEquals("jperez", creado.usuario());
        assertEquals("jperez@vetsalud.pe", creado.correo());
        assertEquals("Juan Carlos", creado.nombres());
        assertEquals("Pérez Soto", creado.apellidos());
        assertTrue(creado.debeCambiarClave());
        String hash = dao.hashes.get(c.idUsuario());
        assertNotEquals(c.claveTemporal(), hash, "nunca en texto plano");
        assertTrue(hasher.verificar(c.claveTemporal().toCharArray(), hash));
    }

    @Test
    @DisplayName("RF-02: el veterinario sin colegiatura CMVP es rechazado; con colegiatura se registra")
    void veterinarioRequiereColegiatura() throws Exception {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("VETERINARIO", "mvargas", "mvargas@vetsalud.pe", " ", null), ADMIN));
        assertTrue(e.getMessage().contains("CMVP"));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("VETERINARIO", "mvargas", "mvargas@vetsalud.pe", "12AB", null), ADMIN));
        AdminCredencial c = servicio.crear(datos("VETERINARIO", "mvargas", "mvargas@vetsalud.pe", "06120", null), ADMIN);
        assertEquals("06120", dao.usuarios.get(c.idUsuario()).colegiatura());
        AdminCredencial t = servicio.crear(datos("TECNICO", "rquispe", "rquispe@vetsalud.pe", "99999", null), ADMIN);
        assertNull(dao.usuarios.get(t.idUsuario()).colegiatura(), "solo veterinarios guardan colegiatura");
    }

    @Test
    @DisplayName("RF-02: usuario o correo duplicados se rechazan (sin importar mayúsculas)")
    void duplicados() {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("CAJERO", "LTorres", "otro@vetsalud.pe", null, null), ADMIN));
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("CAJERO", "nuevo.cajero", "LTORRES@vetsalud.pe", null, null), ADMIN));
        assertTrue(e.getMessage().contains("correo"));
    }

    @Test
    @DisplayName("RF-02: formato de usuario, correo y rol inexistente se validan")
    void formato() {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("CAJERO", "ab", "x@vetsalud.pe", null, null), ADMIN));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("CAJERO", "con espacio", "x@vetsalud.pe", null, null), ADMIN));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("CAJERO", "valido", "no-es-correo", null, null), ADMIN));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("SUPERUSUARIO", "valido", "x@vetsalud.pe", null, null), ADMIN));
    }

    @Test
    @DisplayName("P11: el rol CLIENTE se vincula a un cliente existente, un usuario por cliente")
    void usuarioDelPortal() throws Exception {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("CLIENTE", "portal.uno", "p1@gmail.com", null, null), ADMIN));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("CLIENTE", "portal.uno", "p1@gmail.com", null, "99999999"), ADMIN));
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("CLIENTE", "portal.uno", "p1@gmail.com", null, "45678912"), ADMIN));
        assertTrue(e.getMessage().contains("ya tiene un usuario"));
        AdminCredencial c = servicio.crear(datos("CLIENTE", "portal.uno", "p1@gmail.com", null, " 70123456 "), ADMIN);
        assertEquals(11, dao.vinculos.get(c.idUsuario()));
    }

    @Test
    @DisplayName("Continuidad: el administrador no puede desactivarse ni cambiar su propio rol")
    void noSeDesactivaASiMismo() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.cambiarEstado(1, false, ADMIN));
        assertTrue(e.getMessage().contains("propia cuenta"));
        assertTrue(dao.usuarios.get(1).activo());
        UsuarioAdmin cambio = UsuarioAdmin.formulario(1, "RECEPCION", "Rosa", "Paredes", "rparedes",
                "rparedes@vetsalud.pe", null, null);
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar(cambio, ADMIN));
        assertEquals("ADMINISTRADOR", dao.usuarios.get(1).rol());
    }

    @Test
    @DisplayName("RF-02: una cuenta del personal no puede convertirse en cuenta del portal")
    void personalNoPasaAPortal() {
        UsuarioAdmin cambio = UsuarioAdmin.formulario(2, "CLIENTE", "Lucía", "Torres", "ltorres", "ltorres@vetsalud.pe",
                null, "70123456");
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar(cambio, ADMIN));
    }

    @Test
    @DisplayName("RF-03: restablecer asigna clave temporal, desbloquea y exige cambiarla al ingresar")
    void restablecimiento() throws Exception {
        dao.bloquear(2);
        AdminCredencial c = servicio.restablecerClave(2, ADMIN);
        UsuarioAdmin u = dao.usuarios.get(2);
        assertFalse(u.bloqueado());
        assertEquals(0, u.intentosFallidos());
        assertTrue(u.debeCambiarClave());
        assertTrue(hasher.verificar(c.claveTemporal().toCharArray(), dao.hashes.get(2)));
        assertThrows(ReglaNegocioException.class, () -> servicio.restablecerClave(1, ADMIN),
                "la propia clave se cambia en «Mi cuenta»");
    }

    @Test
    @DisplayName("RF-01: desbloquear exige que la cuenta esté bloqueada; activar/desactivar otra cuenta")
    void desbloqueoYEstado() throws Exception {
        assertThrows(ReglaNegocioException.class, () -> servicio.desbloquear(2, ADMIN));
        dao.bloquear(2);
        servicio.desbloquear(2, ADMIN);
        assertFalse(dao.usuarios.get(2).bloqueado());
        servicio.cambiarEstado(2, false, ADMIN);
        assertFalse(dao.usuarios.get(2).activo());
        assertThrows(ReglaNegocioException.class, () -> servicio.restablecerClave(2, ADMIN),
                "a una cuenta inactiva no se le asigna clave");
        servicio.cambiarEstado(2, true, ADMIN);
        assertTrue(dao.usuarios.get(2).activo());
    }

    @Test
    @DisplayName("RF-02: sin el permiso USUARIO_ADMINISTRAR ninguna acción se ejecuta")
    void sinPermiso() {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(datos("CAJERO", "nuevo.cajero", "nc@vetsalud.pe", null, null), SIN_PERMISO));
        assertThrows(ReglaNegocioException.class, () -> servicio.restablecerClave(1, SIN_PERMISO));
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarEstado(1, false, SIN_PERMISO));
        assertEquals(2, dao.usuarios.size());
    }
}
