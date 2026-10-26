package pe.vetsalud.erp.fakes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.CitaDao;
import pe.vetsalud.erp.modelo.AgendaFiltro;
import pe.vetsalud.erp.modelo.CambioEstadoCita;
import pe.vetsalud.erp.modelo.Cita;
import pe.vetsalud.erp.modelo.CitaSolicitud;
import pe.vetsalud.erp.modelo.ControlProgramado;

/** Doble en memoria de {@link CitaDao}: reproduce el historial de estados que en la BD llena el trigger. */
public class CitaDaoEnMemoria implements CitaDao {

    public final Map<Long, Cita> citas = new LinkedHashMap<>();
    public final Map<Long, List<CambioEstadoCita>> historial = new LinkedHashMap<>();
    public final List<ControlProgramado> controles = new ArrayList<>();
    private long secuencia = 1;
    private long secuenciaHist = 1;

    /** Agrega una cita ya existente (datos de prueba). */
    public Cita agregar(int idMascota, String mascota, int idVeterinario, String veterinario, int idServicio,
                        LocalDateTime inicio, LocalDateTime fin, String estado) {
        long id = secuencia++;
        Cita c = new Cita(id, idMascota, mascota, "Canino", null, 1, "Cliente", "999999999", idServicio, "Servicio",
                idVeterinario, veterinario, "ltorres", inicio, fin, estado, "Motivo original", inicio.minusDays(1),
                null, null, List.of());
        citas.put(id, c);
        return c;
    }

    @Override
    public List<Cita> agenda(LocalDateTime desde, LocalDateTime hasta, AgendaFiltro f) {
        return citas.values().stream()
                .filter(c -> !c.inicio().isBefore(desde) && c.inicio().isBefore(hasta))
                .filter(c -> f.idVeterinario() == null || c.idVeterinario() == f.idVeterinario())
                .filter(c -> f.idServicio() == null || c.idServicio() == f.idServicio())
                .filter(c -> f.estado() == null || c.estado().equals(f.estado()))
                .sorted(Comparator.comparing(Cita::inicio)).toList();
    }

    @Override
    public Optional<Cita> buscarPorId(long idCita) {
        return Optional.ofNullable(citas.get(idCita));
    }

    @Override
    public Optional<Cita> cruce(Integer idVeterinario, Integer idMascota, LocalDateTime inicio, LocalDateTime fin,
                                Long exceptoIdCita) {
        return citas.values().stream()
                .filter(c -> !c.liberada())
                .filter(c -> idVeterinario == null || c.idVeterinario() == idVeterinario)
                .filter(c -> idMascota == null || c.idMascota() == idMascota)
                .filter(c -> c.seCruzaCon(inicio, fin))
                .filter(c -> exceptoIdCita == null || c.idCita() != exceptoIdCita)
                .min(Comparator.comparing(Cita::inicio));
    }

    @Override
    public long insertar(CitaSolicitud s, LocalDateTime fin, int idUsuario) {
        long id = secuencia++;
        citas.put(id, new Cita(id, s.idMascota(), "Mascota " + s.idMascota(), "Canino", null, 1, "Cliente", "999999999",
                s.idServicio(), "Servicio " + s.idServicio(), s.idVeterinario(), "Vet " + s.idVeterinario(), "usuario" + idUsuario,
                s.inicio(), fin, "PENDIENTE", s.motivo(), LocalDateTime.now(), null, null, List.of()));
        registrar(id, null, "PENDIENTE", null, idUsuario);
        return id;
    }

    @Override
    public boolean cambiarEstado(long idCita, String estadoActual, String estadoNuevo, String motivo, int idUsuario) {
        Cita c = citas.get(idCita);
        if (c == null || !c.estado().equals(estadoActual)) {
            return false;
        }
        citas.put(idCita, c.conEstado(estadoNuevo, motivo == null ? c.motivo() : motivo));
        registrar(idCita, estadoActual, estadoNuevo, null, idUsuario);
        return true;
    }

    @Override
    public boolean reprogramar(long idCita, LocalDateTime inicio, LocalDateTime fin, int idVeterinario, String estado,
                               String constancia, int idUsuario) {
        Cita c = citas.get(idCita);
        if (c == null || !c.estado().equals(estado) || !c.reprogramable()) {
            return false;
        }
        citas.put(idCita, c.conHorario(inicio, fin, idVeterinario));
        registrar(idCita, estado, estado, constancia, idUsuario);
        return true;
    }

    @Override
    public List<CambioEstadoCita> historial(long idCita) {
        return historial.getOrDefault(idCita, List.of());
    }

    @Override
    public List<ControlProgramado> controles(LocalDate desde, LocalDate hasta, Integer idVeterinario) {
        return controles.stream()
                .filter(c -> !c.fechaControl().isBefore(desde) && !c.fechaControl().isAfter(hasta))
                .filter(c -> idVeterinario == null || c.idVeterinario() == idVeterinario).toList();
    }

    private void registrar(long idCita, String anterior, String nuevo, String motivo, int idUsuario) {
        historial.computeIfAbsent(idCita, k -> new ArrayList<>()).add(new CambioEstadoCita(secuenciaHist++, anterior,
                nuevo, motivo, "usuario" + idUsuario, "Usuario " + idUsuario, LocalDateTime.now()));
    }
}
