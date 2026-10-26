package pe.vetsalud.erp.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.persistencia.ErroresBD;
import pe.vetsalud.erp.seguridad.FiltroSeguridad;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.ReglaNegocioException;

/**
 * Controlador base del patrón MVC: los servlets reciben la solicitud, delegan en un servicio
 * (modelo) y eligen la vista JSP. Centraliza la lectura segura de parámetros (Apache Commons),
 * los mensajes al usuario y la traducción de errores, para que ningún servlet repita ese código.
 */
public abstract class ControladorBase extends HttpServlet {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Acción de un formulario POST que puede fallar por reglas de negocio o de BD. */
    @FunctionalInterface
    protected interface Accion {
        void ejecutar() throws SQLException, IOException, ServletException;
    }

    // ------------------------------------------------------------------ sesión y permisos

    protected UsuarioSesion usuario(HttpServletRequest req) {
        return (UsuarioSesion) req.getSession().getAttribute(FiltroSeguridad.ATRIBUTO_USUARIO);
    }

    /** Verifica un permiso de acción (RF-02); si falta, lanza una regla de negocio. */
    protected void exigirPermiso(HttpServletRequest req, String permiso) {
        if (!usuario(req).puede(permiso)) {
            log.warn("Acción denegada: falta el permiso {}", permiso);
            throw new ReglaNegocioException("Su rol no tiene el permiso " + permiso + " para esta acción.");
        }
    }

    // ------------------------------------------------------------------ vistas y redirecciones

    protected void vista(HttpServletRequest req, HttpServletResponse res, String nombre)
            throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/jsp/" + nombre + ".jsp").forward(req, res);
    }

    protected void redirigir(HttpServletRequest req, HttpServletResponse res, String ruta) throws IOException {
        res.sendRedirect(req.getContextPath() + ruta);
    }

    protected void exito(HttpServletRequest req, String mensaje) {
        req.getSession().setAttribute("flash", new Flash("exito", mensaje));
    }

    protected void error(HttpServletRequest req, String mensaje) {
        req.getSession().setAttribute("flash", new Flash("error", mensaje));
    }

    /**
     * Ejecuta una acción de formulario con el patrón Post/Redirect/Get: si tiene éxito muestra
     * el mensaje y redirige a {@code destinoExito}; si viola una regla, redirige a {@code destinoError}
     * con el motivo. Los errores inesperados se registran con traza en errores.log (RNF-16).
     */
    protected void procesar(HttpServletRequest req, HttpServletResponse res, String destinoExito,
                            String destinoError, String mensajeExito, Accion accion)
            throws IOException, ServletException {
        try {
            accion.ejecutar();
            if (mensajeExito != null) {
                exito(req, mensajeExito);
            }
            redirigir(req, res, destinoExito);
        } catch (ReglaNegocioException | IllegalArgumentException e) {
            log.info("Operación rechazada: {}", e.getMessage());
            error(req, e.getMessage());
            redirigir(req, res, destinoError);
        } catch (SQLException e) {
            String mensaje = ErroresBD.mensaje(e);
            log.warn("Error de base de datos ({}): {}", e.getSQLState(), e.getMessage(), e);
            error(req, mensaje);
            redirigir(req, res, destinoError);
        }
    }

    /** Para GET: carga datos y muestra la vista; un error de BD muestra la página de error. */
    protected void mostrar(HttpServletRequest req, HttpServletResponse res, String nombreVista, Accion carga)
            throws IOException, ServletException {
        try {
            carga.ejecutar();
            vista(req, res, nombreVista);
        } catch (ReglaNegocioException | IllegalArgumentException e) {
            req.setAttribute("flash", new Flash("error", e.getMessage()));
            vista(req, res, nombreVista);
        } catch (SQLException e) {
            log.error("No se pudo cargar {}: {}", nombreVista, e.getMessage(), e);
            req.setAttribute("mensajeError", ErroresBD.mensaje(e));
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            vista(req, res, "error/error");
        }
    }

    // ------------------------------------------------------------------ lectura de parámetros

    /** Texto recortado; vacío → null (Apache Commons StringUtils). */
    protected String texto(HttpServletRequest req, String nombre) {
        return StringUtils.trimToNull(req.getParameter(nombre));
    }

    protected boolean marcado(HttpServletRequest req, String nombre) {
        String v = req.getParameter(nombre);
        return v != null && (v.equals("on") || v.equals("true") || v.equals("1"));
    }

    protected Integer entero(HttpServletRequest req, String nombre) {
        String v = texto(req, nombre);
        if (v == null) {
            return null;
        }
        if (!NumberUtils.isDigits(v)) {
            throw new IllegalArgumentException("El campo " + nombre + " debe ser un número entero.");
        }
        return Integer.valueOf(v);
    }

    protected Long largo(HttpServletRequest req, String nombre) {
        Integer v = entero(req, nombre);
        return v == null ? null : v.longValue();
    }

    protected BigDecimal decimal(HttpServletRequest req, String nombre) {
        String v = texto(req, nombre);
        if (v == null) {
            return null;
        }
        v = v.replace(",", ".");
        if (!NumberUtils.isCreatable(v)) {
            throw new IllegalArgumentException("El campo " + nombre + " debe ser un número.");
        }
        return new BigDecimal(v);
    }

    /** Fecha en formato del input HTML (aaaa-mm-dd). */
    protected LocalDate fecha(HttpServletRequest req, String nombre) {
        String v = texto(req, nombre);
        try {
            return v == null ? null : LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha no válida en " + nombre + ".");
        }
    }

    /** Fecha y hora de un input datetime-local (aaaa-mm-ddThh:mm). */
    protected LocalDateTime fechaHora(HttpServletRequest req, String nombre) {
        String v = texto(req, nombre);
        try {
            return v == null ? null : LocalDateTime.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha y hora no válidas en " + nombre + ".");
        }
    }

    protected LocalTime hora(HttpServletRequest req, String nombre) {
        String v = texto(req, nombre);
        try {
            return v == null ? null : LocalTime.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Hora no válida en " + nombre + ".");
        }
    }

    /** Parámetro obligatorio de identificación (id en la URL); si falta, 404. */
    protected long idObligatorio(HttpServletRequest req, HttpServletResponse res, String nombre) throws IOException {
        String v = texto(req, nombre);
        if (v == null || !NumberUtils.isDigits(v)) {
            throw new IllegalArgumentException("Falta el identificador " + nombre + ".");
        }
        return Long.parseLong(v);
    }
}
