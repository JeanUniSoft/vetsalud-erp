package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.Cliente;

/** Acceso a datos de clientes (RF-05, RF-06). */
public interface ClienteDao {

    /** Busca por documento, nombre, teléfono o correo; texto vacío = últimos registrados. */
    List<Cliente> buscar(String texto, int limite) throws SQLException;

    Optional<Cliente> buscarPorId(int idCliente) throws SQLException;

    boolean existeDocumento(String tipoDocumento, String numDocumento, Integer exceptoId) throws SQLException;

    int insertar(Cliente cliente, int idUsuario) throws SQLException;

    void actualizar(Cliente cliente, int idUsuario) throws SQLException;
}
