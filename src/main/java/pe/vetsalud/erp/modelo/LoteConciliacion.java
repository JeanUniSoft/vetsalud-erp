package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** RF-22: saldo que resulta del kardex frente al stock registrado en el lote. */
public record LoteConciliacion(int idLote, String codigoLote, LocalDate fechaVencimiento, BigDecimal stockActual,
                               BigDecimal saldoKardex) {

    public boolean cuadra() {
        return stockActual.compareTo(saldoKardex) == 0;
    }
}
