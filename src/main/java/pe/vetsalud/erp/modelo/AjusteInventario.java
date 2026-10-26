package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/**
 * Ajuste de stock de un lote (RF-25): tipo AJUSTE_ENTRADA o AJUSTE_SALIDA, motivo y evidencia
 * (n.° de acta u observación). Se guarda en el kardex como un movimiento más.
 */
public record AjusteInventario(Integer idLote, String tipo, BigDecimal cantidad, String motivo, String evidencia) {

    /** Texto que queda en movimiento_inventario.motivo. */
    public String textoMotivo() {
        return motivo + " · Evidencia: " + evidencia;
    }
}
