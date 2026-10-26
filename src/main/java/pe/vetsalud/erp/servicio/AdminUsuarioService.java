package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.UsuarioAdminDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.AdminCredencial;
import pe.vetsalud.erp.modelo.Opcion;
import pe.vetsalud.erp.modelo.UsuarioAdmin;
import pe.vetsalud.erp.seguridad.ClaveHasher;
import pe.vetsalud.erp.seguridad.PoliticaAcceso;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * UC-15 Administrar usuarios (P12: RF-02, RF-03).
 * <ul>
 *   <li>Alta con clave temporal aleatoria (hash PBKDF2, debe cambiarla al ingresar).</li>
 *   <li>Usuario y correo únicos; colegiatura CMVP obligatoria para veterinarios; rol CLIENTE vinculado a
 *       un cliente existente (un usuario por cliente).</li>
 *   <li>Restablecimiento controlado, desbloqueo y activación; el administrador no puede desactivarse ni
 *       cambiar su propio rol (evita quedarse sin administrador).</li>
 * </ul>
 * Cada acción exige el permiso USUARIO_ADMINISTRAR y queda en el log de auditoría y en la tabla auditoria.
 */
public class AdminUsuarioService {

    public static final String PERMISO = "USUARIO_ADMINISTRAR";
    public static final Set<String> ESTADOS = Set.of("ACTIVOS", "INACTIVOS", "BLOQUEADOS");
    public static final int LIMITE_SUGERENCIAS = 200;

    private static final Pattern USUARIO = Pattern.compile("^[a-z0-9._]{4,40}$");
    private static final Pattern COLEGIATURA = Pattern.compile("^[0-9]{4,6}$");

    private final UsuarioAdminDao dao;
    private final ClaveHasher hasher;
    private final GeneradorClaveTemporal generador;

    public AdminUsuarioService(UsuarioAdminDao dao, ClaveHasher hasher, GeneradorClaveTemporal generador) {
        this.dao = Preconditions.checkNotNull(dao);
        this.hasher = Preconditions.checkNotNull(hasher);
        this.generador = Preconditions.checkNotNull(generador);
    }

    public List<UsuarioAdmin> listar(String texto, String rol, String estado) throws SQLException {
        String e = StringUtils.upperCase(StringUtils.trimToNull(estado));
        if (e != null && !ESTADOS.contains(e)) {
            throw new ReglaNegocioException("El filtro de estado no es válido.");
        }
        String r = StringUtils.trimToNull(rol);
        if (r != null && !dao.roles().contains(r)) {
            throw new ReglaNegocioException("El rol elegido no existe.");
        }
        return dao.listar(StringUtils.trimToNull(texto), r, e);
    }

    public UsuarioAdmin obtener(int idUsuario) throws SQLException {
        return dao.buscarPorId(idUsuario).orElseThrow(() -> new ReglaNegocioException("El usuario no existe."));
    }

    public List<String> roles() throws SQLException {
        return dao.roles();
    }

    public List<Opcion> clientesSinUsuario() throws SQLException {
        return dao.clientesSinUsuario(LIMITE_SUGERENCIAS);
    }

    /** Alta de usuario: devuelve la clave temporal para mostrarla UNA vez (RF-03). */
    public AdminCredencial crear(UsuarioAdmin datos, UsuarioSesion actor) throws SQLException {
        exigirPermiso(actor);
        UsuarioAdmin u = normalizarYValidar(datos);
        validarUnicos(u, null);
        Integer idCliente = clienteVinculado(u, null);
        String clave = generador.generar(u.usuario());
        int id = dao.insertar(u, hasher.hashear(clave.toCharArray()), idCliente, actor.idUsuario());
        Auditoria.registrar("USUARIO_CREAR", "usuario", "id=" + id + " usuario=" + u.usuario() + " rol=" + u.rol()
                + " por=" + actor.usuario());
        return new AdminCredencial(id, u.usuario(), clave, "Cuenta creada");
    }

