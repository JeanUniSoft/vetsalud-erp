package pe.vetsalud.erp.dao;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Almacén de fotografías de mascotas (RF-08). La imagen va en disco y la BD guarda solo la ruta relativa.
 * Interfaz pequeña (ISP) para poder cambiar el disco por otro almacenamiento sin tocar el servicio (OCP/DIP).
 */
public interface MascotaFotoAlmacen {

    /**
     * Guarda los bytes con un nombre generado por el sistema (nunca el nombre enviado por el usuario).
     *
     * @param extension "jpg" o "png", ya validada por el servicio
     * @return ruta relativa a guardar en mascota.foto_ruta
     */
    String guardar(int idMascota, byte[] contenido, String extension) throws IOException;

    /** Archivo físico de una ruta relativa, solo si existe y está dentro del directorio de fotos. */
    Optional<Path> ubicar(String rutaRelativa);

    /** Elimina un archivo anterior (silencioso si no existe). */
    void eliminar(String rutaRelativa);
}
