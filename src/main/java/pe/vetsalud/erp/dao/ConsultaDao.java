package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

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

/**
 * Acceso a datos de la consulta clínica (RF-14 a RF-19). Los triggers de la BD protegen además la consulta
 * firmada (RN-05) y exigen veterinario y diagnóstico principal para firmar (RN-04).
 */
public interface ConsultaDao {

    Optional<Consulta> buscarPorId(long idConsulta) throws SQLException;

    Optional<Consulta> buscarPorCita(long idCita) throws SQLException;

    long insertar(int idMascota, Long idCita, int idVeterinario, String motivo, int idUsuario) throws SQLException;

    /** @return false si la consulta ya no estaba abierta */
    boolean actualizarDatos(long idConsulta, ConsultaDatos datos, int idUsuario) throws SQLException;

    /** @return false si la consulta ya no estaba abierta */
    boolean actualizarSignos(long idConsulta, ConsultaSignos signos, int idUsuario) throws SQLException;

    List<Diagnostico> diagnosticos(long idConsulta) throws SQLException;

    long insertarDiagnostico(long idConsulta, String descripcion, boolean principal, int idUsuario) throws SQLException;

    List<ProcedimientoConsulta> procedimientos(long idConsulta) throws SQLException;

    long insertarProcedimiento(ProcedimientoConsulta procedimiento, int idUsuario) throws SQLException;

    Optional<Receta> receta(long idConsulta) throws SQLException;

    long insertarReceta(long idConsulta, String indicaciones, int idUsuario) throws SQLException;

    void actualizarReceta(long idReceta, String indicaciones, int idUsuario) throws SQLException;

    /** Agrega un medicamento; si la consulta no tiene receta, la crea en la misma transacción. */
    long insertarLineaReceta(long idConsulta, RecetaLinea linea, int idUsuario) throws SQLException;

    List<ProductoReceta> productosActivos() throws SQLException;

    Optional<ProductoReceta> producto(int idProducto) throws SQLException;

    List<Adenda> adendas(long idConsulta) throws SQLException;

    long insertarAdenda(long idConsulta, String texto, int idUsuario) throws SQLException;

    /**
     * Firma en una sola transacción: consulta FIRMADA + recordatorio del control + cita ATENDIDA.
     *
     * @return false si la consulta ya no estaba abierta (no se hace ningún cambio)
     */
    boolean firmar(ConsultaFirma firma, int idUsuario) throws SQLException;
}
