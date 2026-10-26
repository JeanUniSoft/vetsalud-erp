package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.Categoria;
import pe.vetsalud.erp.modelo.Proveedor;

/** Catálogos de apoyo del inventario: categorías y proveedores (RF-20, RF-21). */
public interface CatalogoInventarioDao {

    List<Categoria> categorias() throws SQLException;

    boolean existeCategoria(String nombre, Integer exceptoId) throws SQLException;

    int insertarCategoria(String nombre, int idUsuario) throws SQLException;

    void renombrarCategoria(int idCategoria, String nombre, int idUsuario) throws SQLException;

    List<Proveedor> proveedores(boolean soloActivos) throws SQLException;

    Optional<Proveedor> proveedorPorId(int idProveedor) throws SQLException;

    boolean existeRuc(String ruc, Integer exceptoId) throws SQLException;

    int insertarProveedor(Proveedor proveedor, int idUsuario) throws SQLException;

    void actualizarProveedor(Proveedor proveedor, int idUsuario) throws SQLException;
}
