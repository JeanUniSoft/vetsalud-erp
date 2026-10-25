package pe.vetsalud.erp.dao.jdbc;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import pe.vetsalud.erp.dao.PanelDao;
import pe.vetsalud.erp.modelo.AlertaInventario;
import pe.vetsalud.erp.modelo.CitaPanel;
import pe.vetsalud.erp.modelo.PuntoSerie;
import pe.vetsalud.erp.modelo.ResumenPanel;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

public class PanelDaoJdbc implements PanelDao {

    private final Db db;

    public PanelDaoJdbc(Db db) {
        this.db = db;
    }

    @Override
    public ResumenPanel resumen(LocalDate dia) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, """
                SELECT
                  (SELECT count(*) FROM cita WHERE inicio::date = ?)                                         AS citas,
                  (SELECT count(*) FROM cita WHERE inicio::date = ? AND estado IN ('PENDIENTE','CONFIRMADA')) AS por_atender,
                  (SELECT count(*) FROM cita WHERE inicio::date = ? AND estado IN ('EN_ESPERA','EN_ATENCION')) AS en_sala,
                  (SELECT count(*) FROM cita WHERE inicio::date = ? AND estado = 'ATENDIDA')                 AS atendidas,
                  (SELECT count(*) FROM consulta WHERE fecha_hora::date = ?)                                 AS consultas,
                  (SELECT count(*) FROM consulta WHERE estado = 'ABIERTA')                                   AS abiertas,
                  (SELECT count(*) FROM venta WHERE estado = 'CONFIRMADA' AND fecha_hora::date = ?)          AS ventas,
                  (SELECT coalesce(sum(total), 0) FROM venta WHERE estado = 'CONFIRMADA' AND fecha_hora::date = ?) AS monto,
                  (SELECT count(*) FROM caja_turno WHERE estado = 'ABIERTA')                                 AS turnos,
                  (SELECT count(*) FROM v_alerta_inventario)                                                 AS alertas
                """, rs -> new ResumenPanel(rs.getInt("citas"), rs.getInt("por_atender"), rs.getInt("en_sala"),
                        rs.getInt("atendidas"), rs.getInt("consultas"), rs.getInt("abiertas"), rs.getInt("ventas"),
                        rs.getBigDecimal("monto"), rs.getInt("turnos"), rs.getInt("alertas")),
                dia, dia, dia, dia, dia, dia, dia).orElseThrow());
    }

    @Override
    public List<CitaPanel> citasDelDia(LocalDate dia, Integer idVeterinario) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT c.id_cita, c.inicio, m.nombre AS mascota, e.nombre AS especie,
                       cl.nombres || coalesce(' ' || cl.apellidos, '') AS cliente,
                       v.nombres || ' ' || v.apellidos AS veterinario, s.nombre AS servicio, c.estado::text AS estado
                FROM cita c
                JOIN mascota m ON m.id_mascota = c.id_mascota
                JOIN raza r ON r.id_raza = m.id_raza JOIN especie e ON e.id_especie = r.id_especie
                JOIN cliente cl ON cl.id_cliente = m.id_cliente
                JOIN usuario v ON v.id_usuario = c.id_veterinario
                JOIN servicio s ON s.id_servicio = c.id_servicio
                WHERE c.inicio::date = ? AND (?::int IS NULL OR c.id_veterinario = ?::int)
                ORDER BY c.inicio
                """, rs -> new CitaPanel(rs.getLong("id_cita"), Jdbc.fechaHora(rs, "inicio"), rs.getString("mascota"),
                        rs.getString("especie"), rs.getString("cliente"), rs.getString("veterinario"),
                        rs.getString("servicio"), rs.getString("estado")),
                dia, idVeterinario, idVeterinario));
    }

    @Override
    public List<PuntoSerie> ventasPorDia(LocalDate desde, LocalDate hasta) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT d::date AS dia, coalesce(sum(v.total), 0) AS total
                FROM generate_series(?::date, ?::date, interval '1 day') d
                LEFT JOIN venta v ON v.fecha_hora::date = d::date AND v.estado = 'CONFIRMADA'
                GROUP BY d ORDER BY d
                """, rs -> new PuntoSerie(Jdbc.fecha(rs, "dia"), rs.getBigDecimal("total"), 0), desde, hasta));
    }

    @Override
    public List<AlertaInventario> alertasInventario() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT codigo, nombre, alerta, stock, vence FROM v_alerta_inventario
                ORDER BY CASE alerta WHEN 'VENCIDO' THEN 0 WHEN 'STOCK_MINIMO' THEN 1 ELSE 2 END, vence NULLS LAST
                """, rs -> new AlertaInventario(rs.getString("codigo"), rs.getString("nombre"), rs.getString("alerta"),
                        rs.getBigDecimal("stock") == null ? BigDecimal.ZERO : rs.getBigDecimal("stock"),
                        Jdbc.fecha(rs, "vence"))));
    }
}
