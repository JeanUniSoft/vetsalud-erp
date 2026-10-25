package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.AlertaDao;
import pe.vetsalud.erp.dao.UsuarioDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.Usuario;
import pe.vetsalud.erp.seguridad.ClaveHasher;
import pe.vetsalud.erp.seguridad.PoliticaClave;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * Caso de uso UC-01 Autenticarse (RF-01 a RF-03).
 * <ul>
 *   <li>Mensaje genérico ante usuario o clave incorrectos: no revela qué cuentas existen.</li>
 *   <li>Bloqueo tras N intentos fallidos (parámetro MAX_INTENTOS_LOGIN).</li>
 *   <li>Cada intento queda en acceso_log con fecha, hora, equipo (IP) y resultado.</li>
 * </ul>
 * Depende de interfaces (UsuarioDao, AlertaDao), no de JDBC: principio de inversión de dependencias.
 */
public class AutenticacionService {

    public static final String MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos.";

    private final UsuarioDao usuarios;
    private final AlertaDao alertas;
    private final ClaveHasher hasher;
    private final ParametroService parametros;

    public AutenticacionService(UsuarioDao usuarios, AlertaDao alertas, ClaveHasher hasher, ParametroService parametros) {
        this.usuarios = Preconditions.checkNotNull(usuarios);
        this.alertas = Preconditions.checkNotNull(alertas);
        this.hasher = Preconditions.checkNotNull(hasher);
        this.parametros = Preconditions.checkNotNull(parametros);
    }

    public UsuarioSesion autenticar(String usuario, String clave, String ip) throws SQLException {
        if (StringUtils.isAnyBlank(usuario, clave)) {
            throw new ReglaNegocioException("Ingrese su usuario y contraseña.");
        }
        Optional<Usuario> encontrado = usuarios.buscarPorUsuario(usuario.trim());
        if (encontrado.isEmpty()) {
            usuarios.registrarIntento(null, usuario.trim(), ip, false);
            Auditoria.registrar("LOGIN_FALLIDO", "usuario", "usuario=" + usuario.trim() + " motivo=inexistente");
            throw new ReglaNegocioException(MENSAJE_CREDENCIALES);
        }
        Usuario u = encontrado.get();
        if (!u.activo()) {
            usuarios.registrarIntento(u.idUsuario(), usuario, ip, false);
            throw new ReglaNegocioException("La cuenta está desactivada. Comuníquese con el administrador.");
        }
        if (u.bloqueado()) {
            usuarios.registrarIntento(u.idUsuario(), usuario, ip, false);
            throw new ReglaNegocioException(
                    "La cuenta está bloqueada por intentos fallidos. Solicite el restablecimiento al administrador.");
        }
        if (!hasher.verificar(clave.toCharArray(), u.claveHash())) {
            int maximo = parametros.entero(ParametroService.MAX_INTENTOS_LOGIN, 5);
            int intentos = u.intentosFallidos() + 1;
            boolean bloquear = intentos >= maximo;
            usuarios.registrarFallo(u.idUsuario(), intentos, bloquear);
            usuarios.registrarIntento(u.idUsuario(), usuario, ip, false);
            Auditoria.registrar(bloquear ? "CUENTA_BLOQUEADA" : "LOGIN_FALLIDO", "usuario",
                    "usuario=" + u.usuario() + " intentos=" + intentos);
            // Mensaje genérico: indicar "intentos restantes" revelaría que la cuenta existe
            throw new ReglaNegocioException(MENSAJE_CREDENCIALES);
        }
        usuarios.registrarIngresoExitoso(u.idUsuario());
        usuarios.registrarIntento(u.idUsuario(), usuario, ip, true);
        Integer idCliente = usuarios.clienteVinculado(u.idUsuario()).orElse(null);
        Auditoria.registrar("LOGIN", "usuario", "usuario=" + u.usuario() + " rol=" + u.rol());
        return new UsuarioSesion(u.idUsuario(), u.usuario(), u.nombreCompleto(), u.rol(),
                usuarios.permisosDeRol(u.rol()), idCliente, u.debeCambiarClave());
    }

    public void cambiarClave(UsuarioSesion sesion, String actual, String nueva, String confirmacion) throws SQLException {
        Usuario u = usuarios.buscarPorId(sesion.idUsuario())
                .orElseThrow(() -> new ReglaNegocioException("La cuenta ya no existe."));
        if (actual == null || !hasher.verificar(actual.toCharArray(), u.claveHash())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta.");
        }
        if (!StringUtils.equals(nueva, confirmacion)) {
            throw new ReglaNegocioException("La confirmación no coincide con la nueva contraseña.");
        }
        if (StringUtils.equals(actual, nueva)) {
            throw new ReglaNegocioException("La nueva contraseña debe ser distinta de la actual.");
        }
        List<String> faltas = PoliticaClave.incumplimientos(nueva, u.usuario());
        if (!faltas.isEmpty()) {
            throw new ReglaNegocioException("La nueva contraseña debe " + Joiner.on(", ").join(faltas) + ".");
        }
        usuarios.cambiarClave(u.idUsuario(), hasher.hashear(nueva.toCharArray()), false);
        Auditoria.registrar("CLAVE_CAMBIADA", "usuario", "usuario=" + u.usuario());
    }

    /**
     * Restablecimiento controlado (RF-03): no se envía la clave por correo; se crea una alerta para el
     * administrador, que asigna una clave temporal desde P12. La respuesta es la misma exista o no la cuenta.
     */
    public void solicitarRecuperacion(String usuarioOCorreo, String ip) throws SQLException {
        if (StringUtils.isBlank(usuarioOCorreo)) {
            throw new ReglaNegocioException("Ingrese su usuario o correo.");
        }
        Optional<Usuario> u = usuarios.buscarPorUsuarioOCorreo(usuarioOCorreo.trim());
        if (u.isPresent()) {
            alertas.crear("ADMINISTRADOR", "OTRO", "Solicitud de restablecimiento de contraseña de "
                    + u.get().usuario() + " (" + u.get().rol() + ") desde " + StringUtils.defaultString(ip, "equipo desconocido"));
            Auditoria.registrar("RECUPERACION_SOLICITADA", "usuario", "usuario=" + u.get().usuario());
        }
    }
}
