package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Cliente (propietario de mascotas). RF-05, RF-06. */
public record Cliente(Integer idCliente, String tipoDocumento, String numDocumento, String nombres, String apellidos,
                      String telefono, String correo, String direccion, String distrito,
                      boolean aceptaRecordatorios, boolean activo, LocalDateTime creadoEn, int numMascotas) {

    /** Nombre para mostrar (razón social si es RUC). */
    public String nombreCompleto() {
        return apellidos == null ? nombres : nombres + " " + apellidos;
    }

    /** Datos capturados en un formulario (aún sin id). */
    public static Cliente nuevo(String tipoDocumento, String numDocumento, String nombres, String apellidos,
                                String telefono, String correo, String direccion, String distrito,
                                boolean aceptaRecordatorios) {
        return new Cliente(null, tipoDocumento, numDocumento, nombres, apellidos, telefono, correo, direccion,
                distrito, aceptaRecordatorios, true, null, 0);
    }

    public Cliente conId(int id) {
        return new Cliente(id, tipoDocumento, numDocumento, nombres, apellidos, telefono, correo, direccion, distrito,
                aceptaRecordatorios, activo, creadoEn, numMascotas);
    }
}
