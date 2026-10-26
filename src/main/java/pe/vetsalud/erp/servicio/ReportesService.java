package pe.vetsalud.erp.servicio;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.ToLongFunction;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import pe.vetsalud.erp.dao.ReporteDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.CondicionLote;
import pe.vetsalud.erp.modelo.FilaReporte;
import pe.vetsalud.erp.modelo.KpiReporte;
import pe.vetsalud.erp.modelo.LoteStock;
import pe.vetsalud.erp.modelo.ReporteAtenciones;
import pe.vetsalud.erp.modelo.ReporteAusencia;
import pe.vetsalud.erp.modelo.ReporteBarra;
import pe.vetsalud.erp.modelo.ReporteCitas;
import pe.vetsalud.erp.modelo.ReporteClinicaResumen;
import pe.vetsalud.erp.modelo.ReporteColumna;
import pe.vetsalud.erp.modelo.ReporteFiltro;
import pe.vetsalud.erp.modelo.ReporteGrafico;
import pe.vetsalud.erp.modelo.ReporteGrupo;
import pe.vetsalud.erp.modelo.ReporteKardex;
import pe.vetsalud.erp.modelo.ReporteRotacion;
import pe.vetsalud.erp.modelo.ReporteTabla;
import pe.vetsalud.erp.modelo.ReporteVentaAnulada;
import pe.vetsalud.erp.modelo.ReporteVentasResumen;

/**
 * UC-14 Consultar y exportar reportes (P10: RF-32 a RF-36).
 * <ul>
 *   <li>Valida filtros (periodo de 1 a 366 días, agrupaciones y vistas de una lista blanca).</li>
 *   <li>Arma cada reporte como una {@link ReporteTabla} genérica con totales, notas, anexos y gráfico.</li>
 *   <li>Exporta con la estrategia {@link ExportadorReporte} que corresponda al formato (abierto/cerrado).</li>
 * </ul>
 * No conoce HTTP ni SQL: depende de la interfaz {@link ReporteDao} (DIP), por eso se prueba sin BD.
 */
public class ReportesService {

    public static final int MAX_DIAS = 366;
    public static final int LIMITE_KARDEX = 2000;
    public static final int LIMITE_AUSENCIAS = 500;
    static final int BARRAS_CATEGORIAS = 12;
    static final int BARRAS_PERIODO = 31;

    public static final Map<String, String> AGRUPACIONES_VENTAS = ImmutableMap.of(
            "dia", "Día", "mes", "Mes", "servicio", "Servicio", "producto", "Producto",
            "cajero", "Cajero", "medio", "Medio de pago");
    public static final Map<String, String> VISTAS_INVENTARIO = ImmutableMap.of(
            "stock", "Stock por lote", "kardex", "Kardex", "vencimientos", "Vencimientos", "rotacion", "Rotación");
    public static final Map<String, String> VISTAS_CLINICA = ImmutableMap.of(
            "citas", "Citas", "ausencias", "Ausencias", "atenciones", "Atenciones (consultas)",
            "procedimientos", "Procedimientos");
    public static final Map<String, String> AGRUPACIONES_CLINICA = ImmutableMap.of(
            "profesional", "Profesional", "servicio", "Servicio", "dia", "Día", "mes", "Mes");
    public static final Set<String> TIPOS = Set.of("ventas", "inventario", "clinica");

    private static final Map<String, String> MEDIOS = ImmutableMap.of(
            "EFECTIVO", "Efectivo", "TARJETA", "Tarjeta", "YAPE_PLIN", "Yape / Plin", "TRANSFERENCIA", "Transferencia");
    private static final Map<String, String> MOVIMIENTOS = ImmutableMap.of(
            "INGRESO", "Ingreso", "VENTA", "Venta", "AJUSTE_ENTRADA", "Ajuste (+)", "AJUSTE_SALIDA", "Ajuste (−)",
            "DEVOLUCION", "Devolución");
    private static final Map<CondicionLote, String> CONDICIONES = ImmutableMap.of(
            CondicionLote.VENCIDO, "Vencido", CondicionLote.POR_VENCER, "Por vencer",
            CondicionLote.STOCK_BAJO, "Stock bajo", CondicionLote.NORMAL, "Normal");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ReporteDao dao;
    private final Map<String, ExportadorReporte> exportadores;
    private final IntSupplier diasPorVencer;
    private final Clock reloj;

    /**
     * @param exportadores  formatos disponibles; agregar uno nuevo no cambia esta clase
     * @param diasPorVencer parámetro DIAS_POR_VENCER (RF-24) leído al momento de cada reporte
     */
    public ReportesService(ReporteDao dao, List<ExportadorReporte> exportadores, IntSupplier diasPorVencer,
                           Clock reloj) {
        this.dao = Preconditions.checkNotNull(dao);
        this.exportadores = Maps.uniqueIndex(Preconditions.checkNotNull(exportadores), ExportadorReporte::formato);
        this.diasPorVencer = Preconditions.checkNotNull(diasPorVencer);
        this.reloj = Preconditions.checkNotNull(reloj);
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }

