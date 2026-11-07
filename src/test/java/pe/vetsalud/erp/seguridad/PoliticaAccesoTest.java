package pe.vetsalud.erp.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-02: cada rol ve solo sus pantallas (tabla de roles del APF2). */
class PoliticaAccesoTest {

    @Test
    @DisplayName("Caja entra a ventas y caja, pero no a la historia clínica ni a reportes")
    void cajero() {
        assertTrue(PoliticaAcceso.permite("CAJERO", "/ventas/nueva"));
        assertTrue(PoliticaAcceso.permite("CAJERO", "/caja"));
        assertFalse(PoliticaAcceso.permite("CAJERO", "/historia"));
        assertFalse(PoliticaAcceso.permite("CAJERO", "/reportes"));
    }

    @Test
    @DisplayName("El cliente solo accede a su portal y a su cuenta")
    void cliente() {
        assertTrue(PoliticaAcceso.permite("CLIENTE", "/portal"));
        assertTrue(PoliticaAcceso.permite("CLIENTE", "/cuenta"));
        for (String ruta : List.of("/panel", "/clientes", "/ventas", "/admin/usuarios")) {
            assertFalse(PoliticaAcceso.permite("CLIENTE", ruta), ruta);
        }
        assertEquals("/portal", PoliticaAcceso.inicio("CLIENTE"));
    }

    @Test
    @DisplayName("Un prefijo parecido no da acceso (/ventasX no es /ventas)")
    void prefijoExacto() {
        assertFalse(PoliticaAcceso.permite("CAJERO", "/ventasx"));
        assertTrue(PoliticaAcceso.esPublica("/static/css/app.css"));
        assertFalse(PoliticaAcceso.esPublica("/panel"));
    }

    @Test
    @DisplayName("Política de clave: rechaza claves débiles y acepta una robusta")
    void politicaClave() {
        assertFalse(PoliticaClave.incumplimientos("abc", "jperez").isEmpty());
        assertFalse(PoliticaClave.incumplimientos("Jperez#2026", "jperez").isEmpty());
        assertTrue(PoliticaClave.incumplimientos("Clinica#2026", "jperez").isEmpty());
    }

    // ---------------------------------------------------------------- S13: pruebas de seguridad (hallazgo SEG-03/SEG-04)

    @Test
    @DisplayName("S13: las rutas comunes exigen coincidencia exacta; /cuenta/../admin no es /cuenta")
    void rutaComunManipulada() {
        for (String ruta : List.of("/cuenta/../admin/usuarios", "/cuenta/..;/admin/usuarios", "/logout/../reportes",
                "/sin-permiso/../admin/auditoria", "/cuentax")) {
            assertFalse(PoliticaAcceso.permite("CLIENTE", ruta), ruta);
        }
        assertTrue(PoliticaAcceso.permite("CLIENTE", "/cuenta"));
    }

    @Test
    @DisplayName("S13: una ruta con .. nunca es pública aunque empiece por /static/ o /login")
    void rutaPublicaManipulada() {
        for (String ruta : List.of("/static/../admin/usuarios", "/static/..;/admin/usuarios", "/login/../panel",
                "/loginx", "/recuperar/x", "/favicon.ico/../admin")) {
            assertFalse(PoliticaAcceso.esPublica(ruta), ruta);
        }
        assertTrue(PoliticaAcceso.esPublica("/login"));
        assertTrue(PoliticaAcceso.esPublica("/recuperar"));
        assertTrue(PoliticaAcceso.esPublica("/favicon.ico"));
        assertTrue(PoliticaAcceso.esPublica("/static/img/logo.svg"));
    }
}
