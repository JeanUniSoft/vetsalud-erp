package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pe.vetsalud.erp.dao.KardexDao;
import pe.vetsalud.erp.modelo.AjusteInventario;
import pe.vetsalud.erp.modelo.KardexFiltro;
import pe.vetsalud.erp.modelo.LoteAlerta;
import pe.vetsalud.erp.modelo.LoteConciliacion;
import pe.vetsalud.erp.modelo.MovimientoKardex;

public class KardexDaoEnMemoria implements KardexDao {

    public final List<AjusteInventario> ajustes = new ArrayList<>();
    public final List<LoteAlerta> alertas = new ArrayList<>();
    public final List<String> alertasCreadas = new ArrayList<>();
    private final Set<String> pendientes = new HashSet<>();

    @Override
    public List<MovimientoKardex> movimientos(KardexFiltro filtro, int limite) {
        return List.of();
    }

    @Override
    public List<MovimientoKardex> ajustesRecientes(int limite) {
        return List.of();
    }

    @Override
    public List<LoteConciliacion> conciliacion(int idProducto) {
        return List.of();
    }

    @Override
    public long registrarAjuste(AjusteInventario ajuste, int idUsuario) {
        ajustes.add(ajuste);
        return ajustes.size();
    }

    @Override
    public List<LoteAlerta> alertas() {
        return alertas;
    }

    @Override
    public Set<String> alertasPendientes(String rol) {
        return new HashSet<>(pendientes);
    }

    @Override
    public void crearAlerta(String rol, String tipo, String mensaje, int idUsuario) {
        alertasCreadas.add(rol + "|" + tipo + "|" + mensaje);
        pendientes.add(tipo + "|" + mensaje);
    }
}
