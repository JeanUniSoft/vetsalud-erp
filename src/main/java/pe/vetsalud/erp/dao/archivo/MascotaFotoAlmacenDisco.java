package pe.vetsalud.erp.dao.archivo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.MascotaFotoAlmacen;

/**
 * Guarda las fotos de mascotas en disco, fuera del WAR (no se sirven como archivos estáticos: pasan por un servlet
 * con control de acceso). Directorio: propiedad del sistema {@code vetsalud.fotos} o {@code ~/vetsalud-fotos}.
 */
public class MascotaFotoAlmacenDisco implements MascotaFotoAlmacen {

    private static final Logger LOG = LoggerFactory.getLogger(MascotaFotoAlmacenDisco.class);
    private static final String SUBCARPETA = "mascotas";

    private final Path base;

    public MascotaFotoAlmacenDisco(Path base) {
        this.base = Preconditions.checkNotNull(base).toAbsolutePath().normalize();
    }

    public static MascotaFotoAlmacenDisco desdeConfiguracion() {
        return new MascotaFotoAlmacenDisco(Paths.get(System.getProperty("vetsalud.fotos",
                System.getProperty("user.home") + "/vetsalud-fotos")));
    }

    @Override
    public String guardar(int idMascota, byte[] contenido, String extension) throws IOException {
        Preconditions.checkArgument(extension.equals("jpg") || extension.equals("png"), "Extensión no permitida");
        Path carpeta = base.resolve(SUBCARPETA);
        Files.createDirectories(carpeta);
        // Nombre generado: el nombre original del usuario nunca llega al sistema de archivos (path traversal)
        String nombre = "m" + idMascota + "-" + UUID.randomUUID() + "." + extension;
        Files.write(carpeta.resolve(nombre), contenido, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return SUBCARPETA + "/" + nombre;
    }

    @Override
    public Optional<Path> ubicar(String rutaRelativa) {
        if (StringUtils.isBlank(rutaRelativa)) {
            return Optional.empty();
        }
        Path archivo = base.resolve(rutaRelativa).normalize();
        if (!archivo.startsWith(base) || !Files.isRegularFile(archivo)) {
            return Optional.empty();
        }
        return Optional.of(archivo);
    }

    @Override
    public void eliminar(String rutaRelativa) {
        ubicar(rutaRelativa).ifPresent(archivo -> {
            try {
                Files.deleteIfExists(archivo);
            } catch (IOException e) {
                LOG.warn("No se pudo eliminar la foto anterior {}: {}", rutaRelativa, e.getMessage());
            }
        });
    }
}
