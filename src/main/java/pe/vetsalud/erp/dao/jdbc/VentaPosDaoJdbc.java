package pe.vetsalud.erp.dao.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.dao.VentaPosDao;
import pe.vetsalud.erp.modelo.ConsultaPorCobrar;
import pe.vetsalud.erp.modelo.LineaVenta;
import pe.vetsalud.erp.modelo.MascotaVenta;
import pe.vetsalud.erp.modelo.Pago;
import pe.vetsalud.erp.modelo.ProductoRecetado;
import pe.vetsalud.erp.modelo.ServicioVendible;
import pe.vetsalud.erp.modelo.Venta;
import pe.vetsalud.erp.modelo.VentaFiltro;
import pe.vetsalud.erp.modelo.VentaTotales;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link VentaPosDao}. Confirmar y anular delegan en las funciones de PostgreSQL
 * (fn_confirmar_venta, fn_anular_venta), que hacen la operación completa en una transacción (RNF-08).
 * La auditoría de la venta la registra el trigger tg_aud_venta.
 */
public class VentaPosDaoJdbc implements VentaPosDao {

    private static final String SERIE_TICKET = "T001";

    private static final String SELECT = """
            SELECT v.id_venta, v.id_turno, t.id_cajero, uc.nombres || ' ' || uc.apellidos AS cajero,
                   t.estado = 'ABIERTA' AS turno_abierto, v.id_cliente,
                   cl.nombres || coalesce(' ' || cl.apellidos, '') AS cliente,
                   cl.tipo_documento::text || ' ' || cl.num_documento AS documento,
                   v.id_mascota, m.nombre AS mascota, v.id_consulta, v.serie, v.numero, v.fecha_hora,
                   v.subtotal, v.descuento, v.total, v.id_autoriza_descuento,
                   ua.nombres || ' ' || ua.apellidos AS autoriza, v.estado::text AS estado,
                   v.motivo_anulacion, v.anulada_en,
                   coalesce((SELECT sum(p.monto) FROM pago p WHERE p.id_venta = v.id_venta), 0) AS pagado,
                   (SELECT count(*) FROM venta_detalle d WHERE d.id_venta = v.id_venta) AS num_lineas
            FROM venta v
            JOIN caja_turno t ON t.id_turno = v.id_turno
            JOIN usuario uc ON uc.id_usuario = t.id_cajero
            JOIN cliente cl ON cl.id_cliente = v.id_cliente
            LEFT JOIN mascota m ON m.id_mascota = v.id_mascota
            LEFT JOIN usuario ua ON ua.id_usuario = v.id_autoriza_descuento
            """;

    private static final String SELECT_CONSULTA = """
            SELECT c.id_consulta, c.fecha_hora, c.id_mascota, m.nombre AS mascota, m.id_cliente,
                   cl.nombres || coalesce(' ' || cl.apellidos, '') AS cliente,
                   u.nombres || ' ' || u.apellidos AS veterinario, c.motivo, d.descripcion AS diagnostico,
                   (SELECT count(*) FROM consulta_procedimiento cp WHERE cp.id_consulta = c.id_consulta) AS procedimientos,
                   EXISTS (SELECT 1 FROM receta r JOIN receta_detalle rd ON rd.id_receta = r.id_receta
                           WHERE r.id_consulta = c.id_consulta) AS tiene_receta
            FROM consulta c
            JOIN mascota m ON m.id_mascota = c.id_mascota
            JOIN cliente cl ON cl.id_cliente = m.id_cliente
            JOIN usuario u ON u.id_usuario = c.id_veterinario
            LEFT JOIN consulta_diagnostico d ON d.id_consulta = c.id_consulta AND d.es_principal
            WHERE c.estado = 'FIRMADA'
              AND NOT EXISTS (SELECT 1 FROM venta v WHERE v.id_consulta = c.id_consulta AND v.estado <> 'ANULADA')
            """;

    private final Db db;

    public VentaPosDaoJdbc(Db db) {
        this.db = db;
    }

    private static Venta mapear(ResultSet rs) throws SQLException {
        return new Venta(rs.getLong("id_venta"), rs.getInt("id_turno"), rs.getInt("id_cajero"), rs.getString("cajero"),
                rs.getBoolean("turno_abierto"), rs.getInt("id_cliente"), rs.getString("cliente"),
                rs.getString("documento"), Jdbc.entero(rs, "id_mascota"), rs.getString("mascota"),
                Jdbc.largo(rs, "id_consulta"), rs.getString("serie"), Jdbc.entero(rs, "numero"),
                Jdbc.fechaHora(rs, "fecha_hora"), rs.getBigDecimal("subtotal"), rs.getBigDecimal("descuento"),
                rs.getBigDecimal("total"), Jdbc.entero(rs, "id_autoriza_descuento"), rs.getString("autoriza"),
                rs.getString("estado"), rs.getString("motivo_anulacion"), Jdbc.fechaHora(rs, "anulada_en"),
                rs.getBigDecimal("pagado"), rs.getInt("num_lineas"));
    }

