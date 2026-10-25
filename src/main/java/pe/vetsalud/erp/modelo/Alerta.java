package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Alerta interna dirigida a un rol (RF-38). atendidaPor/atendidaEn son null mientras está pendiente. */
public record Alerta(long idAlerta, String rolDestino, String tipo, String mensaje, LocalDateTime creadaEn,
                     String atendidaPor, LocalDateTime atendidaEn) {

    public boolean pendiente() {
        return atendidaEn == null;
    }
}
