package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Usuario del sistema (tabla usuario). El hash solo se usa para verificar; nunca se muestra. */
public record Usuario(int idUsuario, String rol, String nombres, String apellidos, String usuario,
                      String correo, String claveHash, String colegiatura, int intentosFallidos,
                      boolean bloqueado, boolean activo, boolean debeCambiarClave,
                      LocalDateTime ultimoAcceso) {

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}
