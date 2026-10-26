package pe.vetsalud.erp.fakes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.MascotaClinicaDao;
import pe.vetsalud.erp.modelo.MascotaClinica;

/** Pacientes de prueba: Max (dueño con recordatorios), Bruno (dueño sin consentimiento) y Kira (fallecida). */
public class MascotaClinicaDaoEnMemoria implements MascotaClinicaDao {

    public static final int MAX = 10;
    public static final int BRUNO = 8;
    public static final int FALLECIDA = 30;

    public final Map<Integer, MascotaClinica> mascotas = new LinkedHashMap<>();

    public MascotaClinicaDaoEnMemoria() {
        agregar(mascota(MAX, "Max", "ACTIVA", 7, true, "jorge.paredes@gmail.com", null));
        agregar(mascota(BRUNO, "Bruno", "ACTIVA", 9, false, null, "Alergia alimentaria al pollo"));
        agregar(mascota(FALLECIDA, "Kira", "FALLECIDA", 11, true, "kira@correo.pe", null));
    }

    public static MascotaClinica mascota(int id, String nombre, String estado, int idCliente, boolean acepta,
                                         String correo, String alertas) {
        return new MascotaClinica(id, nombre, "Canino", "Mestizo", "M", LocalDate.of(2020, 2, 14),
                new BigDecimal("12.50"), "Marrón", null, false, alertas, estado, idCliente, "Dueño de " + nombre,
                "DNI", "4125678" + id % 10, "951234876", correo, acepta, null);
    }

    public final void agregar(MascotaClinica m) {
        mascotas.put(m.idMascota(), m);
    }

    @Override
    public List<MascotaClinica> buscar(String texto, int limite) {
        return mascotas.values().stream()
                .filter(m -> texto == null || m.nombre().toLowerCase().contains(texto.toLowerCase())
                        || m.numDocumento().equals(texto))
                .limit(limite).toList();
    }

    @Override
    public Optional<MascotaClinica> buscarPorId(int idMascota) {
        return Optional.ofNullable(mascotas.get(idMascota));
    }
}
