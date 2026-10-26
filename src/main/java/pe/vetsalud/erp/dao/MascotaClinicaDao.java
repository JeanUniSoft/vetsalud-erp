package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.MascotaClinica;

/** Búsqueda de pacientes para la agenda y la historia clínica (P04, P05). */
public interface MascotaClinicaDao {

    /** Por nombre de la mascota, documento o nombre del propietario, o microchip; texto vacío = atendidas recientemente. */
    List<MascotaClinica> buscar(String texto, int limite) throws SQLException;

    Optional<MascotaClinica> buscarPorId(int idMascota) throws SQLException;
}
