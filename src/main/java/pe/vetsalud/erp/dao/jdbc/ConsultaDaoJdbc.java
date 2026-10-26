package pe.vetsalud.erp.dao.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;

import pe.vetsalud.erp.dao.ConsultaDao;
import pe.vetsalud.erp.modelo.Adenda;
import pe.vetsalud.erp.modelo.Consulta;
import pe.vetsalud.erp.modelo.ConsultaDatos;
import pe.vetsalud.erp.modelo.ConsultaFirma;
import pe.vetsalud.erp.modelo.ConsultaSignos;
import pe.vetsalud.erp.modelo.Diagnostico;
import pe.vetsalud.erp.modelo.ProcedimientoConsulta;
import pe.vetsalud.erp.modelo.ProductoReceta;
import pe.vetsalud.erp.modelo.Receta;
import pe.vetsalud.erp.modelo.RecetaLinea;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/**
 * Implementación JDBC de {@link ConsultaDao}. Protecciones en la BD: tg_consulta_proteger (firma solo del
 * veterinario con diagnóstico principal; firmada = inmutable), tg_*_proteger en diagnósticos, procedimientos y
 * recetas, uq_diagnostico_principal y tg_aud_consulta (auditoría con el usuario de la transacción).
 */
public class ConsultaDaoJdbc implements ConsultaDao {

    static final String SELECT_CONSULTA = """
            SELECT co.id_consulta, co.id_mascota, co.id_cita, co.id_veterinario,
                   v.nombres || ' ' || v.apellidos AS veterinario, v.colegiatura_cmvp, co.fecha_hora, co.motivo,
                   co.anamnesis, co.temperatura_c, co.frec_cardiaca, co.frec_respiratoria, co.peso_kg,
                   co.examen_clinico, co.indicaciones, co.fecha_control, co.estado, co.firmada_en
            FROM consulta co
            JOIN usuario v ON v.id_usuario = co.id_veterinario
            """;

    static final String SELECT_PROCEDIMIENTO = """
            SELECT p.id_proc_consulta, p.id_consulta, p.id_servicio, s.nombre AS servicio, p.id_responsable,
                   u.nombres || ' ' || u.apellidos AS responsable, p.cantidad, p.precio_unitario, p.resultado
            FROM consulta_procedimiento p
            JOIN servicio s ON s.id_servicio = p.id_servicio
            JOIN usuario u ON u.id_usuario = p.id_responsable
            """;

    static final String SELECT_LINEA = """
            SELECT d.id_receta_det, d.id_receta, d.id_producto, p.nombre AS producto, p.unidad, d.dosis, d.frecuencia,
                   d.duracion_dias, d.via, d.cantidad
            FROM receta_detalle d
            JOIN producto p ON p.id_producto = d.id_producto
            """;

    static final String SELECT_ADENDA = """
            SELECT a.id_adenda, a.id_consulta, a.id_usuario, u.nombres || ' ' || u.apellidos AS autor, a.texto, a.fecha_hora
            FROM adenda a
            JOIN usuario u ON u.id_usuario = a.id_usuario
            """;

    private final Db db;

