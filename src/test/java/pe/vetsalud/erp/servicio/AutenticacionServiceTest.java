package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.AlertaDaoEnMemoria;
import pe.vetsalud.erp.fakes.ParametroDaoEnMemoria;
import pe.vetsalud.erp.fakes.UsuarioDaoEnMemoria;
import pe.vetsalud.erp.modelo.Usuario;
import pe.vetsalud.erp.seguridad.ClaveHasher;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-01 a RF-03: autenticación, bloqueo por intentos y cambio de contraseña. */
class AutenticacionServiceTest {

    private static final String CLAVE = "VetSalud2026!";

    private UsuarioDaoEnMemoria usuarios;
    private AlertaDaoEnMemoria alertas;
    private AutenticacionService servicio;
    private final ClaveHasher hasher = new ClaveHasher();

    @BeforeEach
    void preparar() {
        usuarios = new UsuarioDaoEnMemoria();
        alertas = new AlertaDaoEnMemoria();
        usuarios.agregar(new Usuario(3, "VETERINARIO", "Carlos", "Mendoza Ríos", "cmendoza", "cmendoza@vetsalud.pe",
                hasher.hashear(CLAVE.toCharArray()), "04512", 0, false, true, false, null));
        usuarios.permisos.put("VETERINARIO", Set.of("CONSULTA_FIRMAR", "CITA_GESTIONAR"));
        servicio = new AutenticacionService(usuarios, alertas, hasher, new ParametroService(new ParametroDaoEnMemoria()));
    }

    @Test
    @DisplayName("RF-01: credenciales válidas crean la sesión con rol y permisos")
    void ingresoValido() throws Exception {
        UsuarioSesion sesion = servicio.autenticar("CMendoza", CLAVE, "127.0.0.1");

        assertEquals("VETERINARIO", sesion.rol());
        assertTrue(sesion.puede("CONSULTA_FIRMAR"));
        assertEquals("Carlos Mendoza Ríos", sesion.nombreCompleto());
        assertTrue(usuarios.accesos.contains("CMendoza:OK"));
    }

    @Test
    @DisplayName("RF-01: clave incorrecta suma un intento y se audita el fallo")
    void claveIncorrecta() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.autenticar("cmendoza", "otra", "127.0.0.1"));

        assertEquals(AutenticacionService.MENSAJE_CREDENCIALES, e.getMessage());
        assertEquals(1, usuarios.usuarios.get(3).intentosFallidos());
        assertTrue(usuarios.accesos.contains("cmendoza:FALLO"));
    }

    @Test
    @DisplayName("RF-01: al quinto intento fallido la cuenta se bloquea")
    void bloqueoPorIntentos() {
        for (int i = 0; i < 5; i++) {
            assertThrows(ReglaNegocioException.class, () -> servicio.autenticar("cmendoza", "mala", "127.0.0.1"));
        }
        assertTrue(usuarios.usuarios.get(3).bloqueado());
        // aunque ahora use la clave correcta, sigue bloqueado
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.autenticar("cmendoza", CLAVE, "127.0.0.1"));
        assertTrue(e.getMessage().contains("bloqueada"));
    }

    @Test
    @DisplayName("Un usuario inexistente recibe el mismo mensaje (no se revela si existe)")
    void usuarioInexistente() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.autenticar("nadie", CLAVE, "127.0.0.1"));
        assertEquals(AutenticacionService.MENSAJE_CREDENCIALES, e.getMessage());
    }

    @Test
    @DisplayName("RF-03: la nueva clave debe cumplir la política y la anterior deja de funcionar")
    void cambioDeClave() throws Exception {
        UsuarioSesion sesion = servicio.autenticar("cmendoza", CLAVE, "127.0.0.1");

        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarClave(sesion, CLAVE, "corta", "corta"));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarClave(sesion, CLAVE, "Nueva#Clave2026", "Distinta#2026"));

        servicio.cambiarClave(sesion, CLAVE, "Nueva#Clave2026", "Nueva#Clave2026");

        assertThrows(ReglaNegocioException.class, () -> servicio.autenticar("cmendoza", CLAVE, "127.0.0.1"));
        assertEquals("cmendoza", servicio.autenticar("cmendoza", "Nueva#Clave2026", "127.0.0.1").usuario());
    }

    @Test
    @DisplayName("RF-03: la recuperación avisa al administrador sin revelar si la cuenta existe")
    void recuperacionControlada() throws Exception {
        servicio.solicitarRecuperacion("cmendoza@vetsalud.pe", "127.0.0.1");
        servicio.solicitarRecuperacion("noexiste@x.pe", "127.0.0.1");

        assertEquals(1, alertas.creadas.size());
        assertTrue(alertas.creadas.get(0).startsWith("ADMINISTRADOR|OTRO|"));
        assertFalse(alertas.creadas.get(0).contains("VetSalud2026"));
    }
}
