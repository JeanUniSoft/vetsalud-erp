package pe.vetsalud.erp.fakes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.ServicioDao;
import pe.vetsalud.erp.modelo.Servicio;

/** Catálogo de servicios en memoria con los mismos datos de 05_datos_prueba.sql. */
public class ServicioDaoEnMemoria implements ServicioDao {

    public static final int CONSULTA = 1;
    public static final int VACUNA = 3;
    public static final int CURACION = 5;
    public static final int INACTIVO = 9;

    public final Map<Integer, Servicio> servicios = new LinkedHashMap<>();

    public ServicioDaoEnMemoria() {
        agregar(new Servicio(CONSULTA, "Consulta general", "CONSULTA", 30, new BigDecimal("60.00"), true));
        agregar(new Servicio(VACUNA, "Vacuna triple felina", "VACUNACION", 20, new BigDecimal("75.00"), true));
        agregar(new Servicio(CURACION, "Curación de herida", "PROCEDIMIENTO", 30, new BigDecimal("70.00"), true));
        agregar(new Servicio(INACTIVO, "Baño medicado (retirado)", "ESTETICA", 45, new BigDecimal("50.00"), false));
    }

    public final void agregar(Servicio s) {
        servicios.put(s.idServicio(), s);
    }

    @Override
    public List<Servicio> activos() {
        return servicios.values().stream().filter(Servicio::activo).toList();
    }

    @Override
    public Optional<Servicio> buscarPorId(int idServicio) {
        return Optional.ofNullable(servicios.get(idServicio));
    }
}