    private static LineaVenta mapearLinea(ResultSet rs) throws SQLException {
        Integer idServicio = Jdbc.entero(rs, "id_servicio");
        return new LineaVenta(rs.getLong("id_detalle"), idServicio, Jdbc.entero(rs, "id_lote"),
                Jdbc.entero(rs, "id_producto"), rs.getString("codigo"),
                idServicio != null ? rs.getString("servicio") : rs.getString("producto"), rs.getString("codigo_lote"),
                Jdbc.fecha(rs, "fecha_vencimiento"), rs.getBigDecimal("cantidad"), rs.getBigDecimal("precio_unitario"),
                rs.getBigDecimal("importe"));
    }

    private static ConsultaPorCobrar mapearConsulta(ResultSet rs) throws SQLException {
        return new ConsultaPorCobrar(rs.getLong("id_consulta"), Jdbc.fechaHora(rs, "fecha_hora"), rs.getInt("id_mascota"),
                rs.getString("mascota"), rs.getInt("id_cliente"), rs.getString("cliente"), rs.getString("veterinario"),
                rs.getString("motivo"), rs.getString("diagnostico"), rs.getInt("procedimientos"),
                rs.getBoolean("tiene_receta"));
    }

    private static ServicioVendible mapearServicio(ResultSet rs) throws SQLException {
        return new ServicioVendible(rs.getInt("id_servicio"), rs.getString("nombre"), rs.getString("tipo"),
                rs.getBigDecimal("tarifa"));
    }

    // ------------------------------------------------------------------ venta

