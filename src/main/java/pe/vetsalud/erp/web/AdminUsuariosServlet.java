package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import pe.vetsalud.erp.dao.jdbc.UsuarioAdminDaoJdbc;
import pe.vetsalud.erp.modelo.UsuarioAdmin;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.seguridad.ClaveHasher;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.AdminUsuarioService;
import pe.vetsalud.erp.servicio.GeneradorClaveTemporal;

/**
 * P12 · Usuarios (RF-02, RF-03): lista con filtros, alta, edición, clave temporal, desbloqueo y activación.
 * Rutas: /admin/usuarios · /admin/usuarios/nuevo · /admin/usuarios/editar?id= · POST /admin/usuarios/clave ·
 * POST /admin/usuarios/estado (accion=desbloquear|activar|desactivar).
 * La clave temporal viaja en la sesión solo hasta la siguiente página, que la muestra una vez y la borra.
 */
@WebServlet({"/admin/usuarios", "/admin/usuarios/nuevo", "/admin/usuarios/editar", "/admin/usuarios/clave",
        "/admin/usuarios/estado"})
public class AdminUsuariosServlet extends ControladorBase {

    static final String CREDENCIAL = "credencialTemporal";
    private static final String BORRADOR = "borradorUsuario";

    private AdminUsuarioService servicio;

    @Override
    public void init() {
        servicio = new AdminUsuarioService(new UsuarioAdminDaoJdbc(Db.get()), new ClaveHasher(),
                new GeneradorClaveTemporal());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        switch (req.getServletPath()) {
            case "/admin/usuarios/nuevo" -> mostrar(req, res, "admin/usuario-formulario", () -> {
                req.setAttribute("usuario", tomar(req, BORRADOR));
                req.setAttribute("nuevo", true);
                opciones(req);
            });
            case "/admin/usuarios/editar" -> mostrar(req, res, "admin/usuario-formulario", () -> {
                UsuarioAdmin borrador = (UsuarioAdmin) tomar(req, BORRADOR);
                Integer id = entero(req, "id");
                if (id == null) {
                    throw new IllegalArgumentException("Falta el identificador del usuario.");
                }
                UsuarioAdmin actual = servicio.obtener(id);
                req.setAttribute("actual", actual);
                req.setAttribute("usuario", borrador != null && id.equals(borrador.idUsuario()) ? borrador : actual);
                req.setAttribute("nuevo", false);
                req.setAttribute("credencial", tomar(req, CREDENCIAL));
                opciones(req);
            });
            case "/admin/usuarios/clave", "/admin/usuarios/estado" -> redirigir(req, res, "/admin/usuarios");
            default -> mostrar(req, res, "admin/usuarios", () -> {
                req.setAttribute("q", texto(req, "q"));
                req.setAttribute("rolFiltro", texto(req, "rol"));
                req.setAttribute("estadoFiltro", texto(req, "estado"));
                req.setAttribute("roles", servicio.roles());
                req.setAttribute("credencial", tomar(req, CREDENCIAL));
                req.setAttribute("usuarios", servicio.listar(texto(req, "q"), texto(req, "rol"), texto(req, "estado")));
            });
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        HttpSession sesion = req.getSession();
        switch (req.getServletPath()) {
            case "/admin/usuarios/nuevo" -> {
                UsuarioAdmin datos = datos(req, null);
                sesion.setAttribute(BORRADOR, datos);          // si falla, el formulario conserva lo escrito
                procesar(req, res, "/admin/usuarios", "/admin/usuarios/nuevo",
                        "Usuario creado. Entréguele la clave temporal: deberá cambiarla en su primer ingreso.", () -> {
                            exigirPermiso(req, AdminUsuarioService.PERMISO);
                            sesion.setAttribute(CREDENCIAL, servicio.crear(datos, u));
                            sesion.removeAttribute(BORRADOR);
                        });
            }
            case "/admin/usuarios/editar" -> {
                Integer id = entero(req, "id");
                UsuarioAdmin datos = datos(req, id);
                sesion.setAttribute(BORRADOR, datos);
                String volver = "/admin/usuarios/editar?id=" + id;
                procesar(req, res, volver, volver, "Datos del usuario actualizados.", () -> {
                    exigirPermiso(req, AdminUsuarioService.PERMISO);
                    servicio.actualizar(datos, u);
                    sesion.removeAttribute(BORRADOR);
                });
            }
            case "/admin/usuarios/clave" -> {
                int id = (int) idObligatorio(req, res, "id");
                String volver = destino(req, id);
                procesar(req, res, volver, volver,
                        "Clave temporal generada. La cuenta quedó desbloqueada y pedirá cambiarla al ingresar (RF-03).",
                        () -> {
                            exigirPermiso(req, AdminUsuarioService.PERMISO);
                            sesion.setAttribute(CREDENCIAL, servicio.restablecerClave(id, u));
                        });
            }
            case "/admin/usuarios/estado" -> {
                int id = (int) idObligatorio(req, res, "id");
                String accion = String.valueOf(texto(req, "accion"));
                String volver = destino(req, id);
                String mensaje = switch (accion) {
                    case "desbloquear" -> "Cuenta desbloqueada.";
                    case "activar" -> "Cuenta activada.";
                    default -> "Cuenta desactivada: ya no podrá ingresar al sistema.";
                };
                procesar(req, res, volver, volver, mensaje, () -> {
                    exigirPermiso(req, AdminUsuarioService.PERMISO);
                    switch (accion) {
                        case "desbloquear" -> servicio.desbloquear(id, u);
                        case "activar" -> servicio.cambiarEstado(id, true, u);
                        case "desactivar" -> servicio.cambiarEstado(id, false, u);
                        default -> throw new IllegalArgumentException("Acción no reconocida.");
                    }
                });
            }
            default -> redirigir(req, res, "/admin/usuarios");
        }
    }

    private UsuarioAdmin datos(HttpServletRequest req, Integer id) {
        return UsuarioAdmin.formulario(id, texto(req, "rol"), texto(req, "nombres"), texto(req, "apellidos"),
                texto(req, "usuario"), texto(req, "correo"), texto(req, "colegiatura"), texto(req, "documentoCliente"));
    }

    private void opciones(HttpServletRequest req) throws java.sql.SQLException {
        req.setAttribute("roles", servicio.roles());
        req.setAttribute("clientesLibres", servicio.clientesSinUsuario());
    }

    /** Destino tras una acción: la ficha del usuario o la lista (lista blanca, sin redirecciones abiertas). */
    private String destino(HttpServletRequest req, int id) {
        return "editar".equals(texto(req, "volver")) ? "/admin/usuarios/editar?id=" + id : "/admin/usuarios";
    }

    /** Lee y borra un atributo de sesión (se muestra una sola vez). */
    private static Object tomar(HttpServletRequest req, String nombre) {
        HttpSession s = req.getSession();
        Object valor = s.getAttribute(nombre);
        s.removeAttribute(nombre);
        return valor;
    }
}
