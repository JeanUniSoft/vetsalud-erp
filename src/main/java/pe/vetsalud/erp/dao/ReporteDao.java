package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import pe.vetsalud.erp.modelo.LoteStock;
import pe.vetsalud.erp.modelo.ReporteAtenciones;
import pe.vetsalud.erp.modelo.ReporteAusencia;
import pe.vetsalud.erp.modelo.ReporteCitas;
import pe.vetsalud.erp.modelo.ReporteClinicaResumen;
import pe.vetsalud.erp.modelo.ReporteGrupo;
import pe.vetsalud.erp.modelo.ReporteKardex;
import pe.vetsalud.erp.modelo.ReporteRotacion;
import pe.vetsalud.erp.modelo.ReporteVentaAnulada;
import pe.vetsalud.erp.modelo.ReporteVentasResumen;

/**
 * Consultas de solo lectura de P10 · Reportes (RF-32 a RF-35).
 * Los periodos son [desde, hastaExclusivo): así se aprovechan los índices por fecha (RNF-01).
 * Las agrupaciones son claves de una lista blanca (dia, mes, cajero...); nunca SQL del usuario.
 */
public interface ReporteDao {

    /** Ventas CONFIRMADAS agrupadas: dia, mes, cajero, medio, servicio o producto (RF-33). */
    List<ReporteGrupo> ventasAgrupadas(String agrupacion, LocalDate desde, LocalDate hastaExclusivo) throws SQLException;

    ReporteVentasResumen resumenVentas(LocalDate desde, LocalDate hastaExclusivo) throws SQLException;

    List<ReporteVentaAnulada> ventasAnuladas(LocalDate desde, LocalDate hastaExclusivo) throws SQLException;

    /** Stock por lote (vista v_stock_lote, conexión de solo lectura) (RF-34). */
    List<LoteStock> stockPorLote() throws SQLException;

    /** Movimientos de kardex del periodo; producto filtra por código exacto o nombre parcial (RF-34). */
    List<ReporteKardex> kardex(LocalDate desde, LocalDate hastaExclusivo, String producto, int limite) throws SQLException;

    List<ReporteRotacion> rotacion(LocalDate desde, LocalDate hastaExclusivo) throws SQLException;

    /** Citas agrupadas por profesional, servicio, dia o mes (RF-35). */
    List<ReporteCitas> citasAgrupadas(String agrupacion, LocalDate desde, LocalDate hastaExclusivo) throws SQLException;

    List<ReporteAusencia> ausencias(LocalDate desde, LocalDate hastaExclusivo, int limite) throws SQLException;

    List<ReporteAtenciones> atencionesAgrupadas(String agrupacion, LocalDate desde, LocalDate hastaExclusivo)
            throws SQLException;

    List<ReporteGrupo> procedimientosAgrupados(String agrupacion, LocalDate desde, LocalDate hastaExclusivo)
            throws SQLException;

    ReporteClinicaResumen resumenClinica(LocalDate desde, LocalDate hastaExclusivo) throws SQLException;
}
