package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.Profesional;

/** Personal clínico (veterinarios y técnicos) para la agenda y los procedimientos. */
public interface ProfesionalDao {

    /** Veterinarios activos (pueden atender citas y firmar consultas). */
    List<Profesional> veterinarios() throws SQLException;

    /** Veterinarios y técnicos activos (responsables de procedimientos, RF-16). */
    List<Profesional> personalClinico() throws SQLException;

    Optional<Profesional> buscarPorId(int idUsuario) throws SQLException;
}
