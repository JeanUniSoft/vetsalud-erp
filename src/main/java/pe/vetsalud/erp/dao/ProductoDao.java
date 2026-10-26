package pe.vetsalud.erp.dao;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.Lote;
import pe.vetsalud.erp.modelo.Producto;

/** Productos de farmacia y sus lotes (RF-20, RF-23, RF-24). */
public interface ProductoDao {

    /**
     * Catálogo con stock calculado por lotes.
     *
     * @param texto         código o nombre (null = todos)
     * @param idCategoria   null = todas
     * @param soloAlertas   solo productos con stock mínimo, lotes por vencer o vencidos
     * @param diasPorVencer parámetro DIAS_POR_VENCER
     */
    List<Producto> buscar(String texto, Integer idCategoria, boolean soloAlertas, boolean incluirInactivos,
                          int diasPorVencer, int limite) throws SQLException;

    Optional<Producto> buscarPorId(int idProducto, int diasPorVencer) throws SQLException;

    boolean existeCodigo(String codigo, Integer exceptoId) throws SQLException;

    int insertar(Producto producto, int idUsuario) throws SQLException;

    void actualizar(Producto producto, int idUsuario) throws SQLException;

    /** Lotes del producto en orden FEFO (vencimiento más próximo primero, sin vencimiento al final). */
    List<Lote> lotes(int idProducto, int diasPorVencer) throws SQLException;

    Optional<Lote> lotePorId(int idLote, int diasPorVencer) throws SQLException;

    /** RN-07: id del lote que propone fn_lote_sugerido para la cantidad pedida. */
    Optional<Integer> loteSugerido(int idProducto, BigDecimal cantidad) throws SQLException;
}
