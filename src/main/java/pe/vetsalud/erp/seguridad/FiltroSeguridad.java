package pe.vetsalud.erp.seguridad;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.log.ContextoSolicitud;

/**
 * Filtro único de seguridad que se ejecuta en cada solicitud (patrón Intercepting Filter):
 * <ol>
 *   <li>Cabeceras de protección del navegador (clickjacking, sniffing, CSP).</li>
 *   <li>Autenticación: sin sesión válida se redirige al login (RF-01, RNF-05).</li>
 *   <li>Autorización por rol según {@link PoliticaAcceso} (RF-02).</li>
 *   <li>Token anti-CSRF obligatorio en todo POST.</li>
 *   <li>Contexto de log (usuario, rol, id de solicitud) para la trazabilidad (RNF-16).</li>
 * </ol>
 */
@WebFilter("/*")
public class FiltroSeguridad extends HttpFilter {

    public static final String ATRIBUTO_USUARIO = "sesion";
    public static final String ATRIBUTO_CSRF = "csrf";
    public static final String PARAMETRO_CSRF = "_csrf";

    private static final Logger LOG = LoggerFactory.getLogger(FiltroSeguridad.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        req.setCharacterEncoding("UTF-8");
        res.setCharacterEncoding("UTF-8");
        cabecerasDeSeguridad(res);

        // S13 (SEG-03, SEG-04): getRequestURI() conserva "..", ";" y "%2e" sin normalizar, pero el contenedor sí los
        // resuelve al elegir el servlet. Se rechazan esas formas y se decide con la ruta que realmente se va a ejecutar.
        if (uriManipulada(req.getRequestURI())) {
            LOG.warn("Solicitud rechazada por ruta manipulada: {}", req.getRequestURI());
            res.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String ruta = rutaCanonica(req);

        HttpSession sesion = req.getSession(true);
        asegurarTokenCsrf(sesion);

        if ("POST".equalsIgnoreCase(req.getMethod()) && !tokenCsrfValido(req, sesion)) {
            LOG.warn("Solicitud POST rechazada por token CSRF inválido en {}", ruta);
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Token de seguridad inválido. Recargue la página.");
            return;
        }

        if (PoliticaAcceso.esPublica(ruta)) {
            chain.doFilter(req, res);
            return;
        }

        UsuarioSesion usuario = (UsuarioSesion) sesion.getAttribute(ATRIBUTO_USUARIO);
        if (usuario == null) {
            res.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        if (usuario.debeCambiarClave() && !ruta.startsWith("/cuenta") && !ruta.startsWith("/logout")) {
            res.sendRedirect(req.getContextPath() + "/cuenta?obligatorio=1");
            return;
        }
        if (!usuario.accede(ruta)) {
            LOG.warn("Acceso denegado a {} para el rol {}", ruta, usuario.rol());
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            req.getRequestDispatcher("/WEB-INF/jsp/error/sin-permiso.jsp").forward(req, res);
            return;
        }

        // Los mensajes "flash" de la redirección anterior pasan a la solicitud actual
        Object flash = sesion.getAttribute("flash");
        if (flash != null) {
            req.setAttribute("flash", flash);
            sesion.removeAttribute("flash");
        }
        res.setHeader("Cache-Control", "no-store");
        try (ContextoSolicitud ctx = ContextoSolicitud.iniciar(usuario.usuario(), usuario.rol())) {
            req.setAttribute("idSolicitud", ctx.idSolicitud());
            chain.doFilter(req, res);
        }
    }

    /** Ruta resuelta por el contenedor (servlet + información adicional), no el texto enviado por el cliente. */
    static String rutaCanonica(HttpServletRequest req) {
        String ruta = StringUtils.defaultString(req.getServletPath()) + StringUtils.defaultString(req.getPathInfo());
        return ruta.isEmpty() || ruta.equals("/") ? "/panel" : ruta;
    }

    /** Recorridos de directorio, parámetros de ruta y sus formas codificadas: ninguna URL legítima del ERP los usa. */
    static boolean uriManipulada(String uri) {
        String u = StringUtils.defaultString(uri).toLowerCase(java.util.Locale.ROOT);
        return u.contains("..") || u.contains(";") || u.contains("\\")
                || u.contains("%2e") || u.contains("%2f") || u.contains("%5c") || u.contains("%3b") || u.contains("%00");
    }

    private static void cabecerasDeSeguridad(HttpServletResponse res) {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "same-origin");
        res.setHeader("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
                        + "form-action 'self'; frame-ancestors 'none'");
    }

    private static void asegurarTokenCsrf(HttpSession sesion) {
        if (sesion.getAttribute(ATRIBUTO_CSRF) == null) {
            byte[] bytes = new byte[24];
            ALEATORIO.nextBytes(bytes);
            sesion.setAttribute(ATRIBUTO_CSRF, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        }
    }

    private static boolean tokenCsrfValido(HttpServletRequest req, HttpSession sesion) {
        String esperado = (String) sesion.getAttribute(ATRIBUTO_CSRF);
        String recibido = req.getParameter(PARAMETRO_CSRF);
        return esperado != null && java.security.MessageDigest.isEqual(
                esperado.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                StringUtils.defaultString(recibido).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
