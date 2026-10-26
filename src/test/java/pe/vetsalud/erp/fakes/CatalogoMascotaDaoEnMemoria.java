package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.List;

import pe.vetsalud.erp.dao.CatalogoMascotaDao;
import pe.vetsalud.erp.modelo.Especie;
import pe.vetsalud.erp.modelo.Raza;

/** Catálogo con dos especies y tres razas; cuenta las consultas para verificar la caché. */
public class CatalogoMascotaDaoEnMemoria implements CatalogoMascotaDao {

    public static final int CANINO = 1;
    public static final int FELINO = 2;
    public static final int LABRADOR = 3;
    public static final int MESTIZO_CANINO = 4;
    public static final int SIAMES = 5;

    public final List<Especie> especies = new ArrayList<>(List.of(new Especie(CANINO, "Canino"), new Especie(FELINO, "Felino")));
    public final List<Raza> razas = new ArrayList<>(List.of(
            new Raza(LABRADOR, CANINO, "Canino", "Labrador Retriever"),
            new Raza(MESTIZO_CANINO, CANINO, "Canino", "Mestizo"),
            new Raza(SIAMES, FELINO, "Felino", "Siamés")));
    public int consultasEspecies;
    public int consultasRazas;

    @Override
    public List<Especie> especies() {
        consultasEspecies++;
        return List.copyOf(especies);
    }

    @Override
    public List<Raza> razas() {
        consultasRazas++;
        return List.copyOf(razas);
    }
}
