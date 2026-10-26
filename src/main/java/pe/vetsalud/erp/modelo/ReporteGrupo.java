package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/**
 * Resultado agrupado genérico (ventas por día/cajero/medio/servicio/producto, procedimientos...).
 *
 * @param registros comprobantes, líneas o procedimientos contados
 * @param cantidad  unidades (null cuando la agrupación es por comprobante)
 * @param monto     importe en soles
 */
public record ReporteGrupo(String grupo, long registros, BigDecimal cantidad, BigDecimal monto) {
}
