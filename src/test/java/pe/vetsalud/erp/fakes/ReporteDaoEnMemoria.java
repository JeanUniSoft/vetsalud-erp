package pe.vetsalud.erp.fakes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.vetsalud.erp.dao.ReporteDao;
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

/** Doble de prueba de ReporteDao: devuelve datos preparados y recuerda los periodos consultados. */
public class ReporteDaoEnMemoria implements ReporteDao {

    public final Map<String, List<ReporteGrupo>> ventas = new HashMap<>();
    public ReporteVentasResumen resumen = ReporteVentasResumen.VACIO;
    public final List<ReporteVentaAnulada> anuladas = new ArrayList<>();
    public final List<LoteStock> lotes = new ArrayList<>();
    public final List<ReporteKardex> kardex = new ArrayList<>();
    public final List<ReporteRotacion> rotacion = new ArrayList<>();
    public final List<ReporteCitas> citas = new ArrayList<>();
    public final List<ReporteAusencia> ausencias = new ArrayList<>();
    public final List<ReporteAtenciones> atenciones = new ArrayList<>();
    public final List<ReporteGrupo> procedimientos = new ArrayList<>();
    public ReporteClinicaResumen resumenClinica = ReporteClinicaResumen.VACIO;
    public final List<String> consultas = new ArrayList<>();

    private void anotar(String que, LocalDate desde, LocalDate hasta) {
        consultas.add(que + ":" + desde + ".." + hasta);
    }

    @Override
    public List<ReporteGrupo> ventasAgrupadas(String agrupacion, LocalDate desde, LocalDate hasta) {
        anotar("ventas-" + agrupacion, desde, hasta);
        return ventas.getOrDefault(agrupacion, List.of());
    }

    @Override
    public ReporteVentasResumen resumenVentas(LocalDate desde, LocalDate hasta) {
        return resumen;
    }

    @Override
    public List<ReporteVentaAnulada> ventasAnuladas(LocalDate desde, LocalDate hasta) {
        return anuladas;
    }

    @Override
    public List<LoteStock> stockPorLote() {
        return lotes;
    }

    @Override
    public List<ReporteKardex> kardex(LocalDate desde, LocalDate hasta, String producto, int limite) {
        anotar("kardex-" + producto, desde, hasta);
        return kardex.stream().limit(limite).toList();
    }

    @Override
    public List<ReporteRotacion> rotacion(LocalDate desde, LocalDate hasta) {
        return rotacion;
    }

    @Override
    public List<ReporteCitas> citasAgrupadas(String agrupacion, LocalDate desde, LocalDate hasta) {
        anotar("citas-" + agrupacion, desde, hasta);
        return citas;
    }

    @Override
    public List<ReporteAusencia> ausencias(LocalDate desde, LocalDate hasta, int limite) {
        return ausencias;
    }

    @Override
    public List<ReporteAtenciones> atencionesAgrupadas(String agrupacion, LocalDate desde, LocalDate hasta) {
        return atenciones;
    }

    @Override
    public List<ReporteGrupo> procedimientosAgrupados(String agrupacion, LocalDate desde, LocalDate hasta) {
        return procedimientos;
    }

    @Override
    public ReporteClinicaResumen resumenClinica(LocalDate desde, LocalDate hasta) {
        return resumenClinica;
    }
}
