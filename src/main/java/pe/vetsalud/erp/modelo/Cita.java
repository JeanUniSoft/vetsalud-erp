package pe.vetsalud.erp.modelo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cita de la agenda (RF-09 a RF-12) con los datos que muestran la agenda y el detalle.
 *
 * @param idConsulta     consulta vinculada (null si aún no se atiende)
 * @param estadoConsulta ABIERTA | FIRMADA (null si no hay consulta)
 * @param transiciones   estados a los que puede pasar según RF-12 (los calcula {@code CitaService})
 */
public record Cita(long idCita, int idMascota, String mascota, String especie, String alertasMascota,
                   int idCliente, String cliente, String telefonoCliente,
                   int idServicio, String servicio, int idVeterinario, String veterinario,
                   String registradoPor, LocalDateTime inicio, LocalDateTime fin, String estado, String motivo,
                   LocalDateTime creadoEn, Long idConsulta, String estadoConsulta, List<String> transiciones) {

    public Cita {
        transiciones = transiciones == null ? List.of() : List.copyOf(transiciones);
    }

    public long duracionMin() {
        return Duration.between(inicio, fin).toMinutes();
    }

    /** RF-10: solo se reprograma una cita pendiente o confirmada (mismo criterio que el trigger fn_cita_estado). */
    public boolean reprogramable() {
        return "PENDIENTE".equals(estado) || "CONFIRMADA".equals(estado);
    }

    /** Cita que ya no ocupa el horario (la restricción ex_cita_sin_solape las excluye). */
    public boolean liberada() {
        return "CANCELADA".equals(estado) || "NO_ASISTIO".equals(estado);
    }

    public boolean seCruzaCon(LocalDateTime otroInicio, LocalDateTime otroFin) {
        return inicio.isBefore(otroFin) && otroInicio.isBefore(fin);
    }

    public Cita conTransiciones(List<String> nuevas) {
        return new Cita(idCita, idMascota, mascota, especie, alertasMascota, idCliente, cliente, telefonoCliente,
                idServicio, servicio, idVeterinario, veterinario, registradoPor, inicio, fin, estado, motivo,
                creadoEn, idConsulta, estadoConsulta, nuevas);
    }

    public Cita conEstado(String nuevoEstado, String nuevoMotivo) {
        return new Cita(idCita, idMascota, mascota, especie, alertasMascota, idCliente, cliente, telefonoCliente,
                idServicio, servicio, idVeterinario, veterinario, registradoPor, inicio, fin, nuevoEstado, nuevoMotivo,
                creadoEn, idConsulta, estadoConsulta, transiciones);
    }

    public Cita conHorario(LocalDateTime nuevoInicio, LocalDateTime nuevoFin, int nuevoVeterinario) {
        return new Cita(idCita, idMascota, mascota, especie, alertasMascota, idCliente, cliente, telefonoCliente,
                idServicio, servicio, nuevoVeterinario, veterinario, registradoPor, nuevoInicio, nuevoFin, estado,
                motivo, creadoEn, idConsulta, estadoConsulta, transiciones);
    }

    public Cita conConsulta(Long nuevaConsulta, String nuevoEstadoConsulta) {
        return new Cita(idCita, idMascota, mascota, especie, alertasMascota, idCliente, cliente, telefonoCliente,
                idServicio, servicio, idVeterinario, veterinario, registradoPor, inicio, fin, estado, motivo,
                creadoEn, nuevaConsulta, nuevoEstadoConsulta, transiciones);
    }
}
