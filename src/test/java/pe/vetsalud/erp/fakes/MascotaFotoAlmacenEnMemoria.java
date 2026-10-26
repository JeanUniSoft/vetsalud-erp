package pe.vetsalud.erp.fakes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.MascotaFotoAlmacen;

public class MascotaFotoAlmacenEnMemoria implements MascotaFotoAlmacen {

    public final Map<String, byte[]> archivos = new LinkedHashMap<>();
    public final List<String> eliminados = new ArrayList<>();
    private int secuencia = 1;

    @Override
    public String guardar(int idMascota, byte[] contenido, String extension) {
        String ruta = "mascotas/m" + idMascota + "-" + (secuencia++) + "." + extension;
        archivos.put(ruta, contenido);
        return ruta;
    }

    @Override
    public Optional<Path> ubicar(String rutaRelativa) {
        return archivos.containsKey(rutaRelativa) ? Optional.of(Path.of(rutaRelativa)) : Optional.empty();
    }

    @Override
    public void eliminar(String rutaRelativa) {
        archivos.remove(rutaRelativa);
        eliminados.add(rutaRelativa);
    }
}
