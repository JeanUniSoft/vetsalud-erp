package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.List;

import pe.vetsalud.erp.dao.HistoriaDao;
import pe.vetsalud.erp.modelo.Adenda;
import pe.vetsalud.erp.modelo.Consulta;
import pe.vetsalud.erp.modelo.ConsultaResumen;
import pe.vetsalud.erp.modelo.Diagnostico;
import pe.vetsalud.erp.modelo.ProcedimientoConsulta;
import pe.vetsalud.erp.modelo.Receta;

/** Doble en memoria de {@link HistoriaDao}: las listas se cargan directamente en cada prueba. */
public class HistoriaDaoEnMemoria implements HistoriaDao {

    public final List<Consulta> consultas = new ArrayList<>();
    public final List<Diagnostico> diagnosticos = new ArrayList<>();
    public final List<ProcedimientoConsulta> procedimientos = new ArrayList<>();
    public final List<Receta> recetas = new ArrayList<>();
    public final List<Adenda> adendas = new ArrayList<>();
    public final List<ConsultaResumen> abiertas = new ArrayList<>();
    public Integer ultimoFiltroVeterinario;

    private List<Long> idsDe(int idMascota) {
        return consultas.stream().filter(c -> c.idMascota() == idMascota).map(Consulta::idConsulta).toList();
    }

    @Override
    public List<Consulta> consultas(int idMascota) {
        return consultas.stream().filter(c -> c.idMascota() == idMascota).toList();
    }

    @Override
    public List<Diagnostico> diagnosticos(int idMascota) {
        return diagnosticos.stream().filter(d -> idsDe(idMascota).contains(d.idConsulta())).toList();
    }

    @Override
    public List<ProcedimientoConsulta> procedimientos(int idMascota) {
        return procedimientos.stream().filter(p -> idsDe(idMascota).contains(p.idConsulta())).toList();
    }

    @Override
    public List<Receta> recetas(int idMascota) {
        return recetas.stream().filter(r -> idsDe(idMascota).contains(r.idConsulta())).toList();
    }

    @Override
    public List<Adenda> adendas(int idMascota) {
        return adendas.stream().filter(a -> idsDe(idMascota).contains(a.idConsulta())).toList();
    }

    @Override
    public List<ConsultaResumen> consultasAbiertas(Integer idVeterinario) {
        ultimoFiltroVeterinario = idVeterinario;
        return abiertas.stream().filter(c -> idVeterinario == null || c.idVeterinario() == idVeterinario).toList();
    }
}
