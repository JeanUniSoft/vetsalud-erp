package pe.vetsalud.erp.modelo;

/** Estado de un lote en el reporte de stock y vencimientos (RF-24, RF-34). */
public enum CondicionLote {
    VENCIDO,     // no se puede vender (RN-06)
    POR_VENCER,  // vence dentro de los días de alerta configurados
    STOCK_BAJO,  // el stock vendible del producto está en o bajo el mínimo
    NORMAL
}
