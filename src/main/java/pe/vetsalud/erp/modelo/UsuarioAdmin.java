package pe.vetsalud.erp.modelo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Usuario tal como lo administra P12 (RF-02, RF-03). Nunca incluye el hash de la contraseña (RNF-15).
 *
 * @param documentoCliente documento del cliente vinculado (solo rol CLIENTE, tabla cliente_usuario)
 * @param cliente          nombre del cliente vinculado (solo lectura)
 */
public record UsuarioAdmin(Integer idUsuario, String rol, String nombres, String apellidos, String usuario,
                           String correo, String colegiatura, int intentosFallidos, boolean bloqueado,
                           boolean activo, boolean debeCambiarClave, LocalDateTime ultimoAcceso,
                           LocalDateTime creadoEn, String documentoCliente, String cliente)
        implements Serializable {

    /** Datos capturados en el formulario (alta o edición). */
    public static UsuarioAdmin formulario(Integer idUsuario, String rol, String nombres, String apellidos,
                                          String usuario, String correo, String colegiatura,
                                          String documentoCliente) {
        return new UsuarioAdmin(idUsuario, rol, nombres, apellidos, usuario, correo, colegiatura, 0, false, true,
                false, null, null, documentoCliente, null);
    }

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }

    /** Estado para el distintivo de la lista: BLOQUEADO, INACTIVO o ACTIVO. */
    public String estado() {
        if (!activo) {
            return "INACTIVO";
        }
        return bloqueado ? "BLOQUEADO" : "ACTIVO";
    }
}
