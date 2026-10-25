package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.List;

import pe.vetsalud.erp.dao.AlertaDao;
import pe.vetsalud.erp.modelo.Alerta;

public class AlertaDaoEnMemoria implements AlertaDao {

    public final List<String> creadas = new ArrayList<>();

    @Override
    public void crear(String rolDestino, String tipo, String mensaje) {
        creadas.add(rolDestino + "|" + tipo + "|" + mensaje);
    }

    @Override
    public List<Alerta> pendientesPorRol(String rol) {
        return List.of();
    }

    @Override
    public List<Alerta> recientes(int limite) {
        return List.of();
    }

    @Override
    public boolean atender(long idAlerta, int idUsuario, String rol) {
        return true;
    }
}
