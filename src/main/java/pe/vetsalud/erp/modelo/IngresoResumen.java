package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila del listado de ingresos registrados (RF-21). */
public record IngresoResumen(long idIngreso, String proveedor, String tipoDoc, String numDoc, LocalDate fecha,
                             String usuario, int lineas, BigDecimal unidades) {
}
