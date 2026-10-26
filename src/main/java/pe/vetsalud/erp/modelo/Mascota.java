package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mascota (paciente) vinculada a un cliente propietario. RF-07, RF-08, RN-02.
 * La raza determina la especie (3FN): idEspecie/especie se leen a través de la raza.
 *
 * @param propietario nombre para mostrar del cliente actual
 * @param fotoRuta    ruta relativa del archivo de foto en disco (nunca la imagen en la BD)
 */
public record Mascota(Integer idMascota, Integer idCliente, String propietario, Integer idEspecie, String especie,
                      Integer idRaza, String raza, String nombre, String sexo, LocalDate fechaNacimiento,
                      String color, BigDecimal pesoKg, String microchip, boolean esterilizado, String alertas,
                      String fotoRuta, String estado, LocalDateTime creadoEn) {

    public static final String ACTIVA = "ACTIVA";
    public static final String INACTIVA = "INACTIVA";
    public static final String FALLECIDA = "FALLECIDA";

    /** Datos capturados en el formulario de registro o edición (aún sin nombres de catálogo). */
    public static Mascota datos(Integer idMascota, Integer idCliente, Integer idEspecie, Integer idRaza, String nombre,
                                String sexo, LocalDate fechaNacimiento, String color, BigDecimal pesoKg,
                                String microchip, boolean esterilizado, String alertas) {
        return new Mascota(idMascota, idCliente, null, idEspecie, null, idRaza, null, nombre, sexo, fechaNacimiento,
                color, pesoKg, microchip, esterilizado, alertas, null, ACTIVA, null);
    }

    public Mascota conId(int id) {
        return new Mascota(id, idCliente, propietario, idEspecie, especie, idRaza, raza, nombre, sexo, fechaNacimiento,
                color, pesoKg, microchip, esterilizado, alertas, fotoRuta, estado, creadoEn);
    }

    public Mascota conEstado(String nuevoEstado) {
        return new Mascota(idMascota, idCliente, propietario, idEspecie, especie, idRaza, raza, nombre, sexo,
                fechaNacimiento, color, pesoKg, microchip, esterilizado, alertas, fotoRuta, nuevoEstado, creadoEn);
    }

    public Mascota conPropietario(int nuevoCliente, String nombrePropietario) {
        return new Mascota(idMascota, nuevoCliente, nombrePropietario, idEspecie, especie, idRaza, raza, nombre, sexo,
                fechaNacimiento, color, pesoKg, microchip, esterilizado, alertas, fotoRuta, estado, creadoEn);
    }

    public Mascota conFoto(String ruta) {
        return new Mascota(idMascota, idCliente, propietario, idEspecie, especie, idRaza, raza, nombre, sexo,
                fechaNacimiento, color, pesoKg, microchip, esterilizado, alertas, ruta, estado, creadoEn);
    }

    public String sexoTexto() {
        return "M".equals(sexo) ? "Macho" : "H".equals(sexo) ? "Hembra" : "";
    }

    public boolean activa() {
        return ACTIVA.equals(estado);
    }

    public boolean fallecida() {
        return FALLECIDA.equals(estado);
    }

    public boolean tieneFoto() {
        return fotoRuta != null && !fotoRuta.isBlank();
    }

    public boolean tieneAlertas() {
        return alertas != null && !alertas.isBlank();
    }

    /** Inicial para el avatar cuando no hay foto. */
    public String inicial() {
        return nombre == null || nombre.isBlank() ? "?" : nombre.substring(0, 1).toUpperCase();
    }
}