    // ================================================================== filtros

    /**
     * Valida y completa los filtros. Por defecto: del primer día del mes a hoy.
     *
     * @throws ReglaNegocioException si el periodo es inválido o la vista/agrupación no existe
     */
    public ReporteFiltro filtro(String tipo, LocalDate desde, LocalDate hasta, String vista, String agrupacion,
                                String producto, Integer dias) {
        LocalDate h = hasta != null ? hasta : hoy();
        LocalDate d = desde != null ? desde : h.withDayOfMonth(1);
        validarPeriodo(d, h);
        String v = null;
        String a = null;
        switch (StringUtils.defaultString(tipo)) {
            case "ventas" -> a = elegir(agrupacion, "dia", AGRUPACIONES_VENTAS, "Agrupación");
            case "inventario" -> v = elegir(vista, "stock", VISTAS_INVENTARIO, "Vista");
            case "clinica" -> {
                v = elegir(vista, "citas", VISTAS_CLINICA, "Vista");
                a = elegir(agrupacion, "profesional", AGRUPACIONES_CLINICA, "Agrupación");
            }
            case "indice" -> {
                // índice de P10: solo el periodo
            }
            default -> throw new ReglaNegocioException("El tipo de reporte solicitado no existe.");
        }
        int alerta = dias == null ? diasPorVencer.getAsInt() : dias;
        if (alerta < 1 || alerta > 365) {
            throw new ReglaNegocioException("Los días de alerta de vencimiento deben estar entre 1 y 365.");
        }
        String p = StringUtils.trimToNull(producto);
        Validaciones.longitudMaxima(p, 60, "Producto");
        return new ReporteFiltro(d, h, v, a, p, alerta);
    }

