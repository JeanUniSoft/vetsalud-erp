package pe.vetsalud.erp.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Recordatorio por correo de una cita o de un control posterior (RF-37, RF-19).
 * Incluye los datos mínimos para decidir el envío (consentimiento, correo, estado de la cita y de la mascota);
 * nunca lleva motivo, diagnóstico ni receta.
 *
 * @param inicioCita   fecha y hora de la cita (si el origen es una cita)
 * @param fechaControl fecha del control (si el origen es una consulta)
 * @param enviadoEn    fecha del intento de envío (ENVIADO o FALLIDO)
 */
public record Recordatorio(long idRecordatorio, int idCliente, String cliente, String correo,
                           boolean aceptaRecordatorios, boolean clienteActivo, Long idCita, Long idConsulta,
                           int idMascota, String mascota, String estadoMascota, LocalDateTime inicioCita,
                           String estadoCita, LocalDate fechaControl, String canal, LocalDateTime programadoPara,
                           String estado, LocalDateTime enviadoEn) {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIADO = "ENVIADO";
    public static final String FALLIDO = "FALLIDO";
    public static final String CANCELADO = "CANCELADO";

    public boolean esCita() {
        return idCita != null;
    }

    public String origenTexto() {
        return esCita() ? "Cita" : "Control";
    }

    /** Fecha y hora del evento recordado (el control se considera a inicio del día). */
    public LocalDateTime fechaEvento() {
        return esCita() ? inicioCita : fechaControl == null ? null : fechaControl.atStartOfDay();
    }

    public Recordatorio conEstado(String nuevoEstado, LocalDateTime fecha) {
        return new Recordatorio(idRecordatorio, idCliente, cliente, correo, aceptaRecordatorios, clienteActivo, idCita,
                idConsulta, idMascota, mascota, estadoMascota, inicioCita, estadoCita, fechaControl, canal,
                programadoPara, nuevoEstado, fecha);
    }

    /** Destinatario enmascarado para listados y bitácoras: m***@gmail.com (RNF-15). */
    public String destinatarioEnmascarado() {
        return enmascararCorreo(correo);
    }

    public static String enmascararCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            return "";
        }
        int arroba = correo.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