    public ConsultaDaoJdbc(Db db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ mapeadores (compartidos con HistoriaDaoJdbc)

    static Consulta mapearConsulta(ResultSet rs) throws SQLException {
        return new Consulta(rs.getLong("id_consulta"), rs.getInt("id_mascota"), Jdbc.largo(rs, "id_cita"),
                rs.getInt("id_veterinario"), rs.getString("veterinario"), rs.getString("colegiatura_cmvp"),
                Jdbc.fechaHora(rs, "fecha_hora"), rs.getString("motivo"), rs.getString("anamnesis"),
                rs.getBigDecimal("temperatura_c"), Jdbc.entero(rs, "frec_cardiaca"), Jdbc.entero(rs, "frec_respiratoria"),
                rs.getBigDecimal("peso_kg"), rs.getString("examen_clinico"), rs.getString("indicaciones"),
                Jdbc.fecha(rs, "fecha_control"), rs.getString("estado"), Jdbc.fechaHora(rs, "firmada_en"));
    }

    static Diagnostico mapearDiagnostico(ResultSet rs) throws SQLException {
        return new Diagnostico(rs.getLong("id_diagnostico"), rs.getLong("id_consulta"), rs.getString("descripcion"),
                rs.getBoolean("es_principal"));
    }

    static ProcedimientoConsulta mapearProcedimiento(ResultSet rs) throws SQLException {
        return new ProcedimientoConsulta(rs.getLong("id_proc_consulta"), rs.getLong("id_consulta"), rs.getInt("id_servicio"),
                rs.getString("servicio"), rs.getInt("id_responsable"), rs.getString("responsable"), rs.getInt("cantidad"),
                rs.getBigDecimal("precio_unitario"), rs.getString("resultado"));
    }

    static RecetaLinea mapearLinea(ResultSet rs) throws SQLException {
        return new RecetaLinea(rs.getLong("id_receta_det"), rs.getLong("id_receta"), rs.getInt("id_producto"),
                rs.getString("producto"), rs.getString("unidad"), rs.getString("dosis"), rs.getString("frecuencia"),
                rs.getInt("duracion_dias"), rs.getString("via"), rs.getBigDecimal("cantidad"));
    }

    static Adenda mapearAdenda(ResultSet rs) throws SQLException {
        return new Adenda(rs.getLong("id_adenda"), rs.getLong("id_consulta"), rs.getInt("id_usuario"), rs.getString("autor"),
                rs.getString("texto"), Jdbc.fechaHora(rs, "fecha_hora"));
    }

    /**
     * Recetas con sus líneas de una consulta o de todas las consultas de una mascota. Las líneas se leen en una
     * sola consulta adicional y se agrupan por receta con Guava. Las dos condiciones son fijas (sin datos del usuario).
     */
    static List<Receta> recetas(Connection cn, boolean porMascota, long id) throws SQLException {
        String condicion = porMascota ? " WHERE co.id_mascota = ?" : " WHERE r.id_consulta = ?";
        List<Receta> cabeceras = Jdbc.lista(cn, """
                SELECT r.id_receta, r.id_consulta, r.fecha, r.indicaciones
                FROM receta r JOIN consulta co ON co.id_consulta = r.id_consulta
                """ + condicion + " ORDER BY r.fecha, r.id_receta",
                rs -> new Receta(rs.getLong("id_receta"), rs.getLong("id_consulta"), Jdbc.fechaHora(rs, "fecha"),
                        rs.getString("indicaciones"), List.of()), id);
        if (cabeceras.isEmpty()) {
            return cabeceras;
        }
        List<RecetaLinea> lineas = Jdbc.lista(cn, SELECT_LINEA
                + " JOIN receta r ON r.id_receta = d.id_receta JOIN consulta co ON co.id_consulta = r.id_consulta"
                + condicion + " ORDER BY d.id_receta_det", ConsultaDaoJdbc::mapearLinea, id);
        ImmutableListMultimap<Long, RecetaLinea> porReceta = Multimaps.index(lineas, RecetaLinea::idReceta);
        return cabeceras.stream().map(r -> new Receta(r.idReceta(), r.idConsulta(), r.fecha(), r.indicaciones(),
                porReceta.get(r.idReceta()))).toList();
    }

    // ------------------------------------------------------------------ consulta

    @Override
    public Optional<Consulta> buscarPorId(long idConsulta) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT_CONSULTA + " WHERE co.id_consulta = ?",
                ConsultaDaoJdbc::mapearConsulta, idConsulta));
    }

    @Override
    public Optional<Consulta> buscarPorCita(long idCita) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT_CONSULTA + " WHERE co.id_cita = ?",
                ConsultaDaoJdbc::mapearConsulta, idCita));
    }

    @Override
    public long insertar(int idMascota, Long idCita, int idVeterinario, String motivo, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.insertar(cn, """
                INSERT INTO consulta (id_mascota, id_cita, id_veterinario, motivo)
                VALUES (?, ?::bigint, ?, ?) RETURNING id_consulta
                """, idMascota, idCita, idVeterinario, motivo));
    }

    @Override
    public boolean actualizarDatos(long idConsulta, ConsultaDatos d, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                UPDATE consulta SET motivo = ?, anamnesis = ?, examen_clinico = ?, indicaciones = ?, fecha_control = ?::date
                WHERE id_consulta = ? AND estado = 'ABIERTA'
                """, d.motivo(), d.anamnesis(), d.examenClinico(), d.indicaciones(), d.fechaControl(), idConsulta) == 1);
    }

    @Override
    public boolean actualizarSignos(long idConsulta, ConsultaSignos s, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                UPDATE consulta SET temperatura_c = ?::numeric, frec_cardiaca = ?::smallint,
                       frec_respiratoria = ?::smallint, peso_kg = ?::numeric
                WHERE id_consulta = ? AND estado = 'ABIERTA'
                """, s.temperaturaC(), s.frecCardiaca(), s.frecRespiratoria(), s.pesoKg(), idConsulta) == 1);
    }

    // ------------------------------------------------------------------ diagnósticos y procedimientos

    @Override
    public List<Diagnostico> diagnosticos(long idConsulta) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT id_diagnostico, id_consulta, descripcion, es_principal FROM consulta_diagnostico
                WHERE id_consulta = ? ORDER BY es_principal DESC, id_diagnostico
                """, ConsultaDaoJdbc::mapearDiagnostico, idConsulta));
    }

    @Override
    public long insertarDiagnostico(long idConsulta, String descripcion, boolean principal, int idUsuario)
            throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.insertar(cn, """
                INSERT INTO consulta_diagnostico (id_consulta, descripcion, es_principal)
                VALUES (?, ?, ?) RETURNING id_diagnostico
                """, idConsulta, descripcion, principal));
    }

    @Override
    public List<ProcedimientoConsulta> procedimientos(long idConsulta) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT_PROCEDIMIENTO + " WHERE p.id_consulta = ? ORDER BY p.id_proc_consulta",
                ConsultaDaoJdbc::mapearProcedimiento, idConsulta));
    }

    @Override
    public long insertarProcedimiento(ProcedimientoConsulta p, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.insertar(cn, """
                INSERT INTO consulta_procedimiento (id_consulta, id_servicio, id_responsable, cantidad, precio_unitario, resultado)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id_proc_consulta
                """, p.idConsulta(), p.idServicio(), p.idResponsable(), p.cantidad(), p.precioUnitario(), p.resultado()));
    }

    // ------------------------------------------------------------------ receta

    @Override
    public Optional<Receta> receta(long idConsulta) throws SQLException {
        return db.consultar(cn -> recetas(cn, false, idConsulta).stream().findFirst());
    }

    @Override
    public long insertarReceta(long idConsulta, String indicaciones, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.insertar(cn,
                "INSERT INTO receta (id_consulta, indicaciones) VALUES (?, ?) RETURNING id_receta", idConsulta, indicaciones));
    }

    @Override
    public void actualizarReceta(long idReceta, String indicaciones, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, "UPDATE receta SET indicaciones = ? WHERE id_receta = ?",
                indicaciones, idReceta));
    }

    @Override
    public long insertarLineaReceta(long idConsulta, RecetaLinea l, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> {
            Long idReceta = Jdbc.escalar(cn, "SELECT min(id_receta) FROM receta WHERE id_consulta = ?", Long.class, idConsulta);
            if (idReceta == null) {
                idReceta = Jdbc.insertar(cn, "INSERT INTO receta (id_consulta) VALUES (?) RETURNING id_receta", idConsulta);
            }
            return Jdbc.insertar(cn, """
                    INSERT INTO receta_detalle (id_receta, id_producto, dosis, frecuencia, duracion_dias, via, cantidad)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_receta_det
                    """, idReceta, l.idProducto(), l.dosis(), l.frecuencia(), l.duracionDias(), l.via(), l.cantidad());
        });
    }

    @Override
    public List<ProductoReceta> productosActivos() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT id_producto, codigo, nombre, unidad, requiere_receta, activo FROM producto
                WHERE activo ORDER BY requiere_receta DESC, nombre
                """, ConsultaDaoJdbc::mapearProducto));
    }

    @Override
    public Optional<ProductoReceta> producto(int idProducto) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, """
                SELECT id_producto, codigo, nombre, unidad, requiere_receta, activo FROM producto WHERE id_producto = ?
                """, ConsultaDaoJdbc::mapearProducto, idProducto));
    }

    private static ProductoReceta mapearProducto(ResultSet rs) throws SQLException {
        return new ProductoReceta(rs.getInt("id_producto"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("unidad"), rs.getBoolean("requiere_receta"), rs.getBoolean("activo"));
    }

    // ------------------------------------------------------------------ adendas y firma

    @Override
    public List<Adenda> adendas(long idConsulta) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, SELECT_ADENDA + " WHERE a.id_consulta = ? ORDER BY a.fecha_hora, a.id_adenda",
                ConsultaDaoJdbc::mapearAdenda, idConsulta));
    }

    @Override
    public long insertarAdenda(long idConsulta, String texto, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> Jdbc.insertar(cn,
                "INSERT INTO adenda (id_consulta, id_usuario, texto) VALUES (?, ?, ?) RETURNING id_adenda",
                idConsulta, idUsuario, texto));
    }

    @Override
    public boolean firmar(ConsultaFirma f, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> {
            // tg_consulta_proteger exige veterinario activo y diagnóstico principal, y fija firmada_en
            if (Jdbc.ejecutar(cn, "UPDATE consulta SET estado = 'FIRMADA' WHERE id_consulta = ? AND estado = 'ABIERTA'",
                    f.idConsulta()) != 1) {
                return false;
            }
            if (f.conRecordatorio()) {
                Jdbc.ejecutar(cn, "INSERT INTO recordatorio (id_cliente, id_consulta, programado_para) VALUES (?, ?, ?)",
                        f.idClienteRecordatorio(), f.idConsulta(), f.recordatorioPara());
            }
            if (f.idCitaAtendida() != null) {
                Jdbc.ejecutar(cn, "UPDATE cita SET estado = 'ATENDIDA' WHERE id_cita = ? AND estado = 'EN_ATENCION'",
                        f.idCitaAtendida());
            }
            return true;
        });
    }
}
