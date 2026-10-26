package pe.vetsalud.erp.fakes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.AuditoriaConsultaDao;
import pe.vetsalud.erp.modelo.AccesoRegistro;
import pe.vetsalud.erp.modelo.AuditoriaRegistro;
import pe.vetsalud.erp.modelo.Opcion;

/** Doble de prueba de AuditoriaConsultaDao. */
public class AuditoriaConsultaDaoEnMemoria implements AuditoriaConsultaDao {

    public final List<AuditoriaRegistro> registros = new ArrayList<>();
    public final Map<Long, Map<String, String>> antes = new HashMap<>();
    public final Map<Long, Map<String, String>> despues = new HashMap<>();
    public final List<AccesoRegistro> accesos = new ArrayList<>();
    public String ultimaConsulta;

    @Override
    public List<AuditoriaRegistro> buscar(String tabla, String operacion, Integer idUsuario, LocalDate desde,
                                          LocalDate hasta, int limite) {
        ultimaConsulta = tabla + "|" + operacion + "|" + idUsuario + "|" + desde + ".." + hasta;
        return registros.stream().filter(r -> tabla == null || r.tabla().equals(tabla))
                .filter(r -> operacion == null || r.operacion().equals(operacion)).limit(limite).toList();
    }

    @Override
    public Optional<AuditoriaRegistro> buscarPorId(long idAuditoria) {
        return registros.stream().filter(r -> r.idAuditoria() == idAuditoria).findFirst();
    }

    @Override
    public Map<String, String> valores(long idAuditoria, boolean deAntes) {
        return (deAntes ? antes : despues).getOrDefault(idAuditoria, Map.of());
    }

    @Override
    public List<String> tablas() {
        return registros.stream().map(AuditoriaRegistro::tabla).distinct().sorted().toList();
    }

    @Override
    public List<Opcion> usuarios() {
        return List.of(new Opcion(1, "rparedes"));
    }

    @Override
    public List<AccesoRegistro> accesos(String usuario, Boolean exitoso, LocalDate desde, LocalDate hasta,
                                        int limite) {
        ultimaConsulta = usuario + "|" + exitoso + "|" + desde + ".." + hasta;
        return accesos.stream().filter(a -> exitoso == null || a.exitoso() == exitoso).toList();
    }
}
