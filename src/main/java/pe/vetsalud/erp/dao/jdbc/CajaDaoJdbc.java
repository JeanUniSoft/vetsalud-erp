package pe.vetsalud.erp.dao.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.dao.CajaDao;
import pe.vetsalud.erp.modelo.ResumenTurno;
import pe.vetsalud.erp.modelo.TurnoCaja;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link CajaDao}. El cierre lo hace fn_cerrar_caja (RN-10) y la auditoría del turno
 * el trigger tg_aud_turno.
 */
public class CajaDaoJdbc implements CajaDao {

    private static final String SELECT = """
            SELECT t.id_turno, t.id_cajero, u.nombres || ' ' || u.apellidos AS cajero, t.apertura, t.monto_inicial,
                   t.cierre, t.efectivo_esperado, t.efectivo_contado, t.diferencia, t.observacion, t.estado
            FROM caja_turno t JOIN usuario u ON u.id_usuario = t.id_cajero
            """;

    private final Db db;

    public CajaDaoJdbc(Db db) {
        this.db = db;
    }

    private static TurnoCaja mapear(ResultSet rs) throws SQLException {
        return new TurnoCaja(rs.getInt("id_turno"), rs.getInt("id_cajero"), rs.getString("cajero"),
                Jdbc.fechaHora(rs, "apertura"), rs.getBigDecimal("monto_inicial"), Jdbc.fechaHora(rs, "cierre"),
                rs.getBigDecimal("efectivo_esperado"), rs.getBigDecimal("efectivo_contado"),
                rs.getBigDecimal("diferencia"), rs.getString("observacion"), rs.getString("estado"));
    }

    @Override
    public Optional<TurnoCaja> turnoAbierto(int idCajero) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE t.id_cajero = ? AND t.estado = 'ABIERTA'",
                CajaDaoJdbc::mapear, idCajero));
    }

    @Override
    public Optional<TurnoCaja> buscarPorId(int idTurno) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE t.id_turno = ?", CajaDaoJdbc::mapear, idTurno));
    }

    @Override
    public int abrir(int idCajero, BigDecimal montoInicial, int idUsuario) throws SQLException {
        // uq_turno_abierto impide un segundo turno abierto aunque dos pestañas envíen a la vez (RN-10)
        return db.enTransaccion(idUsuario, cn -> (int) Jdbc.insertar(cn,
                "INSERT INTO caja_turno (id_cajero, monto_inicial) VALUES (?, ?) RETURNING id_turno",
                idCajero, montoInicial));
    }

    @Override
    public ResumenTurno resumen(int idTurno) throws SQLException {
        return db.consultar(cn -> {
            TurnoCaja turno = Jdbc.uno(cn, SELECT + " WHERE t.id_turno = ?", CajaDaoJdbc::mapear, idTurno)
                    .orElseThrow(() -> new IllegalArgumentException("El turno de caja no existe."));
            List<ResumenTurno.PorMedio> medios = Jdbc.lista(cn, """
                    SELECT m.medio::text AS medio, count(p.id_pago) AS pagos, coalesce(sum(p.monto), 0) AS monto
                    FROM unnest(enum_range(NULL::medio_pago)) AS m(medio)
                    LEFT JOIN (pago p JOIN venta v ON v.id_venta = p.id_venta
                                                  AND v.id_turno = ? AND v.estado = 'CONFIRMADA')
                           ON p.medio = m.medio
                    GROUP BY m.medio ORDER BY m.medio
                    """, rs -> new ResumenTurno.PorMedio(rs.getString("medio"), rs.getInt("pagos"),
                            rs.getBigDecimal("monto")), idTurno);
            return Jdbc.uno(cn, """
                    SELECT count(*) FILTER (WHERE v.estado = 'CONFIRMADA') AS confirmadas,
                           coalesce(sum(v.total) FILTER (WHERE v.estado = 'CONFIRMADA'), 0) AS total_confirmado,
                           coalesce(sum(v.descuento) FILTER (WHERE v.estado = 'CONFIRMADA'), 0) AS descuentos,
                           count(*) FILTER (WHERE v.estado = 'ANULADA') AS anuladas,
                           coalesce(sum(v.total) FILTER (WHERE v.estado = 'ANULADA'), 0) AS total_anulado,
                           coalesce((SELECT sum(p.monto) FROM pago p JOIN venta va ON va.id_venta = p.id_venta
                                     WHERE va.id_turno = ? AND va.estado = 'ANULADA' AND p.medio = 'EFECTIVO'), 0)
                               AS efectivo_devuelto,
                           count(*) FILTER (WHERE v.estado = 'PENDIENTE') AS pendientes,
                           ? + coalesce((SELECT sum(p.monto) FROM pago p JOIN venta vc ON vc.id_venta = p.id_venta
                                         WHERE vc.id_turno = ? AND vc.estado = 'CONFIRMADA'
                                           AND p.medio = 'EFECTIVO'), 0) AS esperado
                    FROM venta v WHERE v.id_turno = ?
                    """, rs -> new ResumenTurno(turno, medios, rs.getInt("confirmadas"),
                            rs.getBigDecimal("total_confirmado"), rs.getBigDecimal("descuentos"), rs.getInt("anuladas"),
                            rs.getBigDecimal("total_anulado"), rs.getBigDecimal("efectivo_devuelto"),
                            rs.getInt("pendientes"), rs.getBigDecimal("esperado")),
                    idTurno, turno.montoInicial(), idTurno, idTurno).orElseThrow();
        });
    }

    @Override
    public BigDecimal cerrar(int idTurno, BigDecimal efectivoContado, String observacion, int idUsuario)
            throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.escalar(cn,
                "SELECT fn_cerrar_caja(?::int, ?::numeric, ?::varchar)", BigDecimal.class, idTurno, efectivoContado,
                observacion));
    }

    @Override
    public List<TurnoCaja> historial(Integer idCajero, LocalDate desde, LocalDate hasta, int limite)
            throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                 WHERE (?::int IS NULL OR t.id_cajero = ?::int)
                   AND (?::date IS NULL OR t.apertura >= ?::date)
                   AND (?::date IS NULL OR t.apertura < ?::date + 1)
                 ORDER BY t.apertura DESC LIMIT ?
                """, CajaDaoJdbc::mapear, idCajero, idCajero, desde, desde, hasta, hasta, limite));
    }

    @Override
    public List<TurnoCaja> abiertos() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + " WHERE t.estado = 'ABIERTA' ORDER BY t.apertura",
                CajaDaoJdbc::mapear));
    }
}
