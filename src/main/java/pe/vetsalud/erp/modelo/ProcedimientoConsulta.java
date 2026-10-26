package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/** Procedimiento realizado en la consulta (RF-16): el precio unitario es la tarifa copiada al momento del registro. */
public record ProcedimientoConsulta(long idProcConsulta, long idConsulta, int idServicio, String servicio,
                                    int idResponsable, String responsable, int cantidad, BigDecimal precioUnitario,
                                    String resultado) {

    public BigDecimal importe() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }
}
