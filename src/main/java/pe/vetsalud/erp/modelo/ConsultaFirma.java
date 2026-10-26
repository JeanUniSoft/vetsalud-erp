package pe.vetsalud.erp.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Operación atómica de firma (RF-18, RF-19): firma la consulta, programa el recordatorio del control
 * (si el propietario lo acepta) y marca la cita como atendida.
 *
 * @param fechaControl          control programado en la consulta (null: sin control)
 * @param idClienteRecordatorio propietario que recibirá el recordatorio (null: no se programa)
 * @param idCitaAtendida        cita en atención que pasa a ATENDIDA (null: sin cita)
 */
public record ConsultaFirma(long idConsulta, LocalDate fechaControl, Integer idClienteRecordatorio,
                            LocalDateTime recordatorioPara, Long idCitaAtendida) {

    public boolean conRecordatorio() {
        return idClienteRecordatorio != null;
    }
}
