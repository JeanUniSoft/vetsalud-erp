package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Consulta clínica (RF-14 a RF-19). Mientras está ABIERTA se edita; FIRMADA es inmutable y solo admite adendas (RN-05).
 *
 * @param idCita        cita de la que proviene (null si es una atención sin cita)
 * @param colegiatura   número CMVP del veterinario responsable
 */
public record Consulta(long idConsulta, int idMascota, Long idCita, int idVeterinario, String veterinario,
                       String colegiatura, LocalDateTime fechaHora, String motivo, String anamnesis,
                       BigDecimal temperaturaC, Integer frecCardiaca, Integer frecRespiratoria, BigDecimal pesoKg,
                       String examenClinico, String indicaciones, LocalDate fechaControl, String estado,
                       LocalDateTime firmadaEn) {

    public static final String ABIERTA = "ABIERTA";
    public static final String FIRMADA = "FIRMADA";

    public boolean abierta() {
        return ABIERTA.equals(estado);
    }

    public boolean firmada() {
        return FIRMADA.equals(estado);
    }

    public boolean conSignos() {
        return temperaturaC != null || frecCardiaca != null || frecRespiratoria != null || pesoKg != null;
    }

    public Consulta conDatos(ConsultaDatos d) {
        return new Consulta(idConsulta, idMascota, idCita, idVeterinario, veterinario, colegiatura, fechaHora,
                d.motivo(), d.anamnesis(), temperaturaC, frecCardiaca, frecRespiratoria, pesoKg, d.examenClinico(),
                d.indicaciones(), d.fechaControl(), estado, firmadaEn);
    }

    public Consulta conSignos(ConsultaSignos s) {
        return new Consulta(idConsulta, idMascota, idCita, idVeterinario, veterinario, colegiatura, fechaHora,
                motivo, anamnesis, s.temperaturaC(), s.frecCardiaca(), s.frecRespiratoria(), s.pesoKg(),
                examenClinico, indicaciones, fechaControl, estado, firmadaEn);
    }

    public Consulta firmadaEl(LocalDateTime momento) {
        return new Consulta(idConsulta, idMascota, idCita, idVeterinario, veterinario, colegiatura, fechaHora,
                motivo, anamnesis, temperaturaC, frecCardiaca, frecRespiratoria, pesoKg, examenClinico, indicaciones,
                fechaControl, FIRMADA, momento);
    }
}
