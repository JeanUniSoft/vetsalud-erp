package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.Opcion;
import pe.vetsalud.erp.modelo.UsuarioAdmin;

/** Administración de usuarios de P12 (RF-02, RF-03). Ningún método devuelve el hash de la clave. */
public interface UsuarioAdminDao {

    /**
     * @param estado ACTIVOS, INACTIVOS, BLOQUEADOS o null (todos)
     */
    List<UsuarioAdmin> listar(String texto, String rol, String estado) throws SQLException;

    Optional<UsuarioAdmin> buscarPorId(int idUsuario) throws SQLException;

    boolean existeUsuario(String usuario, Integer exceptoId) throws SQLException;

    boolean existeCorreo(String correo, Integer exceptoId) throws SQLException;

    /** Nombres de los roles activos (lista blanca del formulario). */
    List<String> roles() throws SQLException;

    /** Cliente activo con ese número de documento (id + nombre). */
    Optional<Opcion> clientePorDocumento(String numDocumento) throws SQLException;

    /** ¿El cliente ya tiene un usuario del portal distinto de exceptoUsuario? (un usuario por cliente) */
    boolean clienteTieneUsuario(int idCliente, Integer exceptoUsuario) throws SQLException;

    /** Clientes activos sin usuario del portal (sugerencias del formulario). etiqueta = "documento · nombre". */
    List<Opcion> clientesSinUsuario(int limite) throws SQLException;

    /** Inserta el usuario y, si es CLIENTE, su vínculo en cliente_usuario, en una sola transacción. */
    int insertar(UsuarioAdmin datos, String claveHash, Integer idCliente, int idActor) throws SQLException;

    /** Actualiza datos y rol; si es CLIENTE, crea o cambia el vínculo, en una sola transacción. */
    void actualizar(UsuarioAdmin datos, Integer idCliente, int idActor) throws SQLException;

    /** Clave temporal: nuevo hash, debe_cambiar_clave = true y desbloqueo de la cuenta (RF-03). */
    void restablecerClave(int idUsuario, String claveHash, int idActor) throws SQLException;

    void desbloquear(int idUsuario, int idActor) throws SQLException;

    void cambiarEstado(int idUsuario, boolean activo, int idActor) throws SQLException;
}