    @Override
    public long crear(int idTurno, int idCliente, Integer idMascota, Long idConsulta, List<LineaVenta> lineas,
                      BigDecimal subtotal, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> {
            long id = Jdbc.insertar(cn, """
                    INSERT INTO venta (id_turno, id_cliente, id_mascota, id_consulta, serie, subtotal, total)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_venta
                    """, idTurno, idCliente, idMascota, idConsulta, SERIE_TICKET, subtotal, subtotal);
            insertarLineas(cn, id, lineas);
            return id;
        });
    }

    private static void insertarLineas(Connection cn, long idVenta, List<LineaVenta> lineas) throws SQLException {
        for (LineaVenta l : lineas) {
            Jdbc.ejecutar(cn, """
                    INSERT INTO venta_detalle (id_venta, id_servicio, id_lote, cantidad, precio_unitario)
                    VALUES (?, ?, ?, ?, ?)
                    """, idVenta, l.idServicio(), l.idLote(), l.cantidad(), l.precioUnitario());
        }
    }

    private static void guardarTotales(Connection cn, long idVenta, VentaTotales t) throws SQLException {
        int filas = Jdbc.ejecutar(cn, """
                UPDATE venta SET subtotal = ?, descuento = ?, total = ?, id_autoriza_descuento = ?
                WHERE id_venta = ? AND estado = 'PENDIENTE'
                """, t.subtotal(), t.descuento(), t.total(), t.idAutoriza(), idVenta);
        if (filas != 1) {
            // Otro usuario la confirmó o anuló mientras se editaba: se revierte toda la transacción
            throw new IllegalArgumentException("La venta ya no está pendiente; recargue la pantalla.");
        }
    }

    @Override
    public Optional<Venta> buscarPorId(long idVenta) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE v.id_venta = ?", VentaPosDaoJdbc::mapear, idVenta));
    }

    @Override
    public List<Venta> listar(VentaFiltro f, int limite) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                 WHERE (?::date IS NULL OR v.fecha_hora >= ?::date)
                   AND (?::date IS NULL OR v.fecha_hora < ?::date + 1)
                   AND (?::text IS NULL OR v.estado = ?::estado_venta)
                   AND (?::int IS NULL OR t.id_cajero = ?::int)
                   AND (?::int IS NULL OR v.id_turno = ?::int)
                 ORDER BY v.estado = 'PENDIENTE' DESC, v.fecha_hora DESC LIMIT ?
                """, VentaPosDaoJdbc::mapear, f.desde(), f.desde(), f.hasta(), f.hasta(), f.estado(), f.estado(),
                f.idCajero(), f.idCajero(), f.idTurno(), f.idTurno(), limite));
    }

    @Override
    public List<LineaVenta> lineas(long idVenta) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT d.id_detalle, d.id_servicio, d.id_lote, s.nombre AS servicio, p.id_producto, p.codigo,
                       p.nombre AS producto, l.codigo_lote, l.fecha_vencimiento, d.cantidad, d.precio_unitario, d.importe
                FROM venta_detalle d
                LEFT JOIN servicio s ON s.id_servicio = d.id_servicio
                LEFT JOIN lote l ON l.id_lote = d.id_lote
                LEFT JOIN producto p ON p.id_producto = l.id_producto
                WHERE d.id_venta = ? ORDER BY d.id_detalle
                """, VentaPosDaoJdbc::mapearLinea, idVenta));
    }

    @Override
    public List<Pago> pagos(long idVenta) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT id_pago, id_venta, medio::text AS medio, monto, referencia, fecha_hora
                FROM pago WHERE id_venta = ? ORDER BY id_pago
                """, rs -> new Pago(rs.getLong("id_pago"), rs.getLong("id_venta"), rs.getString("medio"),
                        rs.getBigDecimal("monto"), rs.getString("referencia"), Jdbc.fechaHora(rs, "fecha_hora")),
                idVenta));
    }

    @Override
    public void agregarLineas(long idVenta, List<LineaVenta> lineas, VentaTotales totales, int idUsuario)
            throws SQLException {
        db.enTransaccion(idUsuario, cn -> {
            insertarLineas(cn, idVenta, lineas);
            guardarTotales(cn, idVenta, totales);
            return null;
        });
    }

    @Override
    public boolean quitarLinea(long idVenta, long idDetalle, VentaTotales totales, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> {
            int filas = Jdbc.ejecutar(cn, "DELETE FROM venta_detalle WHERE id_detalle = ? AND id_venta = ?",
                    idDetalle, idVenta);
            if (filas == 1) {
                guardarTotales(cn, idVenta, totales);
            }
            return filas == 1;
        });
    }

    @Override
    public void actualizarTotales(long idVenta, VentaTotales totales, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> {
            guardarTotales(cn, idVenta, totales);
            return null;
        });
    }

    @Override
    public long agregarPago(Pago p, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.insertar(cn, """
                INSERT INTO pago (id_venta, medio, monto, referencia)
                SELECT ?, ?::medio_pago, ?, ? WHERE EXISTS (SELECT 1 FROM venta WHERE id_venta = ? AND estado = 'PENDIENTE')
                RETURNING id_pago
                """, p.idVenta(), p.medio(), p.monto(), p.referencia(), p.idVenta()));
    }

    @Override
    public boolean quitarPago(long idVenta, long idPago, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                DELETE FROM pago WHERE id_pago = ? AND id_venta = ?
                  AND EXISTS (SELECT 1 FROM venta WHERE id_venta = ? AND estado = 'PENDIENTE')
                """, idPago, idVenta, idVenta) == 1);
    }

    @Override
    public String confirmar(long idVenta, BigDecimal descuento, Integer idAutoriza, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.escalar(cn,
                "SELECT fn_confirmar_venta(?::bigint, ?::numeric, ?::int)", String.class, idVenta, descuento,
                idAutoriza));
    }

    @Override
    public void anular(long idVenta, String motivo, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.escalar(cn, "SELECT 1 FROM fn_anular_venta(?::bigint, ?::varchar)",
                Integer.class, idVenta, motivo));
    }

    @Override
    public void descartar(long idVenta, int idUsuario) throws SQLException {
        // La función es SECURITY DEFINER: la app no tiene DELETE sobre venta; tg_aud_venta audita el borrado
        db.enTransaccion(idUsuario, cn -> Jdbc.escalar(cn, "SELECT 1 FROM fn_descartar_venta(?::bigint)",
                Integer.class, idVenta));
    }

    // ------------------------------------------------------------------ datos de apoyo

    @Override
    public boolean clienteActivo(int idCliente) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn,
                "SELECT EXISTS (SELECT 1 FROM cliente WHERE id_cliente = ? AND activo)", Boolean.class, idCliente));
    }

    @Override
    public List<ServicioVendible> serviciosVendibles() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn,
                "SELECT id_servicio, nombre, tipo, tarifa FROM servicio WHERE activo ORDER BY tipo, nombre",
                VentaPosDaoJdbc::mapearServicio));
    }

    @Override
    public Optional<ServicioVendible> servicio(int idServicio) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn,
                "SELECT id_servicio, nombre, tipo, tarifa FROM servicio WHERE id_servicio = ? AND activo",
                VentaPosDaoJdbc::mapearServicio, idServicio));
    }

    @Override
    public List<MascotaVenta> mascotasDeCliente(int idCliente) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT m.id_mascota, m.nombre, e.nombre AS especie, r.nombre AS raza
                FROM mascota m JOIN raza r ON r.id_raza = m.id_raza JOIN especie e ON e.id_especie = r.id_especie
                WHERE m.id_cliente = ? AND m.estado = 'ACTIVA' ORDER BY m.nombre
                """, rs -> new MascotaVenta(rs.getInt("id_mascota"), rs.getString("nombre"), rs.getString("especie"),
                        rs.getString("raza")), idCliente));
    }

    @Override
    public List<ConsultaPorCobrar> consultasPorCobrar(Integer idCliente) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT_CONSULTA
                + " AND (?::int IS NULL OR m.id_cliente = ?::int) ORDER BY c.fecha_hora DESC LIMIT 50",
                VentaPosDaoJdbc::mapearConsulta, idCliente, idCliente));
    }

    @Override
    public Optional<ConsultaPorCobrar> consultaPorCobrar(long idConsulta) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT_CONSULTA + " AND c.id_consulta = ?",
                VentaPosDaoJdbc::mapearConsulta, idConsulta));
    }

    @Override
    public Optional<ServicioVendible> servicioBaseDeConsulta(long idConsulta) throws SQLException {
        return db.consultar(cn -> {
            Optional<ServicioVendible> deCita = Jdbc.uno(cn, """
                    SELECT s.id_servicio, s.nombre, s.tipo, s.tarifa
                    FROM consulta c JOIN cita ci ON ci.id_cita = c.id_cita JOIN servicio s ON s.id_servicio = ci.id_servicio
                    WHERE c.id_consulta = ?
                    """, VentaPosDaoJdbc::mapearServicio, idConsulta);
            if (deCita.isPresent()) {
                return deCita;
            }
            return Jdbc.uno(cn, """
                    SELECT id_servicio, nombre, tipo, tarifa FROM servicio
                    WHERE tipo = 'CONSULTA' AND activo ORDER BY nombre = 'Consulta general' DESC, id_servicio LIMIT 1
                    """, VentaPosDaoJdbc::mapearServicio);
        });
    }

    @Override
    public List<LineaVenta> procedimientosDeConsulta(long idConsulta) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT cp.id_servicio, s.nombre, cp.cantidad, cp.precio_unitario
                FROM consulta_procedimiento cp JOIN servicio s ON s.id_servicio = cp.id_servicio
                WHERE cp.id_consulta = ? ORDER BY cp.id_proc_consulta
                """, rs -> LineaVenta.servicio(rs.getInt("id_servicio"), rs.getString("nombre"),
                        rs.getBigDecimal("cantidad"), rs.getBigDecimal("precio_unitario")), idConsulta));
    }

    @Override
    public List<ProductoRecetado> recetaDeConsulta(long idConsulta) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT x.*, l.codigo_lote AS lote_sugerido
                FROM (SELECT rd.id_producto, p.codigo, p.nombre, rd.cantidad, rd.dosis, rd.frecuencia, rd.duracion_dias,
                             p.precio_venta, fn_lote_sugerido(rd.id_producto, rd.cantidad) AS id_lote_sugerido,
                             coalesce((SELECT sum(l2.stock_actual) FROM lote l2 WHERE l2.id_producto = rd.id_producto
                                         AND (l2.fecha_vencimiento IS NULL OR l2.fecha_vencimiento >= CURRENT_DATE)), 0)
                                 AS disponible, rd.id_receta_det
                      FROM receta r
                      JOIN receta_detalle rd ON rd.id_receta = r.id_receta
                      JOIN producto p ON p.id_producto = rd.id_producto
                      WHERE r.id_consulta = ?) x
                LEFT JOIN lote l ON l.id_lote = x.id_lote_sugerido
                ORDER BY x.id_receta_det
                """, rs -> new ProductoRecetado(rs.getInt("id_producto"), rs.getString("codigo"), rs.getString("nombre"),
                        rs.getBigDecimal("cantidad"), rs.getString("dosis"), rs.getString("frecuencia"),
                        rs.getInt("duracion_dias"), rs.getBigDecimal("precio_venta"), Jdbc.entero(rs, "id_lote_sugerido"),
                        rs.getString("lote_sugerido"), rs.getBigDecimal("disponible")), idConsulta));
    }

    @Override
    public boolean recetaIncluye(long idConsulta, int idProducto) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn, """
                SELECT EXISTS (SELECT 1 FROM receta r JOIN receta_detalle rd ON rd.id_receta = r.id_receta
                               WHERE r.id_consulta = ? AND rd.id_producto = ?)
                """, Boolean.class, idConsulta, idProducto));
    }
}
