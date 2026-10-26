package pe.vetsalud.erp.fakes;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pe.vetsalud.erp.dao.RecordatorioDao;
import pe.vetsalud.erp.modelo.Recordatorio;
import pe.vetsalud.erp.modelo.RecordatorioResumen;

/** Cola en memoria. {@code ahora} hace las veces de now() de PostgreSQL. */
public class RecordatorioDaoEnMemoria implements RecordatorioDao {

    public final Map<Long, Recordatorio> recordatorios = new LinkedHashMap<>();
    /** Ids que "otro proceso" ya tomó: cambiarEstado devuelve false para ellos. */
    public final Set<Long> tomadosPorOtro = new HashSet<>();
    public LocalDateTime ahora;
    public Integer horasProgramadas;
    public LocalTime horaControlProgramada;
    public int citasPorProgramar;
    public int controlesPorProgramar;

    public RecordatorioDaoEnMemoria(LocalDateTime ahora) {
        this.ahora = ahora;
    }

    public void agregar(Recordatorio r) {
        recordatorios.put(r.idRecordatorio(), r);
    }

    @Override
    public List<Recordatorio> listar(String estado, int limite) {
        return recordatorios.values().stream().filter(r -> estado == null || estado.equals(r.estado()))
                .limit(limite).toList();
    }

    @Override
    public RecordatorioResumen resumen() {
        int vencidos = 0;
        int futuros = 0;
        int enviados = 0;
        int fallidos = 0;
        int cancelados = 0;
        for (Recordatorio r : recordatorios.values()) {
            switch (r.estado()) {
                case Recordatorio.PENDIENTE -> {
                    if (r.programadoPara().isAfter(ahora)) {
                        futuros++;
                    } else {
                        vencidos++;
                    }
                }
                case Recordatorio.ENVIADO -> enviados++;
                case Recordatorio.FALLIDO -> fallidos++;
                default -> cancelados++;
            }
        }
        return new RecordatorioResumen(vencidos, futuros, enviados, fallidos, cancelados);
    }

    @Override
    public List<Recordatorio> pendientes(boolean incluirFuturos, int limite) {
        return recordatorios.values().stream()
                .filter(r -> Recordatorio.PENDIENTE.equals(r.estado()))
                .filter(r -> incluirFuturos || !r.programadoPara().isAfter(ahora))
                .sorted(Comparator.comparing(Recordatorio::programadoPara))
                .limit(limite).toList();
    }

    @Override
    public boolean cambiarEstado(long id, String estadoActual, String estadoNuevo, LocalDateTime fecha,
                                 Integer idUsuario) {
        Recordatorio r = recordatorios.get(id);
        if (r == null || tomadosPorOtro.contains(id) || !estadoActual.equals(r.estado())) {
            return false;
        }
        recordatorios.put(id, r.conEstado(estadoNuevo, fecha));
        return true;
    }

    @Override
    public int programarCitas(int horasAntes, Integer idUsuario) {
        horasProgramadas = horasAntes;
        return citasPorProgramar;
    }

    @Override
    public int programarControles(LocalTime hora, Integer idUsuario) {
        horaControlProgramada = hora;
        return controlesPorProgramar;
    }
}