    /** RF-33/34/35: el periodo debe ser coherente y de 366 días como máximo (RNF-01). */
    static void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new ReglaNegocioException("La fecha «Desde» (" + FECHA.format(desde)
                    + ") no puede ser posterior a «Hasta» (" + FECHA.format(hasta) + ").");
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias > MAX_DIAS) {
            throw new ReglaNegocioException("El periodo no puede superar " + MAX_DIAS + " días (eligió " + dias
                    + "). Divídalo en periodos más cortos.");
        }
    }

    private static String elegir(String valor, String porDefecto, Map<String, String> permitidos, String campo) {
        String v = StringUtils.defaultIfBlank(StringUtils.trimToNull(valor), porDefecto);
        if (!permitidos.containsKey(v)) {
            throw new ReglaNegocioException("La opción elegida en «" + campo + "» no es válida.");
        }
        return v;
    }

    // ================================================================== despacho genérico

    public ReporteTabla construir(String tipo, ReporteFiltro f) throws SQLException {
        return switch (StringUtils.defaultString(tipo)) {
            case "ventas" -> ventas(f);
            case "inventario" -> inventario(f);
            case "clinica" -> clinica(f);
            default -> throw new ReglaNegocioException("El tipo de reporte solicitado no existe.");
        };
    }

    public List<KpiReporte> kpis(String tipo, ReporteFiltro f) throws SQLException {
        return switch (StringUtils.defaultString(tipo)) {
            case "ventas" -> kpisVentas(dao.resumenVentas(f.desde(), f.hastaExclusivo()));
            case "inventario" -> kpisInventario(f);
            case "clinica" -> kpisClinica(dao.resumenClinica(f.desde(), f.hastaExclusivo()));
            default -> indicadoresGenerales(f);
        };
    }

    // ================================================================== RF-33 ventas

    public ReporteTabla ventas(ReporteFiltro f) throws SQLException {
        String ag = f.agrupacion();
        String etiqueta = AGRUPACIONES_VENTAS.get(ag);
        List<ReporteGrupo> grupos = dao.ventasAgrupadas(ag, f.desde(), f.hastaExclusivo());
        ReporteVentasResumen r = dao.resumenVentas(f.desde(), f.hastaExclusivo());
        List<ReporteVentaAnulada> anuladas = dao.ventasAnuladas(f.desde(), f.hastaExclusivo());
        boolean porLinea = ag.equals("servicio") || ag.equals("producto");

        List<ReporteColumna> columnas;
        List<FilaReporte> filas = new ArrayList<>();
        FilaReporte totales;
        List<String> notas = new ArrayList<>();
        notas.add("Solo suman las ventas CONFIRMADAS del periodo; las anuladas se listan aparte y no suman (RN-09).");
        BigDecimal suma = grupos.stream().map(ReporteGrupo::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (porLinea) {
            columnas = List.of(ReporteColumna.texto(etiqueta), ReporteColumna.entero("Líneas vendidas"),
                    ReporteColumna.decimal("Cantidad"), ReporteColumna.moneda("Importe"));
            for (ReporteGrupo g : grupos) {
                filas.add(FilaReporte.de(g.grupo(), g.registros(), g.cantidad(), g.monto()));
            }
            totales = FilaReporte.de("Total", sumar(grupos, ReporteGrupo::registros),
                    grupos.stream().map(g -> nulo(g.cantidad())).reduce(BigDecimal.ZERO, BigDecimal::add), suma);
            notas.add("Importes de línea antes de descuentos; la conciliación muestra cómo se llega al total vendido.");
        } else {
            columnas = List.of(ReporteColumna.texto(etiqueta), ReporteColumna.entero("Comprobantes"),
                    ReporteColumna.moneda("Total vendido"), ReporteColumna.porcentaje("% del total"));
            for (ReporteGrupo g : grupos) {
                String nombre = ag.equals("medio") ? MEDIOS.getOrDefault(g.grupo(), g.grupo()) : g.grupo();
                filas.add(FilaReporte.de(nombre, g.registros(), g.monto(), porcentaje(g.monto(), r.total())));
            }
            totales = FilaReporte.de("Total", r.confirmadas(), suma, r.total().signum() == 0 ? BigDecimal.ZERO : CIEN);
            if (ag.equals("medio")) {
                notas.add("Una venta pagada con varios medios cuenta en cada medio; el total de comprobantes no se repite.");
            }
        }
        ReporteTabla conciliacion = ReporteTabla.anexo("Conciliación del total vendido",
                List.of(ReporteColumna.texto("Concepto"), ReporteColumna.moneda("Monto")),
                List.of(FilaReporte.de("Servicios (importe de líneas)", r.servicios()),
                        FilaReporte.de("Productos (importe de líneas)", r.productos()),
                        FilaReporte.de("(−) Descuentos", r.descuentos().negate())),
                FilaReporte.de("Total vendido (ventas confirmadas)", r.total()));
        List<FilaReporte> filasAnuladas = anuladas.stream()
                .map(a -> FilaReporte.de(a.comprobante(), a.fecha(), a.cajero(), a.total(), a.motivo(), a.anuladaEn()))
                .toList();
        ReporteTabla tablaAnuladas = ReporteTabla.anexo("Ventas anuladas (no suman)",
                List.of(ReporteColumna.texto("Comprobante"), ReporteColumna.fechaHora("Fecha de venta"),
                        ReporteColumna.texto("Cajero"), ReporteColumna.moneda("Total anulado"),
                        ReporteColumna.texto("Motivo"), ReporteColumna.fechaHora("Anulada el")),
                filasAnuladas, FilaReporte.de("Total anulado (" + anuladas.size() + ")", null, null,
                        anuladas.stream().map(ReporteVentaAnulada::total).reduce(BigDecimal.ZERO, BigDecimal::add),
                        null, null));

        boolean temporal = ag.equals("dia") || ag.equals("mes");
        int colValor = porLinea ? 3 : 2;
        ReporteGrafico grafico = grafico("Ventas por " + etiqueta.toLowerCase(), filas, 0, colValor,
                ReporteColumna.Tipo.MONEDA, !temporal, temporal ? BARRAS_PERIODO : BARRAS_CATEGORIAS);
        Map<String, String> filtros = filtrosBase(f);
        filtros.put("Agrupación", etiqueta);
        return new ReporteTabla("ventas", "Ventas por " + etiqueta.toLowerCase(), filtros, columnas, filas,
                totales, notas, List.of(conciliacion, tablaAnuladas), grafico);
    }

    List<KpiReporte> kpisVentas(ReporteVentasResumen r) {
        return List.of(
                KpiReporte.moneda("Total vendido", r.total(), r.confirmadas() + " comprobantes confirmados").resaltado(),
                KpiReporte.moneda("Ticket promedio", r.ticketPromedio(),
                        "Descuentos otorgados: S/ " + r.descuentos().setScale(2, RoundingMode.HALF_UP).toPlainString()),
                KpiReporte.moneda("Servicios vendidos", r.servicios(),
                        "Productos: S/ " + r.productos().setScale(2, RoundingMode.HALF_UP).toPlainString()),
                KpiReporte.entero("Ventas anuladas", r.anuladas(),
                        "S/ " + r.montoAnulado().setScale(2, RoundingMode.HALF_UP).toPlainString() + " que no suman al total"));
    }

    // ================================================================== RF-34 inventario

    public ReporteTabla inventario(ReporteFiltro f) throws SQLException {
        Map<String, String> filtros = filtrosBase(f);
        filtros.put("Vista", VISTAS_INVENTARIO.get(f.vista()));
        return switch (f.vista()) {
            case "kardex" -> kardex(f, filtros);
            case "vencimientos" -> vencimientos(f, filtros);
            case "rotacion" -> rotacion(f, filtros);
            default -> stock(f, filtros);
        };
    }

    private ReporteTabla stock(ReporteFiltro f, Map<String, String> filtros) throws SQLException {
        LocalDate hoy = hoy();
        filtros.remove("Desde");
        filtros.remove("Hasta");
        filtros.put("Corte", FECHA.format(hoy));
        filtros.put("Días de alerta", String.valueOf(f.dias()));
        List<LoteStock> lotes = dao.stockPorLote();
        List<FilaReporte> filas = new ArrayList<>();
        BigDecimal saldo = BigDecimal.ZERO;
        BigDecimal valor = BigDecimal.ZERO;
        for (LoteStock l : lotes) {
            filas.add(FilaReporte.de(l.producto(), l.codigo(), l.categoria(), l.codigoLote(), l.vencimiento(),
                    l.stock(), l.stockMinimo(), l.costoUnitario(), l.valorizado(),
                    CONDICIONES.get(l.condicion(hoy, f.dias()))));
            saldo = saldo.add(l.stock());
            valor = valor.add(l.valorizado());
        }
        List<ReporteColumna> columnas = List.of(ReporteColumna.texto("Producto"), ReporteColumna.texto("Código"),
                ReporteColumna.texto("Categoría"), ReporteColumna.texto("Lote"), ReporteColumna.fecha("Vencimiento"),
                ReporteColumna.decimal("Saldo"), ReporteColumna.decimal("Stock mín. (producto)"),
                ReporteColumna.moneda("Costo unit."), ReporteColumna.moneda("Valorizado"),
                ReporteColumna.texto("Condición"));
        return new ReporteTabla("inventario", "Stock por lote", filtros, columnas, filas,
                FilaReporte.de("Total", null, null, null, null, saldo, null, null, valor, null),
                List.of("Condición: vencido (no se vende, RN-06), por vencer (dentro de los días de alerta), "
                        + "stock bajo (stock vendible del producto ≤ mínimo) o normal.",
                        "Fuente: vista v_stock_lote con el rol de solo lectura vetsalud_reportes."),
                null, grafico("Valor del inventario por producto", filas, 0, 8, ReporteColumna.Tipo.MONEDA, true,
                        BARRAS_CATEGORIAS));
    }

    private ReporteTabla vencimientos(ReporteFiltro f, Map<String, String> filtros) throws SQLException {
        LocalDate hoy = hoy();
        filtros.remove("Desde");
        filtros.remove("Hasta");
        filtros.put("Corte", FECHA.format(hoy));
        filtros.put("Días de alerta", String.valueOf(f.dias()));
        List<LoteStock> lotes = dao.stockPorLote().stream()
                .filter(l -> l.vencimiento() != null && l.stock().signum() > 0)
                .filter(l -> !l.vencimiento().isAfter(hoy.plusDays(f.dias())))
                .sorted(Comparator.comparing(LoteStock::vencimiento))
                .toList();
        List<FilaReporte> filas = new ArrayList<>();
        BigDecimal saldo = BigDecimal.ZERO;
        BigDecimal valor = BigDecimal.ZERO;
        for (LoteStock l : lotes) {
            filas.add(FilaReporte.de(l.producto(), l.codigo(), l.codigoLote(), l.vencimiento(), l.diasParaVencer(hoy),
                    l.stock(), l.valorizado(), l.vencimiento().isBefore(hoy) ? "Vencido" : "Por vencer"));
            saldo = saldo.add(l.stock());
            valor = valor.add(l.valorizado());
        }
        List<ReporteColumna> columnas = List.of(ReporteColumna.texto("Producto"), ReporteColumna.texto("Código"),
                ReporteColumna.texto("Lote"), ReporteColumna.fecha("Vencimiento"), ReporteColumna.entero("Días para vencer"),
                ReporteColumna.decimal("Saldo"), ReporteColumna.moneda("Valor en riesgo"), ReporteColumna.texto("Condición"));
        return new ReporteTabla("inventario", "Vencimientos (próximos " + f.dias() + " días)", filtros, columnas,
                filas, FilaReporte.de("Total", null, null, null, null, saldo, valor, null),
                List.of("Lotes con stock vencidos o que vencen dentro de los días de alerta (parámetro DIAS_POR_VENCER).",
                        "Días negativos = el lote ya venció y debe retirarse de la venta (RN-06)."),
                null, grafico("Valor en riesgo por producto", filas, 0, 6, ReporteColumna.Tipo.MONEDA, true,
                        BARRAS_CATEGORIAS));
    }

    private ReporteTabla kardex(ReporteFiltro f, Map<String, String> filtros) throws SQLException {
        if (f.producto() != null) {
            filtros.put("Producto", f.producto());
        }
        List<ReporteKardex> movimientos = dao.kardex(f.desde(), f.hastaExclusivo(), f.producto(), LIMITE_KARDEX + 1);
        List<String> notas = new ArrayList<>();
        if (movimientos.size() > LIMITE_KARDEX) {
            movimientos = movimientos.subList(0, LIMITE_KARDEX);
            notas.add("Se muestran los primeros " + LIMITE_KARDEX + " movimientos; acote el periodo o el producto.");
        }
        List<FilaReporte> filas = new ArrayList<>();
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal salidas = BigDecimal.ZERO;
        Map<String, BigDecimal> porTipo = new LinkedHashMap<>();
        for (ReporteKardex k : movimientos) {
            String tipo = MOVIMIENTOS.getOrDefault(k.tipo(), k.tipo());
            filas.add(FilaReporte.de(k.fecha(), k.codigo(), k.producto(), k.lote(), tipo, k.entrada(), k.salida(),
                    k.saldo(), k.motivo()));
            entradas = entradas.add(nulo(k.entrada()));
            salidas = salidas.add(nulo(k.salida()));
            porTipo.merge(tipo, nulo(k.entrada()).add(nulo(k.salida())), BigDecimal::add);
        }
        notas.add("Kardex inmutable (RNF-06): cada movimiento actualiza el saldo del lote por trigger. "
                + "Los ajustes llevan motivo obligatorio (RF-25).");
        List<FilaReporte> resumen = porTipo.entrySet().stream().map(e -> FilaReporte.de(e.getKey(), e.getValue())).toList();
        List<ReporteColumna> columnas = List.of(ReporteColumna.fechaHora("Fecha"), ReporteColumna.texto("Código"),
                ReporteColumna.texto("Producto"), ReporteColumna.texto("Lote"), ReporteColumna.texto("Movimiento"),
                ReporteColumna.decimal("Entrada"), ReporteColumna.decimal("Salida"),
                ReporteColumna.decimal("Saldo del lote"), ReporteColumna.texto("Motivo"));
        return new ReporteTabla("inventario", "Kardex de inventario", filtros, columnas, filas,
                FilaReporte.de(null, "Totales del periodo", null, null, null, entradas, salidas, null, null),
                notas, null, grafico("Unidades movidas por tipo de movimiento", resumen, 0, 1,
                        ReporteColumna.Tipo.DECIMAL, true, BARRAS_CATEGORIAS));
    }

    private ReporteTabla rotacion(ReporteFiltro f, Map<String, String> filtros) throws SQLException {
        List<ReporteRotacion> productos = dao.rotacion(f.desde(), f.hastaExclusivo());
        long dias = f.diasPeriodo();
        List<FilaReporte> filas = new ArrayList<>();
        BigDecimal vendidas = BigDecimal.ZERO;
        BigDecimal stock = BigDecimal.ZERO;
        for (ReporteRotacion p : productos) {
            filas.add(FilaReporte.de(p.producto(), p.codigo(), p.categoria(), p.vendidas(), p.stock(),
                    indiceRotacion(p.vendidas(), p.stock()), coberturaDias(p.vendidas(), p.stock(), dias),
                    lecturaRotacion(p.vendidas(), p.stock())));
            vendidas = vendidas.add(p.vendidas());
            stock = stock.add(p.stock());
        }
        List<ReporteColumna> columnas = List.of(ReporteColumna.texto("Producto"), ReporteColumna.texto("Código"),
                ReporteColumna.texto("Categoría"), ReporteColumna.decimal("Vendidas (netas)"),
                ReporteColumna.decimal("Stock actual"), ReporteColumna.decimal("Índice de rotación"),
                ReporteColumna.entero("Cobertura (días)"), ReporteColumna.texto("Lectura"));
        return new ReporteTabla("inventario", "Rotación de productos", filtros, columnas, filas,
                FilaReporte.de("Total", null, null, vendidas, stock, indiceRotacion(vendidas, stock),
                        coberturaDias(vendidas, stock, dias), null),
                List.of(FORMULA_ROTACION, FORMULA_COBERTURA,
                        "Lectura: alta (índice ≥ 1), media (0,30 a 0,99), baja (< 0,30), sin movimiento o agotado."),
                null, grafico("Unidades vendidas netas por producto", filas, 0, 3, ReporteColumna.Tipo.DECIMAL, true,
                        BARRAS_CATEGORIAS));
    }

    public static final String FORMULA_ROTACION = "Índice de rotación = unidades vendidas netas del periodo "
            + "(salidas por venta − devoluciones por anulación) ÷ stock actual.";
    public static final String FORMULA_COBERTURA = "Cobertura (días) = stock actual ÷ (vendidas netas ÷ días del "
            + "periodo): días que alcanza el stock al ritmo de venta del periodo.";

    /** Vendidas ÷ stock (2 decimales); null si no hay stock que dividir. */
    static BigDecimal indiceRotacion(BigDecimal vendidas, BigDecimal stock) {
        if (stock == null || stock.signum() <= 0) {
            return null;
        }
        return nulo(vendidas).divide(stock, 2, RoundingMode.HALF_UP);
    }

    /** Stock ÷ venta diaria promedio (días enteros); null si no hubo ventas en el periodo. */
    static Long coberturaDias(BigDecimal vendidas, BigDecimal stock, long diasPeriodo) {
        if (vendidas == null || vendidas.signum() <= 0 || diasPeriodo <= 0) {
            return null;
        }
        BigDecimal ventaDiaria = vendidas.divide(BigDecimal.valueOf(diasPeriodo), 6, RoundingMode.HALF_UP);
        return nulo(stock).divide(ventaDiaria, 0, RoundingMode.DOWN).longValue();
    }

    static String lecturaRotacion(BigDecimal vendidas, BigDecimal stock) {
        if (nulo(vendidas).signum() <= 0) {
            return "Sin movimiento";
        }
        BigDecimal indice = indiceRotacion(vendidas, stock);
        if (indice == null) {
            return "Agotado";
        }
        if (indice.compareTo(BigDecimal.ONE) >= 0) {
            return "Alta";
        }
        return indice.compareTo(new BigDecimal("0.30")) >= 0 ? "Media" : "Baja";
    }

    List<KpiReporte> kpisInventario(ReporteFiltro f) throws SQLException {
        LocalDate hoy = hoy();
        List<LoteStock> lotes = dao.stockPorLote();
        BigDecimal valor = BigDecimal.ZERO;
        BigDecimal enRiesgo = BigDecimal.ZERO;
        long conStock = 0;
        long vencidos = 0;
        long porVencer = 0;
        long bajo = 0;
        for (LoteStock l : lotes) {
            if (l.stock().signum() <= 0) {
                continue;
            }
            conStock++;
            valor = valor.add(l.valorizado());
            switch (l.condicion(hoy, f.dias())) {
                case VENCIDO -> {
                    vencidos++;
                    enRiesgo = enRiesgo.add(l.valorizado());
                }
                case POR_VENCER -> porVencer++;
                case STOCK_BAJO -> bajo++;
                default -> {
                }
            }
        }
        BigDecimal vendidas = dao.rotacion(f.desde(), f.hastaExclusivo()).stream().map(ReporteRotacion::vendidas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return List.of(
                KpiReporte.moneda("Valor del inventario", valor, conStock + " lote(s) con stock").resaltado(),
                KpiReporte.entero("Lotes vencidos", vencidos,
                        "S/ " + enRiesgo.setScale(2, RoundingMode.HALF_UP).toPlainString() + " retenidos (RN-06)"),
                KpiReporte.entero("Lotes por vencer", porVencer, "en los próximos " + f.dias() + " días · "
                        + bajo + " con stock bajo"),
                KpiReporte.decimal("Unidades vendidas", vendidas, "netas en el periodo filtrado"));
    }

    // ================================================================== RF-35 clínica

    public ReporteTabla clinica(ReporteFiltro f) throws SQLException {
        String ag = f.agrupacion();
        String etiqueta = AGRUPACIONES_CLINICA.get(ag);
        boolean temporal = ag.equals("dia") || ag.equals("mes");
        int barras = temporal ? BARRAS_PERIODO : BARRAS_CATEGORIAS;
        Map<String, String> filtros = filtrosBase(f);
        filtros.put("Vista", VISTAS_CLINICA.get(f.vista()));
        filtros.put("Agrupación", etiqueta);
        String sufijo = " por " + etiqueta.toLowerCase();
        return switch (f.vista()) {
            case "ausencias" -> {
                List<ReporteCitas> grupos = dao.citasAgrupadas(ag, f.desde(), f.hastaExclusivo());
                List<FilaReporte> filas = grupos.stream().map(g -> FilaReporte.de(g.grupo(), g.total(), g.canceladas(),
                        g.noAsistio(), g.ausencias(), g.porcentajeAusentismo())).toList();
                ReporteCitas t = totalCitas(grupos);
                List<ReporteAusencia> detalle = dao.ausencias(f.desde(), f.hastaExclusivo(), LIMITE_AUSENCIAS);
                ReporteTabla anexo = ReporteTabla.anexo("Detalle de ausencias",
                        List.of(ReporteColumna.fechaHora("Cita"), ReporteColumna.texto("Mascota"),
                                ReporteColumna.texto("Servicio"), ReporteColumna.texto("Profesional"),
                                ReporteColumna.texto("Estado"), ReporteColumna.texto("Motivo registrado")),
                        detalle.stream().map(a -> FilaReporte.de(a.inicio(), a.mascota(), a.servicio(), a.profesional(),
                                a.estado().equals("NO_ASISTIO") ? "No asistió" : "Cancelada", a.motivo())).toList(),
                        null);
                yield new ReporteTabla("clinica", "Ausencias" + sufijo, filtros,
                        List.of(ReporteColumna.texto(etiqueta), ReporteColumna.entero("Citas"),
                                ReporteColumna.entero("Canceladas"), ReporteColumna.entero("No asistió"),
                                ReporteColumna.entero("Ausencias"), ReporteColumna.porcentaje("% ausentismo")),
                        filas, FilaReporte.de("Total", t.total(), t.canceladas(), t.noAsistio(), t.ausencias(),
                                t.porcentajeAusentismo()),
                        List.of("Ausencias = citas CANCELADAS + NO_ASISTIO; % ausentismo = ausencias ÷ citas del grupo."),
                        List.of(anexo), grafico("Ausencias" + sufijo, filas, 0, 4, ReporteColumna.Tipo.ENTERO, !temporal,
                                barras));
            }
            case "atenciones" -> {
                List<ReporteAtenciones> grupos = dao.atencionesAgrupadas(ag, f.desde(), f.hastaExclusivo());
                List<FilaReporte> filas = grupos.stream().map(g -> FilaReporte.de(g.grupo(), g.consultas(), g.firmadas(),
                        g.abiertas(), g.mascotas())).toList();
                yield new ReporteTabla("clinica", "Atenciones clínicas" + sufijo, filtros,
                        List.of(ReporteColumna.texto(etiqueta), ReporteColumna.entero("Consultas"),
                                ReporteColumna.entero("Firmadas"), ReporteColumna.entero("Abiertas (sin firmar)"),
                                ReporteColumna.entero("Mascotas atendidas")),
                        filas, FilaReporte.de("Total", sumar(grupos, ReporteAtenciones::consultas),
                                sumar(grupos, ReporteAtenciones::firmadas), sumar(grupos, ReporteAtenciones::abiertas), null),
                        List.of("Una consulta abierta aún puede editarse; al firmarse queda inmutable (RN-05).",
                                "«Mascotas atendidas» cuenta mascotas distintas dentro de cada grupo."),
                        null, grafico("Consultas" + sufijo, filas, 0, 1, ReporteColumna.Tipo.ENTERO, !temporal, barras));
            }
            case "procedimientos" -> {
                List<ReporteGrupo> grupos = dao.procedimientosAgrupados(ag, f.desde(), f.hastaExclusivo());
                List<FilaReporte> filas = grupos.stream().map(g -> FilaReporte.de(g.grupo(), g.registros(),
                        g.cantidad(), g.monto())).toList();
                yield new ReporteTabla("clinica", "Procedimientos" + sufijo, filtros,
                        List.of(ReporteColumna.texto(etiqueta), ReporteColumna.entero("Registros"),
                                ReporteColumna.decimal("Cantidad"), ReporteColumna.moneda("Importe (tarifa registrada)")),
                        filas, FilaReporte.de("Total", sumar(grupos, ReporteGrupo::registros),
                                grupos.stream().map(g -> nulo(g.cantidad())).reduce(BigDecimal.ZERO, BigDecimal::add),
                                grupos.stream().map(ReporteGrupo::monto).reduce(BigDecimal.ZERO, BigDecimal::add)),
                        List.of("Procedimientos registrados en las consultas (RF-16); «Profesional» es el responsable."),
                        null, grafico("Procedimientos" + sufijo, filas, 0, 2, ReporteColumna.Tipo.DECIMAL, !temporal,
                                barras));
            }
            default -> {
                List<ReporteCitas> grupos = dao.citasAgrupadas(ag, f.desde(), f.hastaExclusivo());
                List<FilaReporte> filas = grupos.stream().map(g -> FilaReporte.de(g.grupo(), g.total(), g.atendidas(),
                        g.enCurso(), g.programadas(), g.canceladas(), g.noAsistio(), g.porcentajeAusentismo())).toList();
                ReporteCitas t = totalCitas(grupos);
                yield new ReporteTabla("clinica", "Citas" + sufijo, filtros,
                        List.of(ReporteColumna.texto(etiqueta), ReporteColumna.entero("Citas"),
                                ReporteColumna.entero("Atendidas"), ReporteColumna.entero("En sala"),
                                ReporteColumna.entero("Programadas"), ReporteColumna.entero("Canceladas"),
                                ReporteColumna.entero("No asistió"), ReporteColumna.porcentaje("% ausentismo")),
                        filas, FilaReporte.de("Total", t.total(), t.atendidas(), t.enCurso(), t.programadas(),
                                t.canceladas(), t.noAsistio(), t.porcentajeAusentismo()),
                        List.of("En sala = en espera o en atención; programadas = pendientes o confirmadas."),
                        null, grafico("Citas" + sufijo, filas, 0, 1, ReporteColumna.Tipo.ENTERO, !temporal, barras));
            }
        };
    }

    private static ReporteCitas totalCitas(List<ReporteCitas> grupos) {
        return new ReporteCitas("Total", sumar(grupos, ReporteCitas::total), sumar(grupos, ReporteCitas::atendidas),
                sumar(grupos, ReporteCitas::enCurso), sumar(grupos, ReporteCitas::programadas),
                sumar(grupos, ReporteCitas::canceladas), sumar(grupos, ReporteCitas::noAsistio));
    }

    List<KpiReporte> kpisClinica(ReporteClinicaResumen r) {
        BigDecimal ausentismo = r.citas() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(r.ausencias() * 100).divide(BigDecimal.valueOf(r.citas()), 1, RoundingMode.HALF_UP);
        return List.of(
                KpiReporte.entero("Citas del periodo", r.citas(), r.atendidas() + " atendidas").resaltado(),
                KpiReporte.porcentaje("Ausentismo", ausentismo, r.ausencias() + " canceladas o no asistidas"),
                KpiReporte.entero("Atenciones clínicas", r.consultas(), "consultas registradas"),
                KpiReporte.entero("Procedimientos", r.procedimientos(), "registrados en consultas"));
    }

    // ================================================================== P10 índice (RF-32)

    public List<KpiReporte> indicadoresGenerales(ReporteFiltro f) throws SQLException {
        ReporteVentasResumen v = dao.resumenVentas(f.desde(), f.hastaExclusivo());
        ReporteClinicaResumen c = dao.resumenClinica(f.desde(), f.hastaExclusivo());
        List<KpiReporte> inventario = kpisInventario(f);
        return List.of(
                KpiReporte.moneda("Ventas confirmadas", v.total(), v.confirmadas() + " comprobantes · "
                        + v.anuladas() + " anulada(s) aparte").resaltado(),
                KpiReporte.entero("Citas", c.citas(), c.atendidas() + " atendidas · " + c.ausencias() + " ausencias"),
                KpiReporte.entero("Atenciones clínicas", c.consultas(), c.procedimientos() + " procedimiento(s)"),
                new KpiReporte(inventario.get(0).etiqueta(), inventario.get(0).valor(), "MONEDA",
                        inventario.get(1).valor().longValue() + " lote(s) vencido(s) · " + inventario.get(2).valor().longValue()
                                + " por vencer", false));
    }

    // ================================================================== RF-36 exportación

    public Set<String> formatos() {
        return exportadores.keySet();
    }

    public ExportadorReporte exportador(String formato) {
        ExportadorReporte e = exportadores.get(StringUtils.lowerCase(StringUtils.trimToEmpty(formato)));
        if (e == null) {
            throw new ReglaNegocioException("Formato de exportación no disponible. Use: " + String.join(", ", formatos()) + ".");
        }
        return e;
    }

    /** Nombre seguro del archivo: reporte_ventas_dia_20261025-1930.csv (solo letras, dígitos, guiones). */
    public String nombreArchivo(ReporteTabla t, ReporteFiltro f, ExportadorReporte e) {
        String detalle = StringUtils.defaultIfBlank(f.vista(), f.agrupacion());
        String base = "reporte_" + t.codigo() + (detalle == null ? "" : "_" + detalle) + "_" + SELLO.format(ahora());
        return base.replaceAll("[^A-Za-z0-9_-]", "") + "." + e.extension();
    }

    /** Exporta los datos visibles y deja constancia en el log de auditoría (RF-04, RF-36). */
    public void exportar(ReporteTabla t, ReporteFiltro f, ExportadorReporte e, String emitidoPor, String usuario,
                         OutputStream salida) throws IOException {
        e.exportar(t, emitidoPor, ahora(), salida);
        Auditoria.registrar("EXPORTAR_REPORTE", "reporte_" + t.codigo(), "formato=" + e.formato() + " filas="
                + t.filas().size() + " vista=" + StringUtils.defaultString(f.vista(), "-") + " agrupacion="
                + StringUtils.defaultString(f.agrupacion(), "-") + " periodo=" + f.desde() + ".." + f.hasta()
                + " por=" + usuario);
    }

    // ================================================================== utilidades

    private Map<String, String> filtrosBase(ReporteFiltro f) {
        Map<String, String> filtros = new LinkedHashMap<>();
        filtros.put("Desde", FECHA.format(f.desde()));
        filtros.put("Hasta", FECHA.format(f.hasta()));
        return filtros;
    }

    /**
     * Barras de un gráfico a partir de las filas: suma por etiqueta, ordena de mayor a menor si
     * {@code ordenar}, conserva hasta {@code maximo} barras y calcula el largo relativo (0-100).
     */
    static ReporteGrafico grafico(String titulo, List<FilaReporte> filas, int colEtiqueta, int colValor,
                                  ReporteColumna.Tipo tipo, boolean ordenar, int maximo) {
        Map<String, BigDecimal> acumulado = new LinkedHashMap<>();
        for (FilaReporte fila : filas) {
            String etiqueta = StringUtils.defaultIfBlank(String.valueOf(fila.valor(colEtiqueta)), "—");
            acumulado.merge(etiqueta, numero(fila.valor(colValor)), BigDecimal::add);
        }
        List<Map.Entry<String, BigDecimal>> entradas = new ArrayList<>(acumulado.entrySet());
        if (ordenar) {
            entradas.sort(Map.Entry.<String, BigDecimal>comparingByValue().reversed());
        }
        List<Map.Entry<String, BigDecimal>> visibles = entradas.subList(0, Math.min(maximo, entradas.size()));
        BigDecimal mayor = visibles.stream().map(Map.Entry::getValue).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        ImmutableList.Builder<ReporteBarra> barras = ImmutableList.builder();
        for (Map.Entry<String, BigDecimal> e : visibles) {
            int largo = 0;
            if (mayor.signum() > 0 && e.getValue().signum() > 0) {
                largo = Math.max(1, e.getValue().multiply(CIEN).divide(mayor, 0, RoundingMode.HALF_UP).intValue());
            }
            barras.add(new ReporteBarra(StringUtils.abbreviate(e.getKey(), 28), e.getKey(), e.getValue(), largo));
        }
        return new ReporteGrafico(titulo, tipo, barras.build(), entradas.size());
    }

    private static BigDecimal numero(Object valor) {
        if (valor instanceof BigDecimal b) {
            return b;
        }
        if (valor instanceof Number n) {
            return BigDecimal.valueOf(n.longValue());
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal nulo(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static <T> long sumar(List<T> lista, ToLongFunction<T> campo) {
        return lista.stream().mapToLong(campo).sum();
    }

    /** Porcentaje (1 decimal) de parte sobre total; 0 si el total es 0. */
    static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return nulo(parte).multiply(CIEN).divide(total, 1, RoundingMode.HALF_UP);
    }
}
