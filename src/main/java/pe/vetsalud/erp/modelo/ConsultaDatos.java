package pe.vetsalud.erp.modelo;

import java.time.LocalDate;

/** Datos clínicos que registra el veterinario (RF-14) y la fecha de control (RF-19). */
public record ConsultaDatos(String motivo, String anamnesis, String examenClinico, String indicaciones,
                            LocalDate fechaControl) {
}