    public void actualizar(UsuarioAdmin datos, UsuarioSesion actor) throws SQLException {
        exigirPermiso(actor);
        Preconditions.checkArgument(datos.idUsuario() != null, "Falta el identificador del usuario.");
        UsuarioAdmin actual = obtener(datos.idUsuario());
        UsuarioAdmin u = normalizarYValidar(datos);
        if (actual.idUsuario() == actor.idUsuario() && !u.rol().equals(actual.rol())) {
            throw new ReglaNegocioException("No puede cambiar su propio rol: otro administrador debe hacerlo.");
        }
        boolean eraCliente = PoliticaAcceso.CLIENTE.equals(actual.rol());
        boolean esCliente = PoliticaAcceso.CLIENTE.equals(u.rol());
        if (eraCliente != esCliente) {
            throw new ReglaNegocioException("Una cuenta del portal de clientes no puede convertirse en cuenta del "
                    + "personal (ni al revés). Cree una cuenta nueva.");
        }
        validarUnicos(u, u.idUsuario());
        Integer idCliente = clienteVinculado(u, u.idUsuario());
        dao.actualizar(u, idCliente, actor.idUsuario());
        Auditoria.registrar("USUARIO_ACTUALIZAR", "usuario", "id=" + u.idUsuario() + " rol=" + u.rol()
                + " por=" + actor.usuario());
    }

    /** RF-03: clave temporal nueva, obliga a cambiarla al ingresar y desbloquea la cuenta. */
    public AdminCredencial restablecerClave(int idUsuario, UsuarioSesion actor) throws SQLException {
        exigirPermiso(actor);
        if (idUsuario == actor.idUsuario()) {
            throw new ReglaNegocioException("Para su propia cuenta use «Mi cuenta» y cambie la contraseña con la actual.");
        }
        UsuarioAdmin u = obtener(idUsuario);
        if (!u.activo()) {
            throw new ReglaNegocioException("La cuenta está desactivada: actívela antes de asignar una clave temporal.");
        }
        String clave = generador.generar(u.usuario());
        dao.restablecerClave(idUsuario, hasher.hashear(clave.toCharArray()), actor.idUsuario());
        Auditoria.registrar("CLAVE_RESTABLECIDA", "usuario", "id=" + idUsuario + " usuario=" + u.usuario()
                + " por=" + actor.usuario());
        return new AdminCredencial(idUsuario, u.usuario(), clave, "Clave restablecida");
    }

    public void desbloquear(int idUsuario, UsuarioSesion actor) throws SQLException {
        exigirPermiso(actor);
        UsuarioAdmin u = obtener(idUsuario);
        if (!u.bloqueado()) {
            throw new ReglaNegocioException("La cuenta de " + u.usuario() + " no está bloqueada.");
        }
        dao.desbloquear(idUsuario, actor.idUsuario());
        Auditoria.registrar("CUENTA_DESBLOQUEADA", "usuario", "id=" + idUsuario + " por=" + actor.usuario());
    }

    public void cambiarEstado(int idUsuario, boolean activo, UsuarioSesion actor) throws SQLException {
        exigirPermiso(actor);
        if (idUsuario == actor.idUsuario() && !activo) {
            throw new ReglaNegocioException("No puede desactivar su propia cuenta.");
        }
        UsuarioAdmin u = obtener(idUsuario);
        if (u.activo() == activo) {
            throw new ReglaNegocioException("La cuenta de " + u.usuario() + " ya está " + (activo ? "activa." : "inactiva."));
        }
        dao.cambiarEstado(idUsuario, activo, actor.idUsuario());
        Auditoria.registrar(activo ? "USUARIO_ACTIVAR" : "USUARIO_DESACTIVAR", "usuario", "id=" + idUsuario
                + " por=" + actor.usuario());
    }

    // ------------------------------------------------------------------ reglas

    private static void exigirPermiso(UsuarioSesion actor) {
        if (actor == null || !actor.puede(PERMISO)) {
            throw new ReglaNegocioException("Su rol no tiene el permiso " + PERMISO + " para esta acción.");
        }
    }

