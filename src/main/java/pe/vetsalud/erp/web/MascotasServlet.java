package pe.vetsalud.erp.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.modelo.Cliente;
import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.MascotaService;
import pe.vetsalud.erp.servicio.ReglaNegocioException;

/**
 * P03 · Mascotas (RF-07, RF-08, RN-02).
 * Rutas: /mascotas (búsqueda) · /mascotas/nueva?cliente= · /mascotas/editar?id= · /mascotas/ver?id=
 * · /mascotas/estado (POST) · /mascotas/propietario?id= (cambio de propietario auditado).
 * La fotografía se gestiona en {@link FotoMascotaServlet}. Toda escritura exige el permiso CLIENTE_GESTIONAR.
 */
@WebServlet({"/mascotas", "/mascotas/nueva", "/mascotas/editar", "/mascotas/ver", "/mascotas/estado",
        "/mascotas/propietario"})
public class MascotasServlet extends ControladorBase {

    static final String PERMISO = "CLIENTE_GESTIONAR";

    private MascotaService servicio;

    @Override
    public void init() {
        servicio = MascotaModulo.mascotas();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Integer id = MascotaModulo.idValido(texto(req, "id"));
        String ficha = id == null ? "/mascotas" : "/mascotas/ver?id=" + id;
        switch (req.getServletPath()) {
            case "/mascotas/nueva" -> {
                Integer idCliente = MascotaModulo.idValido(texto(req, "cliente"));
                if (idCliente == null) {
                    error(req, "Para registrar una mascota, busque al propietario y use «+ Mascota» en su ficha.");
                    redirigir(req, res, "/clientes");
                    return;
                }
                if (sinPermiso(req, res, "/clientes/ver?id=" + idCliente)) {
                    return;
                }
                mostrar(req, res, "mascotas/formulario", () -> {
                    req.setAttribute("cliente", MascotaModulo.clientes().obtener(idCliente));
                    catalogos(req);
                });
            }
            case "/mascotas/editar" -> {
                if (sinPermiso(req, res, ficha)) {
                    return;
                }
                mostrar(req, res, "mascotas/formulario", () -> {
                    Mascota m = servicio.obtener(obligatorio(id));
                    req.setAttribute("mascota", m);
                    req.setAttribute("cliente", MascotaModulo.clientes().obtener(m.idCliente()));
                    catalogos(req);
                });
            }
            case "/mascotas/ver" -> mostrar(req, res, "mascotas/ficha", () -> {
                Mascota m = servicio.obtener(obligatorio(id));
                req.setAttribute("mascota", m);
                req.setAttribute("propietarios", servicio.historialPropietarios(m.idMascota()));
                req.setAttribute("cambios", servicio.historialCambios(m.idMascota()));
            });
            case "/mascotas/propietario" -> {
                if (sinPermiso(req, res, ficha)) {
                    return;
                }
                mostrar(req, res, "mascotas/propietario", () -> {
                    Mascota m = servicio.obtener(obligatorio(id));
                    String q = texto(req, "q");
                    req.setAttribute("mascota", m);
                    req.setAttribute("q", q);
                    if (q != null) {
                        List<Cliente> candidatos = MascotaModulo.clientes().buscar(q).stream()
                                .filter(c -> c.activo() && !c.idCliente().equals(m.idCliente())).toList();
                        req.setAttribute("candidatos", candidatos);
                    }
                });
            }
            case "/mascotas/estado" -> redirigir(req, res, ficha);
            default -> mostrar(req, res, "mascotas/lista", () -> {
                req.setAttribute("q", texto(req, "q"));
                req.setAttribute("mascotas", servicio.buscar(texto(req, "q")));
            });
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        Integer id = MascotaModulo.idValido(texto(req, "id"));
        String ficha = id == null ? "/mascotas" : "/mascotas/ver?id=" + id;
        switch (req.getServletPath()) {
            case "/mascotas/nueva" -> {
                Integer idCliente = MascotaModulo.idValido(texto(req, "idCliente"));
                String fichaCliente = idCliente == null ? "/clientes" : "/clientes/ver?id=" + idCliente;
                String volver = idCliente == null ? "/clientes" : "/mascotas/nueva?cliente=" + idCliente;
                procesar(req, res, fichaCliente, volver, "Mascota registrada y vinculada al cliente.", () -> {
                    exigirPermiso(req, PERMISO);
                    servicio.registrar(datos(req, null, idCliente), u.idUsuario(), u.usuario());
                });
            }
            case "/mascotas/editar" -> procesar(req, res, ficha, id == null ? "/mascotas" : "/mascotas/editar?id=" + id,
                    "Datos de la mascota actualizados.", () -> {
                        exigirPermiso(req, PERMISO);
                        servicio.actualizar(datos(req, obligatorio(id), null), u.idUsuario(), u.usuario());
                    });
            case "/mascotas/estado" -> procesar(req, res, ficha, ficha, "Estado de la mascota actualizado.", () -> {
                exigirPermiso(req, PERMISO);
                servicio.cambiarEstado(obligatorio(id), texto(req, "estado"), u.idUsuario(), u.usuario());
            });
            case "/mascotas/propietario" -> {
                String q = texto(req, "q");
                String volver = id == null ? "/mascotas" : "/mascotas/propietario?id=" + id
                        + (q == null ? "" : "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
                procesar(req, res, ficha, volver, "Propietario actualizado; el cambio quedó en el historial.", () -> {
                    exigirPermiso(req, PERMISO);
                    servicio.cambiarPropietario(obligatorio(id), MascotaModulo.idValido(texto(req, "idClienteNuevo")),
                            texto(req, "motivo"), u.idUsuario(), u.usuario());
                });
            }
            default -> res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    /** Lee el formulario de mascota; los errores de formato se devuelven como mensajes para el usuario. */
    private Mascota datos(HttpServletRequest req, Integer idMascota, Integer idCliente) {
        LocalDate nacimiento;
        BigDecimal peso;
        try {
            nacimiento = fecha(req, "fechaNacimiento");
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException("La fecha de nacimiento no tiene un formato válido.");
        }
        try {
            peso = decimal(req, "pesoKg");
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException("El peso debe ser un número (por ejemplo 12.5).");
        }
        return Mascota.datos(idMascota, idCliente, MascotaModulo.idValido(texto(req, "idEspecie")),
                MascotaModulo.idValido(texto(req, "idRaza")), texto(req, "nombre"), texto(req, "sexo"), nacimiento,
                texto(req, "color"), peso, texto(req, "microchip"), marcado(req, "esterilizado"), texto(req, "alertas"));
    }

    private void catalogos(HttpServletRequest req) throws java.sql.SQLException {
        req.setAttribute("especies", servicio.especies());
        req.setAttribute("razas", servicio.razas());
        req.setAttribute("hoy", LocalDate.now());
    }

    /** Los formularios de escritura solo se muestran a quien tiene el permiso (defensa en profundidad: el POST también lo exige). */
    private boolean sinPermiso(HttpServletRequest req, HttpServletResponse res, String volver) throws IOException {
        if (usuario(req).puede(PERMISO)) {
            return false;
        }
        log.warn("Formulario de mascotas denegado: falta el permiso {}", PERMISO);
        error(req, "Su rol no tiene el permiso " + PERMISO + " para registrar o modificar mascotas.");
        redirigir(req, res, volver);
        return true;
    }

    private static int obligatorio(Integer id) {
        if (id == null) {
            throw new ReglaNegocioException("Falta el identificador de la mascota o no es válido.");
        }
        return id;
    }
}
