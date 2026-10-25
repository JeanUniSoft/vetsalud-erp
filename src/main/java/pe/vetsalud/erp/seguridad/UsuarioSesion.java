package pe.vetsalud.erp.seguridad;

import java.io.Serializable;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * Datos del usuario autenticado que viajan en la sesión HTTP.
 * Nunca contiene la contraseña ni su hash (RNF-15).
 *
 * @param idCliente solo para el rol CLIENTE (portal, P11); null para el personal.
 */
public record UsuarioSesion(int idUsuario, String usuario, String nombreCompleto, String rol,
                            Set<String> permisos, Integer idCliente, boolean debeCambiarClave)
        implements Serializable {

    public UsuarioSesion {
        permisos = ImmutableSet.copyOf(permisos);
    }

    /** ¿El rol puede entrar a la ruta? (menús y filtro de seguridad, RF-02). */
    public boolean accede(String ruta) {
        return PoliticaAcceso.permite(rol, ruta);
    }

    /** ¿Tiene el permiso de acción (p. ej. CONSULTA_FIRMAR)? */
    public boolean puede(String permiso) {
        return permisos.contains(permiso);
    }

    public boolean esCliente() {
        return PoliticaAcceso.CLIENTE.equals(rol);
    }

    public String getIniciales() {
        String[] partes = nombreCompleto.trim().split("\\s+");
        return partes.length > 1
                ? ("" + partes[0].charAt(0) + partes[1].charAt(0)).toUpperCase()
                : nombreCompleto.substring(0, 1).toUpperCase();
    }

    public UsuarioSesion conClaveCambiada() {
        return new UsuarioSesion(idUsuario, usuario, nombreCompleto, rol, permisos, idCliente, false);
    }
}
