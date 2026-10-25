package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;

import pe.vetsalud.erp.modelo.Usuario;

/**
 * Acceso a datos de usuarios para autenticación (RF-01 a RF-03).
 * Interfaz (DIP de SOLID): el servicio depende de esta abstracción y las pruebas usan un doble en memoria.
 */
public interface UsuarioDao {

    Optional<Usuario> buscarPorUsuario(String usuario) throws SQLException;

    Optional<Usuario> buscarPorId(int idUsuario) throws SQLException;

    Set<String> permisosDeRol(String rol) throws SQLException;

    /** Id del cliente vinculado a un usuario del portal (rol CLIENTE), si existe. */
    Optional<Integer> clienteVinculado(int idUsuario) throws SQLException;

    void registrarIntento(Integer idUsuario, String usuarioIngresado, String ip, boolean exitoso) throws SQLException;

    void registrarFallo(int idUsuario, int intentos, boolean bloquear) throws SQLException;

    void registrarIngresoExitoso(int idUsuario) throws SQLException;

    void cambiarClave(int idUsuario, String nuevoHash, boolean debeCambiar) throws SQLException;

    /** Usuario cuyo nombre de usuario o correo coincide (recuperación de acceso). */
    Optional<Usuario> buscarPorUsuarioOCorreo(String dato) throws SQLException;
}
