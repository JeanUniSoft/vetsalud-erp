package pe.vetsalud.erp.dao.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import pe.vetsalud.erp.dao.InventarioDAO;
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
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link ReporteDao}. Usa dos conexiones (privilegio mínimo, RNF-15):
 * <ul>
 *   <li>{@code reportes} (rol vetsalud_reportes, solo lectura): stock, kardex y rotación, que salen
 *       de las vistas v_stock_lote y v_kardex, las únicas que ese rol puede leer.</li>
 *   <li>{@code app} (rol vetsalud_app): ventas por servicio/producto/cajero y reportes clínicos, porque
 *       necesitan tablas base (venta, venta_detalle, pago, cita, consulta...) que vetsalud_reportes no
 *       puede leer. Solo se ejecutan SELECT con autocommit ({@link Db#consultar}).</li>
 * </ul>
 * Las agrupaciones se traducen con listas blancas de fragmentos SQL constantes: el valor que llega
 * del usuario solo se usa como clave del mapa, nunca se concatena (prevención de inyección SQL).
 */
public class ReporteDaoJdbc implements ReporteDao {

    /** Expresión de agrupación y orden de cada criterio, por consulta. */
    private record Grupo(String expresion, String orden) {
    }

    private static final Map<String, Grupo> GRUPO_VENTA = ImmutableMap.of(
            "dia", new Grupo("to_char(v.fecha_hora, 'DD/MM/YYYY')", "min(v.fecha_hora)"),
            "mes", new Grupo("to_char(v.fecha_hora, 'MM/YYYY')", "min(v.fecha_hora)"),
            "cajero", new Grupo("u.nombres || ' ' || u.apellidos", "monto DESC, grupo"));

    private static final Map<String, Grupo> GRUPO_CITA = ImmutableMap.of(
            "profesional", new Grupo("u.nombres || ' ' || u.apellidos", "total DESC, grupo"),
            "servicio", new Grupo("s.nombre", "total DESC, grupo"),
            "dia", new Grupo("to_char(c.inicio, 'DD/MM/YYYY')", "min(c.inicio)"),
            "mes", new Grupo("to_char(c.inicio, 'MM/YYYY')", "min(c.inicio)"));

    private static final Map<String, Grupo> GRUPO_CONSULTA = ImmutableMap.of(
            "profesional", new Grupo("u.nombres || ' ' || u.apellidos", "consultas DESC, grupo"),
            "servicio", new Grupo("coalesce(s.nombre, 'Atención sin cita')", "consultas DESC, grupo"),
            "dia", new Grupo("to_char(c.fecha_hora, 'DD/MM/YYYY')", "min(c.fecha_hora)"),
            "mes", new Grupo("to_char(c.fecha_hora, 'MM/YYYY')", "min(c.fecha_hora)"));

    private static final Map<String, Grupo> GRUPO_PROCEDIMIENTO = ImmutableMap.of(
            "profesional", new Grupo("r.nombres || ' ' || r.apellidos", "monto DESC, grupo"),
            "servicio", new Grupo("s.nombre", "monto DESC, grupo"),
            "dia", new Grupo("to_char(c.fecha_hora, 'DD/MM/YYYY')", "min(c.fecha_hora)"),
            "mes", new Grupo("to_char(c.fecha_hora, 'MM/YYYY')", "min(c.fecha_hora)"));

    private final Db app;
    private final Db reportes;
    private final InventarioDAO inventario = new InventarioDAO();   // DAO del S10, reutilizado sin cambios

    public ReporteDaoJdbc(Db app, Db reportes) {
        this.app = Preconditions.checkNotNull(app);
        this.reportes = Preconditions.checkNotNull(reportes);
    }

    private static Grupo grupo(Map<String, Grupo> lista, String clave) {
        Grupo g = lista.get(clave);
        if (g == null) {
            throw new IllegalArgumentException("Agrupación no permitida: " + StringUtils.abbreviate(clave, 20));
        }
        return g;
    }

    private static BigDecimal cero(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return v == null ? BigDecimal.ZERO : v;
    }

    private static ReporteGrupo mapearGrupo(ResultSet rs) throws SQLException {
        return new ReporteGrupo(rs.getString("grupo"), rs.getLong("registros"), rs.getBigDecimal("cantidad"),
                cero(rs, "monto"));
    }

    // ------------------------------------------------------------------ ventas (RF-33)

    @Override
    public List<ReporteGrupo> ventasAgrupadas(String agrupacion, LocalDate desde, LocalDate hasta)
            throws SQLException {
        String sql = switch (StringUtils.defaultString(agrupacion)) {
            case "medio" -> """
                    SELECT p.medio::text AS grupo, count(DISTINCT v.id_venta) AS registros, NULL::numeric AS cantidad,
                           sum(p.monto) AS monto
                    FROM venta v JOIN pago p ON p.id_venta = v.id_venta
                    WHERE v.estado = 'CONFIRMADA' AND v.fecha_hora >= ? AND v.fecha_hora < ?
                    GROUP BY 1 ORDER BY monto DESC, grupo
                    """;
            case "servicio" -> """
                    SELECT s.nombre AS grupo, count(*) AS registros, sum(d.cantidad) AS cantidad, sum(d.importe) AS monto
                    FROM venta v JOIN venta_detalle d ON d.id_venta = v.id_venta
                    JOIN servicio s ON s.id_servicio = d.id_servicio
                    WHERE v.estado = 'CONFIRMADA' AND v.fecha_hora >= ? AND v.fecha_hora < ?
                    GROUP BY 1 ORDER BY monto DESC, grupo
                    """;
            case "producto" -> """
                    SELECT p.codigo || ' · ' || p.nombre AS grupo, count(*) AS registros, sum(d.cantidad) AS cantidad,
                           sum(d.importe) AS monto
                    FROM venta v JOIN venta_detalle d ON d.id_venta = v.id_venta
                    JOIN lote l ON l.id_lote = d.id_lote JOIN producto p ON p.id_producto = l.id_producto
                    WHERE v.estado = 'CONFIRMADA' AND v.fecha_hora >= ? AND v.fecha_hora < ?
                    GROUP BY 1 ORDER BY monto DESC, grupo
                    """;
            default -> {
                Grupo g = grupo(GRUPO_VENTA, agrupacion);
                yield "SELECT " + g.expresion() + """
                         AS grupo, count(*) AS registros, NULL::numeric AS cantidad, sum(v.total) AS monto
                        FROM venta v JOIN caja_turno t ON t.id_turno = v.id_turno
                        JOIN usuario u ON u.id_usuario = t.id_cajero
                        WHERE v.estado = 'CONFIRMADA' AND v.fecha_hora >= ? AND v.fecha_hora < ?
                        GROUP BY 1 ORDER BY """ + " " + g.orden();
            }
        };
        return app.consultar(cn -> Jdbc.lista(cn, sql, ReporteDaoJdbc::mapearGrupo, desde, hasta));
    }

    @Override
    public ReporteVentasResumen resumenVentas(LocalDate desde, LocalDate hasta) throws SQLException {
        return app.consultar(cn -> Jdbc.uno(cn, """
                SELECT count(*) FILTER (WHERE v.estado = 'CONFIRMADA')                    AS confirmadas,
                       coalesce(sum(v.total) FILTER (WHERE v.estado = 'CONFIRMADA'), 0)     AS total,
                       coalesce(sum(v.subtotal) FILTER (WHERE v.estado = 'CONFIRMADA'), 0)  AS subtotal,
                       coalesce(sum(v.descuento) FILTER (WHERE v.estado = 'CONFIRMADA'), 0) AS descuentos,
                       (SELECT coalesce(sum(d.importe), 0) FROM venta_detalle d JOIN venta x ON x.id_venta = d.id_venta
                         WHERE x.estado = 'CONFIRMADA' AND x.fecha_hora >= ? AND x.fecha_hora < ?
                           AND d.id_servicio IS NOT NULL)                                  AS servicios,
                       (SELECT coalesce(sum(d.importe), 0) FROM venta_detalle d JOIN venta x ON x.id_venta = d.id_venta
                         WHERE x.estado = 'CONFIRMADA' AND x.fecha_hora >= ? AND x.fecha_hora < ?
                           AND d.id_lote IS NOT NULL)                                      AS productos,
                       count(*) FILTER (WHERE v.estado = 'ANULADA')                       AS anuladas,
                       coalesce(sum(v.total) FILTER (WHERE v.estado = 'ANULADA'), 0)        AS monto_anulado
                FROM venta v
                WHERE v.estado IN ('CONFIRMADA', 'ANULADA') AND v.fecha_hora >= ? AND v.fecha_hora < ?
                """, rs -> new ReporteVentasResumen(rs.getLong("confirmadas"), cero(rs, "total"), cero(rs, "subtotal"),
                        cero(rs, "descuentos"), cero(rs, "servicios"), cero(rs, "productos"), rs.getLong("anuladas"),
                        cero(rs, "monto_anulado")),
                desde, hasta, desde, hasta, desde, hasta)).orElse(ReporteVentasResumen.VACIO);
    }

    @Override
    public List<ReporteVentaAnulada> ventasAnuladas(LocalDate desde, LocalDate hasta) throws SQLException {
        return app.consultar(cn -> Jdbc.lista(cn, """
                SELECT v.serie || '-' || lpad(v.numero::text, 8, '0') AS comprobante, v.fecha_hora, v.anulada_en,
                       u.nombres || ' ' || u.apellidos AS cajero, v.total, v.motivo_anulacion
                FROM venta v JOIN caja_turno t ON t.id_turno = v.id_turno
                JOIN usuario u ON u.id_usuario = t.id_cajero
                WHERE v.estado = 'ANULADA' AND v.fecha_hora >= ? AND v.fecha_hora < ?
                ORDER BY v.fecha_hora
                """, rs -> new ReporteVentaAnulada(rs.getString("comprobante"), Jdbc.fechaHora(rs, "fecha_hora"),
                        rs.getString("cajero"), cero(rs, "total"), rs.getString("motivo_anulacion"),
                        Jdbc.fechaHora(rs, "anulada_en")),
                desde, hasta));
    }

    // ------------------------------------------------------------------ inventario (RF-34)

    @Override
    public List<LoteStock> stockPorLote() throws SQLException {
        return reportes.consultar(inventario::listarStockPorLote);
    }

    @Override
    public List<ReporteKardex> kardex(LocalDate desde, LocalDate hasta, String producto, int limite)
            throws SQLException {
        String texto = StringUtils.trimToNull(producto);
        String patron = texto == null ? null : "%" + texto + "%";
        return reportes.consultar(cn -> Jdbc.lista(cn, """
                SELECT fecha_hora, codigo, producto, codigo_lote, fecha_vencimiento, tipo::text AS tipo,
                       entrada, salida, saldo_lote, motivo
                FROM v_kardex
                WHERE fecha_hora >= ? AND fecha_hora < ?
                  AND (?::text IS NULL OR codigo = ?::text OR producto ILIKE ?::text)
                ORDER BY fecha_hora, id_movimiento
                LIMIT ?
                """, rs -> new ReporteKardex(Jdbc.fechaHora(rs, "fecha_hora"), rs.getString("codigo"),
                        rs.getString("producto"), rs.getString("codigo_lote"), Jdbc.fecha(rs, "fecha_vencimiento"),
                        rs.getString("tipo"), rs.getBigDecimal("entrada"), rs.getBigDecimal("salida"),
                        rs.getBigDecimal("saldo_lote"), rs.getString("motivo")),
                desde, hasta, texto, texto, patron, limite));
    }

    @Override
    public List<ReporteRotacion> rotacion(LocalDate desde, LocalDate hasta) throws SQLException {
        return reportes.consultar(cn -> Jdbc.lista(cn, """
                WITH mov AS (
                  SELECT codigo,
                         coalesce(sum(salida) FILTER (WHERE tipo = 'VENTA'), 0)
                       - coalesce(sum(entrada) FILTER (WHERE tipo = 'DEVOLUCION'), 0) AS vendidas
                  FROM v_kardex
                  WHERE fecha_hora >= ? AND fecha_hora < ?
                  GROUP BY codigo),
                st AS (
                  SELECT codigo, producto, categoria, unidad, sum(stock_actual) AS stock
                  FROM v_stock_lote GROUP BY codigo, producto, categoria, unidad)
                SELECT st.codigo, st.producto, st.categoria, st.unidad, coalesce(mov.vendidas, 0) AS vendidas, st.stock
                FROM st LEFT JOIN mov ON mov.codigo = st.codigo
                ORDER BY vendidas DESC, st.producto
                """, rs -> new ReporteRotacion(rs.getString("codigo"), rs.getString("producto"),
                        rs.getString("categoria"), rs.getString("unidad"), cero(rs, "vendidas"), cero(rs, "stock")),
                desde, hasta));
    }

    // ------------------------------------------------------------------ clínica (RF-35)

    @Override
    public List<ReporteCitas> citasAgrupadas(String agrupacion, LocalDate desde, LocalDate hasta)
            throws SQLException {
        Grupo g = grupo(GRUPO_CITA, agrupacion);
        String sql = "SELECT " + g.expresion() + """
                 AS grupo, count(*) AS total,
                       count(*) FILTER (WHERE c.estado = 'ATENDIDA')                     AS atendidas,
                       count(*) FILTER (WHERE c.estado IN ('EN_ESPERA', 'EN_ATENCION'))  AS en_curso,
                       count(*) FILTER (WHERE c.estado IN ('PENDIENTE', 'CONFIRMADA'))   AS programadas,
                       count(*) FILTER (WHERE c.estado = 'CANCELADA')                    AS canceladas,
                       count(*) FILTER (WHERE c.estado = 'NO_ASISTIO')                   AS no_asistio
                FROM cita c JOIN usuario u ON u.id_usuario = c.id_veterinario
                JOIN servicio s ON s.id_servicio = c.id_servicio
                WHERE c.inicio >= ? AND c.inicio < ?
                GROUP BY 1 ORDER BY """ + " " + g.orden();
        return app.consultar(cn -> Jdbc.lista(cn, sql, rs -> new ReporteCitas(rs.getString("grupo"),
                rs.getLong("total"), rs.getLong("atendidas"), rs.getLong("en_curso"), rs.getLong("programadas"),
                rs.getLong("canceladas"), rs.getLong("no_asistio")), desde, hasta));
    }

    @Override
    public List<ReporteAusencia> ausencias(LocalDate desde, LocalDate hasta, int limite) throws SQLException {
        return app.consultar(cn -> Jdbc.lista(cn, """
                SELECT c.inicio, m.nombre AS mascota, s.nombre AS servicio,
                       u.nombres || ' ' || u.apellidos AS profesional, c.estado::text AS estado,
                       (SELECT h.motivo FROM cita_estado_hist h
                         WHERE h.id_cita = c.id_cita AND h.estado_nuevo = c.estado
                         ORDER BY h.fecha_hora DESC LIMIT 1) AS motivo
                FROM cita c JOIN mascota m ON m.id_mascota = c.id_mascota
                JOIN servicio s ON s.id_servicio = c.id_servicio
                JOIN usuario u ON u.id_usuario = c.id_veterinario
                WHERE c.estado IN ('CANCELADA', 'NO_ASISTIO') AND c.inicio >= ? AND c.inicio < ?
                ORDER BY c.inicio
                LIMIT ?
                """, rs -> new ReporteAusencia(Jdbc.fechaHora(rs, "inicio"), rs.getString("mascota"),
                        rs.getString("servicio"), rs.getString("profesional"), rs.getString("estado"),
                        rs.getString("motivo")),
                desde, hasta, limite));
    }

    @Override
    public List<ReporteAtenciones> atencionesAgrupadas(String agrupacion, LocalDate desde, LocalDate hasta)
            throws SQLException {
        Grupo g = grupo(GRUPO_CONSULTA, agrupacion);
        String sql = "SELECT " + g.expresion() + """
                 AS grupo, count(*) AS consultas,
                       count(*) FILTER (WHERE c.estado = 'FIRMADA') AS firmadas,
                       count(*) FILTER (WHERE c.estado = 'ABIERTA') AS abiertas,
                       count(DISTINCT c.id_mascota)                 AS mascotas
                FROM consulta c JOIN usuario u ON u.id_usuario = c.id_veterinario
                LEFT JOIN cita ci ON ci.id_cita = c.id_cita
                LEFT JOIN servicio s ON s.id_servicio = ci.id_servicio
                WHERE c.fecha_hora >= ? AND c.fecha_hora < ?
                GROUP BY 1 ORDER BY """ + " " + g.orden();
        return app.consultar(cn -> Jdbc.lista(cn, sql, rs -> new ReporteAtenciones(rs.getString("grupo"),
                rs.getLong("consultas"), rs.getLong("firmadas"), rs.getLong("abiertas"), rs.getLong("mascotas")),
                desde, hasta));
    }

    @Override
    public List<ReporteGrupo> procedimientosAgrupados(String agrupacion, LocalDate desde, LocalDate hasta)
            throws SQLException {
        Grupo g = grupo(GRUPO_PROCEDIMIENTO, agrupacion);
        String sql = "SELECT " + g.expresion() + """
                 AS grupo, count(*) AS registros, sum(cp.cantidad) AS cantidad,
                       sum(cp.cantidad * cp.precio_unitario) AS monto
                FROM consulta_procedimiento cp JOIN consulta c ON c.id_consulta = cp.id_consulta
                JOIN servicio s ON s.id_servicio = cp.id_servicio
                JOIN usuario r ON r.id_usuario = cp.id_responsable
                WHERE c.fecha_hora >= ? AND c.fecha_hora < ?
                GROUP BY 1 ORDER BY """ + " " + g.orden();
        return app.consultar(cn -> Jdbc.lista(cn, sql, ReporteDaoJdbc::mapearGrupo, desde, hasta));
    }

    @Override
    public ReporteClinicaResumen resumenClinica(LocalDate desde, LocalDate hasta) throws SQLException {
        return app.consultar(cn -> Jdbc.uno(cn, """
                SELECT (SELECT count(*) FROM cita WHERE inicio >= ? AND inicio < ?) AS citas,
                       (SELECT count(*) FROM cita WHERE inicio >= ? AND inicio < ? AND estado = 'ATENDIDA') AS atendidas,
                       (SELECT count(*) FROM cita WHERE inicio >= ? AND inicio < ?
                          AND estado IN ('CANCELADA', 'NO_ASISTIO')) AS ausencias,
                       (SELECT count(*) FROM consulta WHERE fecha_hora >= ? AND fecha_hora < ?) AS consultas,
                       (SELECT count(*) FROM consulta_procedimiento cp JOIN consulta c ON c.id_consulta = cp.id_consulta
                         WHERE c.fecha_hora >= ? AND c.fecha_hora < ?) AS procedimientos
                """, rs -> new ReporteClinicaResumen(rs.getLong("citas"), rs.getLong("atendidas"),
                        rs.getLong("ausencias"), rs.getLong("consultas"), rs.getLong("procedimientos")),
                desde, hasta, desde, hasta, desde, hasta, desde, hasta, desde, hasta))
                .orElse(ReporteClinicaResumen.VACIO);
    }
}
