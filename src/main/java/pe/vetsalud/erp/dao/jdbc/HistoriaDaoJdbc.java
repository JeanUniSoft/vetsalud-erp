package pe.vetsalud.erp.dao.jdbc;

import java.sql.SQLException;
import java.util.List;

import pe.vetsalud.erp.dao.HistoriaDao;
import pe.vetsalud.erp.modelo.Adenda;
import pe.vetsalud.erp.modelo.Consulta;
import pe.vetsalud.erp.modelo.ConsultaResumen;
import pe.vetsalud.erp.modelo.Diagnostico;
import pe.vetsalud.erp.modelo.ProcedimientoConsulta;
import pe.vetsalud.erp.modelo.Receta;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link HistoriaDao} (solo lectura; reutiliza los mapeadores de {@link ConsultaDaoJdbc}). */
public class HistoriaDaoJdbc implements HistoriaDao {

    private final Db db;

    public HistoriaDaoJdbc(Db db) {
        this.db = db;
    }

    @Override
    public List<Consulta> consultas(int idMascota) throws SQLException {
        // ix_consulta_historia (id_mascota, fecha_hora DESC) atiende esta lectura (RNF-01)
        return db.consultar(cn -> Jdbc.lista(cn, ConsultaDaoJdbc.SELECT_CONSULTA
                + " WHERE co.id_mascota = ? ORDER BY co.fecha_hora", ConsultaDaoJdbc::mapearConsulta, idMascota));
    }

    @Override
    public List<Diagnostico> diagnosticos(int idMascota) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT d.id_diagnostico, d.id_consulta, d.descripcion, d.es_principal
                FROM consulta_diagnostico d JOIN consulta co ON co.id_consulta = d.id_consulta
                WHERE co.id_mascota = ? ORDER BY d.es_principal DESC, d.id_diagnostico
                """, ConsultaDaoJdbc::mapearDiagnostico, idMascota));
    }

    @Override
    public List<ProcedimientoConsulta> procedimientos(int idMascota) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, ConsultaDaoJdbc.SELECT_PROCEDIMIENTO
                + " JOIN consulta co ON co.id_consulta = p.id_consulta WHERE co.id_mascota = ? ORDER BY p.id_proc_consulta",
                ConsultaDaoJdbc::mapearProcedimiento, idMascota));
    }

    @Override
    public List<Receta> recetas(int idMascota) throws SQLException {
        return db.consultar(cn -> ConsultaDaoJdbc.recetas(cn, true, idMascota));
    }

    @Override
    public List<Adenda> adendas(int idMascota) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, ConsultaDaoJdbc.SELECT_ADENDA
                + " JOIN consulta co ON co.id_consulta = a.id_consulta WHERE co.id_mascota = ? ORDER BY a.fecha_hora",
                ConsultaDaoJdbc::mapearAdenda, idMascota));
    }

    @Override
    public List<ConsultaResumen> consultasAbiertas(Integer idVeterinario) throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT co.id_consulta, co.fecha_hora, co.id_mascota, m.nombre AS mascota, e.nombre AS especie,
                       cl.nombres || coalesce(' ' || cl.apellidos, '') AS cliente, co.id_veterinario,
                       v.nombres || ' ' || v.apellidos AS veterinario, co.motivo, co.estado
                FROM consulta co
                JOIN mascota m ON m.id_mascota = co.id_mascota
                JOIN raza r ON r.id_raza = m.id_raza
                JOIN especie e ON e.id_especie = r.id_especie
                JOIN cliente cl ON cl.id_cliente = m.id_cliente
                JOIN usuario v ON v.id_usuario = co.id_veterinario
                WHERE co.estado = 'ABIERTA' AND (?::int IS NULL OR co.id_veterinario = ?::int)
                ORDER BY co.fecha_hora
                """, rs -> new ConsultaResumen(rs.getLong("id_consulta"), Jdbc.fechaHora(rs, "fecha_hora"),
                        rs.getInt("id_mascota"), rs.getString("mascota"), rs.getString("especie"), rs.getString("cliente"),
                        rs.getInt("id_veterinario"), rs.getString("veterinario"), rs.getString("motivo"),
                        rs.getString("estado")), idVeterinario, idVeterinario));
    }
}
