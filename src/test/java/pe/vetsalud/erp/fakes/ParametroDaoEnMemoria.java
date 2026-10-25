package pe.vetsalud.erp.fakes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.ParametroDao;
import pe.vetsalud.erp.modelo.Parametro;

public class ParametroDaoEnMemoria implements ParametroDao {

    public final Map<String, String> valores = new HashMap<>(Map.of(
            "MAX_INTENTOS_LOGIN", "5", "DESCUENTO_MAX_PCT", "10", "DIAS_POR_VENCER", "30", "MINUTOS_SESION", "15"));

    @Override
    public Optional<String> valor(String clave) {
        return Optional.ofNullable(valores.get(clave));
    }

    @Override
    public List<Parametro> todos() {
        return valores.entrySet().stream().map(e -> new Parametro(e.getKey(), e.getValue(), "", null)).toList();
    }

    @Override
    public void actualizar(String clave, String valor, int idUsuario) {
        valores.put(clave, valor);
    }
}
