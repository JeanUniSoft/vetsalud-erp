package pe.vetsalud.erp.fakes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.dao.PortalDao;
import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.modelo.PortalCita;
import pe.vetsalud.erp.modelo.PortalControl;
import pe.vetsalud.erp.modelo.Recordatorio;

/** Portal en memoria: reproduce el filtro por propietario que el DAO JDBC hace en el SQL. */
public class PortalDaoEnMemoria implements PortalDao {

    public final List<Mascota> mascotas = new ArrayList<>();
    public final List<PortalCita> citas = new ArrayList<>();
    public final List<PortalControl> controles = new ArrayList<>();
    public final List<Recordatorio> recordatorios = new ArrayList<>();

    private boolean esDe(int idCliente, int idMascota) {
        return mascotas.stream().anyMatch(m -> m.idMascota() == idMascota && m.idCliente() == idCliente);
    }

    @Override
    public List<Mascota> mascotas(int idCliente) {
        return mascotas.stream().filter(m -> m.idCliente() == idCliente).toList();
    }

    @Override
    public Optional<Mascota> mascota(int idCliente, int idMascota) {
        return mascotas.stream().filter(m -> m.idMascota() == idMascota && m.idCliente() == idCliente).findFirst();
    }

    @Override
    public List<PortalCita> citasDesde(int idCliente, Integer idMascota, LocalDate desde, int limite) {
        return citas.stream()
                .filter(c -> esDe(idCliente, c.idMascota()) && (idMascota == null || c.idMascota() == idMascota))
                .filter(c -> !c.inicio().toLocalDate().isBefore(desde))
                .sorted(Comparator.comparing(PortalCita::inicio)).limit(limite).toList();
    }

    @Override
    public List<PortalControl> controlesDesde(int idCliente, Integer idMascota, LocalDate desde) {
        return controles.stream()
                .filter(c -> esDe(idCliente, c.idMascota()) && (idMascota == null || c.idMascota() == idMascota))
                .filter(c -> !c.fechaControl().isBefore(desde)).toList();
    }

    @Override
    public List<Recordatorio> recordatorios(int idCliente, int limite) {
        return recordatorios.stream().filter(r -> r.idCliente() == idCliente).limit(limite).toList();
    }
}