    /** Limpia, normaliza y valida los datos del formulario. */
    UsuarioAdmin normalizarYValidar(UsuarioAdmin d) throws SQLException {
        String nombres = WordUtils.capitalizeFully(StringUtils.normalizeSpace(Validaciones.requerido(d.nombres(), "Nombres")));
        String apellidos = WordUtils.capitalizeFully(StringUtils.normalizeSpace(Validaciones.requerido(d.apellidos(), "Apellidos")));
        Validaciones.longitudMaxima(nombres, 80, "Nombres");
        Validaciones.longitudMaxima(apellidos, 80, "Apellidos");
        String usuario = StringUtils.lowerCase(Validaciones.requerido(d.usuario(), "Usuario"));
        if (!USUARIO.matcher(usuario).matches()) {
            throw new ReglaNegocioException("El usuario debe tener de 4 a 40 caracteres: letras minúsculas, números, punto o guion bajo.");
        }
        String correo = StringUtils.lowerCase(Validaciones.requerido(d.correo(), "Correo"));
        Validaciones.correo(correo);
        Validaciones.longitudMaxima(correo, 120, "Correo");
        String rol = Validaciones.requerido(d.rol(), "Rol");
        if (!dao.roles().contains(rol)) {
            throw new ReglaNegocioException("El rol elegido no existe o está inactivo.");
        }
        String colegiatura = StringUtils.deleteWhitespace(StringUtils.trimToNull(d.colegiatura()));
        if (PoliticaAcceso.VETERINARIO.equals(rol)) {
            if (StringUtils.isEmpty(colegiatura)) {
                throw new ReglaNegocioException("La colegiatura CMVP es obligatoria para el rol Veterinario.");
            }
            if (!COLEGIATURA.matcher(colegiatura).matches()) {
                throw new ReglaNegocioException("La colegiatura CMVP debe tener entre 4 y 6 dígitos.");
            }
        } else {
            colegiatura = null;                      // solo los médicos veterinarios la registran
        }
        String documento = PoliticaAcceso.CLIENTE.equals(rol)
                ? StringUtils.upperCase(StringUtils.deleteWhitespace(StringUtils.trimToNull(d.documentoCliente()))) : null;
        return new UsuarioAdmin(d.idUsuario(), rol, nombres, apellidos, usuario, correo, colegiatura,
                d.intentosFallidos(), d.bloqueado(), d.activo(), d.debeCambiarClave(), d.ultimoAcceso(),
                d.creadoEn(), documento, null);
    }

    private void validarUnicos(UsuarioAdmin u, Integer exceptoId) throws SQLException {
        if (dao.existeUsuario(u.usuario(), exceptoId)) {
            throw new ReglaNegocioException("El usuario «" + u.usuario() + "» ya existe.");
        }
        if (dao.existeCorreo(u.correo(), exceptoId)) {
            throw new ReglaNegocioException("El correo " + u.correo() + " ya pertenece a otro usuario.");
        }
    }

    /** Rol CLIENTE: el documento debe corresponder a un cliente activo sin otro usuario del portal. */
    private Integer clienteVinculado(UsuarioAdmin u, Integer idUsuario) throws SQLException {
        if (!PoliticaAcceso.CLIENTE.equals(u.rol())) {
            return null;
        }
        if (u.documentoCliente() == null) {
            throw new ReglaNegocioException("Para el rol Cliente indique el documento del cliente que usará el portal.");
        }
        Optional<Opcion> cliente = dao.clientePorDocumento(u.documentoCliente());
        if (cliente.isEmpty()) {
            throw new ReglaNegocioException("No existe un cliente activo con el documento " + u.documentoCliente() + ".");
        }
        int idCliente = (int) cliente.get().id();
        if (dao.clienteTieneUsuario(idCliente, idUsuario)) {
            throw new ReglaNegocioException("El cliente " + cliente.get().etiqueta() + " ya tiene un usuario del portal.");
        }
        return idCliente;
    }
}
