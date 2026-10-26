package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.CambioPropietario;
import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.modelo.MascotaCambio;

/** Acceso a datos de mascotas (RF-07, RF-08). La auditoría de cada cambio la registra el trigger tg_aud_mascota. */
public interface MascotaDao {

    /** Busca por nombre de la mascota, microchip o documento/nombre del propietario; texto vacío = últimas registradas. */
    List<Mascota> buscar(String texto, int limite) throws SQLException;

    /** Todas las mascotas del cliente (activas primero). */
    List<Mascota> listarPorCliente(int idCliente) throws SQLException;

    Optional<Mascota> buscarPorId(int idMascota) throws SQLException;

    boolean existeMicrochip(String microchip, Integer exceptoId) throws SQLException;

    int insertar(Mascota mascota, int idUsuario) throws SQLException;

    /** Actualiza los datos descriptivos (no cambia propietario, estado ni foto). */
    void actualizar(Mascota mascota, int idUsuario) throws SQLException;

    void cambiarEstado(int idMascota, String estado, int idUsuario) throws SQLException;

    void actualizarFoto(int idMascota, String fotoRuta, int idUsuario) throws SQLException;

    /**
     * Cambio de propietario atómico (RF-08): actualiza mascota.id_cliente, inserta la fila de
     * mascota_propietario_hist y reasigna al nuevo dueño los recordatorios aún pendientes de la mascota,
     * todo en UNA transacción.
     *
     * @return false si la mascota ya no pertenecía a {@code idClienteAnterior} (cambio concurrente); no se modifica nada
     */
    boolean cambiarPropietario(int idMascota, int idClienteAnterior, int idClienteNuevo, String motivo, int idUsuario)
            throws SQLException;

    List<CambioPropietario> historialPropietarios(int idMascota) throws SQLException;

    /** Cambios registrados en la bitácora de auditoría para la mascota, del más reciente al más antiguo. */
    List<MascotaCambio> historialCambios(int idMascota, int limite) throws SQLException;
}
