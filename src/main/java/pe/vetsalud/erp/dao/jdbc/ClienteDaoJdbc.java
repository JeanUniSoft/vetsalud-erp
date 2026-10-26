package pe.vetsalud.erp.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import pe.vetsalud.erp.dao.ClienteDao;
import pe.vetsalud.erp.modelo.Cliente;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link ClienteDao}. La auditoría la registra el trigger tg_aud_cliente. */
public class ClienteDaoJdbc implements ClienteDao {

    private static final String SELECT = """
            SELECT c.id_cliente, c.tipo_documento::text AS tipo_documento, c.num_documento, c.nombres, c.apellidos,
                   c.telefono, c.correo, c.direccion, c.distrito, c.acepta_recordatorios, c.activo, c.creado_en,
                   (SELECT count(*) FROM mascota m WHERE m.id_cliente = c.id_cliente AND m.estado = 'ACTIVA') AS num_mascotas
            FROM cliente c
            """;

    private final Db db;

    public ClienteDaoJdbc(Db db) {
        this.db = db;
    }

    static Cliente mapear(ResultSet rs) throws SQLException {
        return new Cliente(rs.getInt("id_cliente"), rs.getString("tipo_documento"), rs.getString("num_documento"),
                rs.getString("nombres"), rs.getString("apellidos"), rs.getString("telefono"), rs.getString("correo"),
                rs.getString("direccion"), rs.getString("distrito"), rs.getBoolean("acepta_recordatorios"),
                rs.getBoolean("activo"), Jdbc.fechaHora(rs, "creado_en"), rs.getInt("num_mascotas"));
    }

    @Override
    public List<Cliente> buscar(String texto, int limite) throws SQLException {
        if (StringUtils.isBlank(texto)) {
            return db.consultar(cn -> Jdbc.lista(cn, SELECT + " ORDER BY c.creado_en DESC LIMIT ?",
                    ClienteDaoJdbc::mapear, limite));
        }
        String patron = "%" + texto.trim() + "%";
        // El índice trigram (ix_cliente_nombre_trgm) acelera la búsqueda por nombre (RNF-01)
        return db.consultar(cn -> Jdbc.lista(cn, SELECT + """
                 WHERE c.num_documento = ? OR c.telefono LIKE ? OR c.correo ILIKE ?
                    OR (c.nombres || ' ' || coalesce(c.apellidos, '')) ILIKE ?
                 ORDER BY c.nombres, c.apellidos LIMIT ?
                """, ClienteDaoJdbc::mapear, texto.trim(), patron, patron, patron, limite));
    }

    @Override
    public Optional<Cliente> buscarPorId(int idCliente) throws SQLException {
        return db.consultar(cn -> Jdbc.uno(cn, SELECT + " WHERE c.id_cliente = ?", ClienteDaoJdbc::mapear, idCliente));
    }

    @Override
    public boolean existeDocumento(String tipoDocumento, String numDocumento, Integer exceptoId) throws SQLException {
        return db.consultar(cn -> Jdbc.escalar(cn, """
                SELECT EXISTS (SELECT 1 FROM cliente WHERE tipo_documento = ?::tipo_documento AND num_documento = ?
                               AND (?::int IS NULL OR id_cliente <> ?::int))
                """, Boolean.class, tipoDocumento, numDocumento, exceptoId, exceptoId));
    }

    @Override
    public int insertar(Cliente c, int idUsuario) throws SQLException {
        return db.enTransaccion(idUsuario, cn -> (int) Jdbc.insertar(cn, """
                INSERT INTO cliente (tipo_documento, num_documento, nombres, apellidos, telefono, correo,
                                     direccion, distrito, acepta_recordatorios)
                VALUES (?::tipo_documento, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_cliente
                """, c.tipoDocumento(), c.numDocumento(), c.nombres(), c.apellidos(), c.telefono(), c.correo(),
                c.direccion(), c.distrito(), c.aceptaRecordatorios()));
    }

    @Override
    public void actualizar(Cliente c, int idUsuario) throws SQLException {
        db.enTransaccion(idUsuario, cn -> Jdbc.ejecutar(cn, """
                UPDATE cliente SET tipo_documento = ?::tipo_documento, num_documento = ?, nombres = ?, apellidos = ?,
                       telefono = ?, correo = ?, direccion = ?, distrito = ?, acepta_recordatorios = ?, activo = ?
                WHERE id_cliente = ?
                """, c.tipoDocumento(), c.numDocumento(), c.nombres(), c.apellidos(), c.telefono(), c.correo(),
                c.direccion(), c.distrito(), c.aceptaRecordatorios(), c.activo(), c.idCliente()));
    }
}
