package pe.vetsalud.erp.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * S13 · Pruebas de seguridad del filtro (hallazgos SEG-03 y SEG-04).
 * El contenedor resuelve "/static/..;/admin/usuarios" hacia el servlet /admin/usuarios, mientras que getRequestURI()
 * conserva el texto sin normalizar: el filtro debe decidir con la ruta que el contenedor va a ejecutar.
 * Se usan dobles con Proxy para no agregar dependencias de mocks al proyecto.
 */
class FiltroSeguridadTest {

    /** Respuesta observada por la prueba. */
    private static final class Resultado {
        boolean cadena;
        boolean reenviado;
        Integer estado;
        String redireccion;
    }

    private static HttpSession sesion(UsuarioSesion usuario) {
        Map<String, Object> atributos = new HashMap<>();
        atributos.put(FiltroSeguridad.ATRIBUTO_USUARIO, usuario);
        return (HttpSession) Proxy.newProxyInstance(FiltroSeguridadTest.class.getClassLoader(),
                new Class<?>[] {HttpSession.class}, (p, m, a) -> switch (m.getName()) {
                    case "getAttribute" -> atributos.get(a[0]);
                    case "setAttribute" -> atributos.put((String) a[0], a[1]);
                    case "removeAttribute" -> atributos.remove(a[0]);
                    default -> null;
                });
    }

    private static Resultado ejecutar(String uri, String servletPath, UsuarioSesion usuario) throws Exception {
        Resultado r = new Resultado();
        HttpSession s = sesion(usuario);
        RequestDispatcher reenvio = (RequestDispatcher) Proxy.newProxyInstance(FiltroSeguridadTest.class.getClassLoader(),
                new Class<?>[] {RequestDispatcher.class}, (p, m, a) -> {
                    r.reenviado = true;
                    return null;
                });
        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(FiltroSeguridadTest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getServletPath" -> servletPath;
                    case "getPathInfo" -> null;
                    case "getMethod" -> "GET";
                    case "getSession" -> s;
                    case "getRequestDispatcher" -> reenvio;
                    default -> null;
                });
        HttpServletResponse res = (HttpServletResponse) Proxy.newProxyInstance(FiltroSeguridadTest.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "sendRedirect" -> r.redireccion = (String) a[0];
                        case "sendError", "setStatus" -> r.estado = (Integer) a[0];
                        default -> { }
                    }
                    return null;
                });
        FilterChain cadena = (rq, rs) -> r.cadena = true;
        new FiltroSeguridad() {
            void correr() throws Exception {
                doFilter(req, res, cadena);
            }
        }.correr();
        return r;
    }

    private static UsuarioSesion cliente() {
        return new UsuarioSesion(8, "mgonzales", "María Gonzales", PoliticaAcceso.CLIENTE, Set.of(), 1, false);
    }

    @ParameterizedTest(name = "sin sesión {0} no llega al servlet")
    @CsvSource({"/static/..;/admin/usuarios", "/static/%2e%2e/admin/usuarios", "/static/../admin/usuarios",
            "/login/..;/admin/usuarios", "/favicon.ico/..;/admin/auditoria", "/recuperar/..;/clientes"})
    void anonimoNoEvadeElFiltro(String uri) throws Exception {
        Resultado r = ejecutar(uri, uri.substring(uri.lastIndexOf("/admin") >= 0 ? uri.lastIndexOf("/admin")
                : uri.lastIndexOf("/clientes")), null);
        assertFalse(r.cadena, "La solicitud llegó al servlet privado");
    }

    @ParameterizedTest(name = "un cliente con {0} no llega al servlet")
    @CsvSource({"/cuenta/..;/admin/usuarios", "/cuenta/../admin/usuarios", "/logout/%2e%2e/admin/usuarios",
            "/sin-permiso/..;/admin/auditoria", "/static/..;/admin/usuarios"})
    void clienteNoEvadeElRol(String uri) throws Exception {
        Resultado r = ejecutar(uri, uri.substring(uri.lastIndexOf("/admin")), cliente());
        assertFalse(r.cadena, "La solicitud llegó al servlet de administrador");
    }

    @Test
    @DisplayName("Sin sesión, una ruta privada normal redirige al login")
    void anonimoRedirigido() throws Exception {
        Resultado r = ejecutar("/admin/usuarios", "/admin/usuarios", null);
        assertFalse(r.cadena);
        assertEquals("/login", r.redireccion);
    }

    @Test
    @DisplayName("Los recursos públicos legítimos siguen funcionando sin sesión")
    void publicoLegitimo() throws Exception {
        assertTrue(ejecutar("/static/css/app.css", "/static/css/app.css", null).cadena);
        assertTrue(ejecutar("/login", "/login", null).cadena);
    }

    @Test
    @DisplayName("Un cliente entra a su portal y recibe 403 en administración por la ruta normal")
    void clienteRutaNormal() throws Exception {
        assertTrue(ejecutar("/portal", "/portal", cliente()).cadena);
        Resultado r = ejecutar("/admin/usuarios", "/admin/usuarios", cliente());
        assertFalse(r.cadena);
        assertEquals(403, r.estado);
        assertTrue(r.reenviado);
        assertNull(r.redireccion);
    }
}
