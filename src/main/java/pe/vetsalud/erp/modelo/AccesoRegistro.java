package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Intento de ingreso registrado en acceso_log (RF-01): fecha, hora, equipo, usuario y resultado. */
public record AccesoRegistro(long idAcceso, LocalDateTime fechaHora, String usuarioIngresado, Integer idUsuario,
                             String nombre, String rol, String ip, boolean exitoso) {
}
