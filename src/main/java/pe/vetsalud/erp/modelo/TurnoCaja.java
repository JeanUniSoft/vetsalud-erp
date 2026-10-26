package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Turno de caja (RF-30). Un cajero solo tiene un turno ABIERTO a la vez (uq_turno_abierto, RN-10). */
public record TurnoCaja(int idTurno, int idCajero, String cajero, LocalDateTime apertura, BigDecimal montoInicial,
                        LocalDateTime cierre, BigDecimal efectivoEsperado, BigDecimal efectivoContado,
                        BigDecimal diferencia, String observacion, String estado) {

    public static final String ABIERTA = "ABIERTA";

    public boolean abierto() {
        return ABIERTA.equals(estado);
    }
}
