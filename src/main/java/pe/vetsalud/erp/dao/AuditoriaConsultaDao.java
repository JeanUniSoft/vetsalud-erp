package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.modelo.AccesoRegistro;
import pe.vetsalud.erp.modelo.AuditoriaRegistro;
import pe.vetsalud.erp.modelo.Opcion;

/**
 * Consultas de trazabilidad de P12 (RF-01 bitácora de accesos, RF-04 auditoría). Solo lectura:
 * la aplicación no tiene UPDATE ni DELETE sobre auditoria ni acceso_log (RNF-06).
 */
public interface AuditoriaConsultaDao {

    /** Filtros opcionales (null = todos). Periodo [desde, hastaExclusivo). Más recientes primero. */
    List<AuditoriaRegistro> buscar(String tabla, String operacion, Integer idUsuario, LocalDate desde,
                                   LocalDate hastaExclusivo, int limite) throws SQLException;

    Optional<AuditoriaRegistro> buscarPorId(long idAuditoria) throws SQLException;

    /**
     * Campos del JSONB como texto (jsonb_each_text), ordenados por nombre.
     *
     * @param antes true = valores_antes; false = valores_despues
     */
    Map<String, String> valores(long idAuditoria, boolean antes) throws SQLException;

    /** Tablas auditadas presentes (filtro). */
    List<String> tablas() throws SQLException;

    /** Usuarios del sistema para el filtro (id + nombre). */
    List<Opcion> usuarios() throws SQLException;

    /** @param exitoso null = todos */
    List<AccesoRegistro> accesos(String usuario, Boolean exitoso, LocalDate desde, LocalDate hastaExclusivo,
                                 int limite) throws SQLException;
}
