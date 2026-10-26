package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import pe.vetsalud.erp.modelo.AjusteInventario;
import pe.vetsalud.erp.modelo.KardexFiltro;
import pe.vetsalud.erp.modelo.LoteAlerta;
import pe.vetsalud.erp.modelo.LoteConciliacion;
import pe.vetsalud.erp.modelo.MovimientoKardex;

/** Kardex (movimiento_inventario / v_kardex), ajustes y alertas de inventario (RF-22, RF-24, RF-25). */
public interface KardexDao {

    /** Movimientos del filtro en orden cronológico (los más recientes si superan el límite). */
    List<MovimientoKardex> movimientos(KardexFiltro filtro, int limite) throws SQLException;

    List<MovimientoKardex> ajustesRecientes(int limite) throws SQLException;

    /** Saldo calculado del kardex por lote frente a lote.stock_actual. */
    List<LoteConciliacion> conciliacion(int idProducto) throws SQLException;

    /** Inserta el movimiento AJUSTE_ENTRADA / AJUSTE_SALIDA (el trigger valida el stock). */
    long registrarAjuste(AjusteInventario ajuste, int idUsuario) throws SQLException;

    /** Filas de v_alerta_inventario (stock mínimo, por vencer y vencidos). */
    List<LoteAlerta> alertas() throws SQLException;

    /** Alertas internas pendientes del rol como "TIPO|mensaje" (para no duplicarlas). */
    Set<String> alertasPendientes(String rol) throws SQLException;

    void crearAlerta(String rol, String tipo, String mensaje, int idUsuario) throws SQLException;
}
