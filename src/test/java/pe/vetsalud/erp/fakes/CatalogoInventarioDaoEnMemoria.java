package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.CatalogoInventarioDao;
import pe.vetsalud.erp.modelo.Categoria;
import pe.vetsalud.erp.modelo.Proveedor;

public class CatalogoInventarioDaoEnMemoria implements CatalogoInventarioDao {

    public final Map<Integer, Categoria> categorias = new LinkedHashMap<>();
    public final Map<Integer, Proveedor> proveedores = new LinkedHashMap<>();
    private int secuencia = 10;

    public CatalogoInventarioDaoEnMemoria() {
        categorias.put(1, new Categoria(1, "Antibióticos", 0));
        categorias.put(2, new Categoria(2, "Accesorios", 0));
        proveedores.put(1, new Proveedor(1, "20100123456", "Distribuidora Veterinaria Andina S.A.C.", null, null, true));
        proveedores.put(2, new Proveedor(2, "20487654321", "Proveedor Inactivo S.A.", null, null, false));
    }

    @Override
    public List<Categoria> categorias() {
        return new ArrayList<>(categorias.values());
    }

    @Override
    public boolean existeCategoria(String nombre, Integer exceptoId) {
        return categorias.values().stream().anyMatch(c -> c.nombre().equalsIgnoreCase(nombre)
                && (exceptoId == null || c.idCategoria() != exceptoId));
    }

    @Override
    public int insertarCategoria(String nombre, int idUsuario) {
        int id = secuencia++;
        categorias.put(id, new Categoria(id, nombre, 0));
        return id;
    }

    @Override
    public void renombrarCategoria(int idCategoria, String nombre, int idUsuario) {
        categorias.put(idCategoria, new Categoria(idCategoria, nombre, 0));
    }

    @Override
    public List<Proveedor> proveedores(boolean soloActivos) {
        return proveedores.values().stream().filter(p -> !soloActivos || p.activo()).toList();
    }

    @Override
    public Optional<Proveedor> proveedorPorId(int idProveedor) {
        return Optional.ofNullable(proveedores.get(idProveedor));
    }

    @Override
    public boolean existeRuc(String ruc, Integer exceptoId) {
        return proveedores.values().stream().anyMatch(p -> p.ruc().equals(ruc)
                && (exceptoId == null || !exceptoId.equals(p.idProveedor())));
    }

    @Override
    public int insertarProveedor(Proveedor proveedor, int idUsuario) {
        int id = secuencia++;
        proveedores.put(id, proveedor.conId(id));
        return id;
    }

    @Override
    public void actualizarProveedor(Proveedor proveedor, int idUsuario) {
        proveedores.put(proveedor.idProveedor(), proveedor);
    }
}
