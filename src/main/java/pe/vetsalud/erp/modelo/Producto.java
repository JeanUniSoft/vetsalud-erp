package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Producto del catálogo de farmacia (RF-20) con el stock calculado a partir de sus lotes (RF-24).
 * El stock nunca se edita aquí: solo lo mueve el kardex (RN-06).
 *
 * @param stockDisponible stock vendible (lotes sin vencer o sin vencimiento)
 * @param stockTotal      stock físico de todos los lotes (incluye vencidos)
 */
public record Producto(Integer idProducto, Integer idCategoria, String categoria, String codigo, String nombre,
                       String unidad, BigDecimal precioVenta, BigDecimal stockMinimo, boolean requiereReceta,
                       boolean activo, BigDecimal stockDisponible, BigDecimal stockTotal,
                       int lotesPorVencer, int lotesVencidos) {

    /** Unidades permitidas (CHECK de la tabla producto). */
    public static final List<String> UNIDADES = List.of("UND", "CAJA", "FRASCO", "TABLETA", "AMPOLLA", "BOLSA", "KG");

    /** Datos capturados en el formulario (sin stock calculado). */
    public static Producto datos(Integer idProducto, Integer idCategoria, String codigo, String nombre, String unidad,
                                 BigDecimal precioVenta, BigDecimal stockMinimo, boolean requiereReceta,
                                 boolean activo) {
        return new Producto(idProducto, idCategoria, null, codigo, nombre, unidad, precioVenta, stockMinimo,
                requiereReceta, activo, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
    }

    /** RF-24: mismo criterio que la vista v_alerta_inventario (stock total en o bajo el mínimo). */
    public boolean bajoMinimo() {
        return activo && stockTotal.compareTo(stockMinimo) <= 0;
    }

    public boolean conAlerta() {
        return bajoMinimo() || lotesPorVencer > 0 || lotesVencidos > 0;
    }

    /** Solo el kilogramo admite cantidades con decimales. */
    public boolean admiteDecimales() {
        return "KG".equals(unidad);
    }

    public Producto conId(int id) {
        return new Producto(id, idCategoria, categoria, codigo, nombre, unidad, precioVenta, stockMinimo,
                requiereReceta, activo, stockDisponible, stockTotal, lotesPorVencer, lotesVencidos);
    }
}
