package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vista clínica de la mascota (paciente) con su propietario, para la agenda y la historia clínica (RF-09, RF-13).
 * {@code alertas} son las alertas clínicas (alergias, agresividad) que deben verse siempre.
 */
public record MascotaClinica(int idMascota, String nombre, String especie, String raza, String sexo,
                             LocalDate fechaNacimiento, BigDecimal pesoKg, String color, String microchip,
                             boolean esterilizado, String alertas, String estado,
                             int idCliente, String cliente, String tipoDocumento, String numDocumento,
                             String telefono, String correo, boolean aceptaRecordatorios,
                             LocalDateTime ultimaConsulta) {

    public boolean activa() {
        return "ACTIVA".equals(estado);
    }

    public String sexoTexto() {
        return "H".equals(sexo) ? "Hembra" : "Macho";
    }

    /** RF-19 / RF-37: el recordatorio por correo requiere consentimiento y un correo registrado. */
    public boolean admiteRecordatorios() {
        return aceptaRecordatorios && correo != null && !correo.isBlank();
    }
}
