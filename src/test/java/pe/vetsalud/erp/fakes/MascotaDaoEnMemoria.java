package pe.vetsalud.erp.fakes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import pe.vetsalud.erp.dao.MascotaDao;
import pe.vetsalud.erp.modelo.CambioPropietario;
import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.modelo.MascotaCambio;

public class MascotaDaoEnMemoria implements MascotaDao {

    public final Map<Integer, Mascota> mascotas = new LinkedHashMap<>();
    public final List<CambioPropietario> historial = new ArrayList<>();
    public final List<MascotaCambio> cambios = new ArrayList<>();
    private int secuencia = 1;
    private long secuenciaHist = 1;

    @Override
    public List<Mascota> buscar(String texto, int limite) {
        return mascotas.values().stream()
                .filter(m -> texto == null || m.nombre().toLowerCase().contains(texto.toLowerCase())
                        || texto.equals(m.microchip()))
                .limit(limite).toList();
    }

    @Override
    public List<Mascota> listarPorCliente(int idCliente) {
        return mascotas.values().stream().filter(m -> m.idCliente() == idCliente).toList();
    }

    @Override
    public Optional<Mascota> buscarPorId(int idMascota) {
        return Optional.ofNullable(mascotas.get(idMascota));
    }

    @Override
    public boolean existeMicrochip(String microchip, Integer exceptoId) {
        return mascotas.values().stream().anyMatch(m -> microchip.equals(m.microchip())
                && (exceptoId == null || !m.idMascota().equals(exceptoId)));
    }

    @Override
    public int insertar(Mascota mascota, int idUsuario) {
        int id = secuencia++;
        mascotas.put(id, mascota.conId(id));
        return id;
    }

    @Override
    public void actualizar(Mascota m, int idUsuario) {
        Mascota actual = mascotas.get(m.idMascota());
        mascotas.put(m.idMascota(), new Mascota(m.idMascota(), actual.idCliente(), actual.propietario(), m.idEspecie(),
                m.especie(), m.idRaza(), m.raza(), m.nombre(), m.sexo(), m.fechaNacimiento(), m.color(), m.pesoKg(),
                m.microchip(), m.esterilizado(), m.alertas(), actual.fotoRuta(), actual.estado(), actual.creadoEn()));
    }

    @Override
    public void cambiarEstado(int idMascota, String estado, int idUsuario) {
        mascotas.computeIfPresent(idMascota, (k, m) -> m.conEstado(estado));
    }

    @Override
    public void actualizarFoto(int idMascota, String fotoRuta, int idUsuario) {
        mascotas.computeIfPresent(idMascota, (k, m) -> m.conFoto(fotoRuta));
    }

    @Override
    public boolean cambiarPropietario(int idMascota, int idClienteAnterior, int idClienteNuevo, String motivo,
                                      int idUsuario) {
        Mascota m = mascotas.get(idMascota);
        if (m == null || !Objects.equals(m.idCliente(), idClienteAnterior)) {
            return false;
        }
        mascotas.put(idMascota, m.conPropietario(idClienteNuevo, "cliente " + idClienteNuevo));
        historial.add(0, new CambioPropietario(secuenciaHist++, idMascota, idClienteAnterior, "cliente " + idClienteAnterior,
                idClienteNuevo, "cliente " + idClienteNuevo, motivo, "usuario" + idUsuario, LocalDateTime.now()));
        return true;
    }

    @Override
    public List<CambioPropietario> historialPropietarios(int idMascota) {
        return historial.stream().filter(h -> h.idMascota() == idMascota).toList();
    }

    @Override
    public List<MascotaCambio> historialCambios(int idMascota, int limite) {
        return cambios.stream().limit(limite).toList();
    }
}
