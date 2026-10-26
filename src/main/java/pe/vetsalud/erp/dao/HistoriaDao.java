package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;

import pe.vetsalud.erp.modelo.Adenda;
import pe.vetsalud.erp.modelo.Consulta;
import pe.vetsalud.erp.modelo.ConsultaResumen;
import pe.vetsalud.erp.modelo.Diagnostico;
import pe.vetsalud.erp.modelo.ProcedimientoConsulta;
import pe.vetsalud.erp.modelo.Receta;

/** Lectura de la historia clínica completa de una mascota (RF-13). */
public interface HistoriaDao {

    List<Consulta> consultas(int idMascota) throws SQLException;

    List<Diagnostico> diagnosticos(int idMascota) throws SQLException;

    List<ProcedimientoConsulta> procedimientos(int idMascota) throws SQLException;

    List<Receta> recetas(int idMascota) throws SQLException;

    List<Adenda> adendas(int idMascota) throws SQLException;

    /** Consultas abiertas (sin firmar), opcionalmente de un veterinario: pendientes de cierre (RF-18). */
    List<ConsultaResumen> consultasAbiertas(Integer idVeterinario) throws SQLException;
}
