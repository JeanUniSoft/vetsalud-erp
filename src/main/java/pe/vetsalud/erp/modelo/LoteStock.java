package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Fila de la vista v_stock_lote: un lote con los datos de su producto. */
public record LoteStock(
        String codigo,
        String producto,
        String categoria,
        String unidad,
        String codigoLote,
        LocalDate vencimiento,          // null si el producto no maneja vencimiento
        BigDecimal stock,
        BigDecimal stockMinimo,
        BigDecimal stockDisponibleProducto,
        BigDecimal costoUnitario) {

    public Long diasParaVencer(LocalDate hoy) {
        return vencimiento == null ? null : ChronoUnit.DAYS.between(hoy, vencimiento);
    }

    public BigDecimal valorizado() {
        return stock.multiply(costoUnitario);
    }

    /** Reglas RN-06 y RF-24: primero vencimiento, luego stock mínimo del producto. */
    public CondicionLote condicion(LocalDate hoy, int diasAlerta) {
        if (vencimiento != null && vencimiento.isBefore(hoy)) {
            return CondicionLote.VENCIDO;
        }
        if (vencimiento != null && !vencimiento.isAfter(hoy.plusDays(diasAlerta))) {
            return CondicionLote.POR_VENCER;
        }
        if (stockDisponibleProducto.compareTo(stockMinimo) <= 0) {
            return CondicionLote.STOCK_BAJO;
        }
        return CondicionLote.NORMAL;
    }
}
