package pe.vetsalud.erp.modelo;

import java.io.Serializable;

/**
 * Clave temporal recién generada (RF-03). Se muestra UNA sola vez al administrador y nunca se guarda
 * en texto plano: en la BD solo queda su hash PBKDF2.
 */
public record AdminCredencial(int idUsuario, String usuario, String claveTemporal, String motivo)
        implements Serializable {
}
