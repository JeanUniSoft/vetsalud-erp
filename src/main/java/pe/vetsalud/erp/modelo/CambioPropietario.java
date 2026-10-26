package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Fila del historial de propietarios de una mascota (tabla mascota_propietario_hist). RF-08, RN-02. */
public record CambioPropietario(long idHist, int idMascota, int idClienteAnterior, String clienteAnterior,
                                int idClienteNuevo, String clienteNuevo, String motivo, String usuario,
                                LocalDateTime fechaHora) {
}
