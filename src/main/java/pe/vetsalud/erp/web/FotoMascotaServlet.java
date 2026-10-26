package pe.vetsalud.erp.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.MascotaService;
import pe.vetsalud.erp.servicio.ReglaNegocioException;

/**
 * RF-08 · Fotografía de la mascota.
 * <ul>
 *   <li>POST /mascotas/foto?id=&amp;_csrf= : subida multipart (JPG/PNG, máx. 2 MB) con permiso CLIENTE_GESTIONAR.
 *       El token va también en la URL porque el filtro de seguridad lo lee antes de procesar el multipart.</li>
 *   <li>GET /mascotas/foto?id= : personal con acceso a /mascotas (lo verifica FiltroSeguridad).</li>
 *   <li>GET /portal/foto?id= : el cliente, solo si la mascota es suya (se verifica en el SQL).</li>
 * </ul>
 * Las fotos viven fuera del WAR y nunca se sirven como archivo estático.
 */
@WebServlet({"/mascotas/foto", "/portal/foto"})
@MultipartConfig(maxFileSize = MascotaService.MAX_FOTO_BYTES,
        maxRequestSize = MascotaService.MAX_FOTO_BYTES + 256 * 1024, fileSizeThreshold = 256 * 1024)
public class FotoMascotaServlet extends ControladorBase {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Integer id = MascotaModulo.idValido(texto(req, "id"));
        if (id == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        UsuarioSesion u = usuario(req);
        Optional<Mascota> mascota;
        try {
            if ("/portal/foto".equals(req.getServletPath())) {
                mascota = MascotaModulo.portal().mascota(u.idCliente(), id);
                if (mascota.isEmpty()) {
                    log.warn("Acceso denegado: el cliente {} pidió la foto de la mascota {} que no es suya", u.idCliente(), id);
                    res.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            } else {
                mascota = Optional.of(MascotaModulo.mascotas().obtener(id));
            }
        } catch (ReglaNegocioException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        } catch (SQLException e) {
            log.error("No se pudo leer la mascota {}: {}", id, e.getMessage(), e);
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        Optional<Path> archivo = MascotaModulo.fotos().ubicar(mascota.get().fotoRuta());
        if (archivo.isEmpty()) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path p = archivo.get();
        res.setContentType(p.getFileName().toString().endsWith(".png") ? "image/png" : "image/jpeg");
        res.setCharacterEncoding(null);   // binario: sin "charset" (el filtro lo fija en UTF-8 para las páginas)
        res.setHeader("Content-Disposition", "inline");
        res.setContentLengthLong(Files.size(p));
        Files.copy(p, res.getOutputStream());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        if (!"/mascotas/foto".equals(req.getServletPath())) {
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        UsuarioSesion u = usuario(req);
        Integer id = MascotaModulo.idValido(texto(req, "id"));
        String ficha = id == null ? "/mascotas" : "/mascotas/ver?id=" + id;
        procesar(req, res, ficha, ficha, "Fotografía actualizada.", () -> {
            exigirPermiso(req, MascotasServlet.PERMISO);
            if (id == null) {
                throw new ReglaNegocioException("Falta el identificador de la mascota.");
            }
            Part parte;
            try {
                parte = req.getPart("foto");
            } catch (IllegalStateException e) {
                // Tomcat corta la lectura al superar maxFileSize / maxRequestSize
                throw new ReglaNegocioException("La fotografía supera el tamaño máximo de 2 MB.");
            }
            if (parte == null || parte.getSize() == 0) {
                throw new ReglaNegocioException("Seleccione una fotografía en formato JPG o PNG.");
            }
            byte[] contenido;
            try (InputStream in = parte.getInputStream()) {
                contenido = in.readNBytes(MascotaService.MAX_FOTO_BYTES + 1);
            } finally {
                parte.delete();
            }
            MascotaModulo.mascotas().actualizarFoto(id, contenido, parte.getSubmittedFileName(), u.idUsuario(),
                    u.usuario());
        });
    }
}
