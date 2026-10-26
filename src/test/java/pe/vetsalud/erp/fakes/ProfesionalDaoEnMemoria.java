package pe.vetsalud.erp.fakes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.ProfesionalDao;
import pe.vetsalud.erp.modelo.Profesional;

/** Personal de prueba con los mismos ids de 05_datos_prueba.sql. */
public class ProfesionalDaoEnMemoria implements ProfesionalDao {

    public static final int RECEPCION = 2;
    public static final int SALAZAR = 3;
    public static final int MENDOZA = 4;
    public static final int TECNICO = 5;
    public static final int VET_INACTIVO = 20;

    public final Map<Integer, Profesional> personal = new LinkedHashMap<>();

    public ProfesionalDaoEnMemoria() {
        agregar(new Profesional(RECEPCION, "Lucía", "Torres Vega", "RECEPCION", null, true));
        agregar(new Profesional(SALAZAR, "Ana", "Salazar León", "VETERINARIO", "05233", true));
        agregar(new Profesional(MENDOZA, "Carlos", "Mendoza Ríos", "VETERINARIO", "04512", true));
        agregar(new Profesional(TECNICO, "Jorge", "Huamán Cruz", "TECNICO", null, true));
        agregar(new Profesional(VET_INACTIVO, "Pedro", "Retirado", "VETERINARIO", "01111", false));
    }

    public final void agregar(Profesional p) {
        personal.put(p.idUsuario(), p);
    }

    @Override
    public List<Profesional> veterinarios() {
        return personal.values().stream().filter(p -> p.esVeterinario() && p.activo()).toList();
    }

    @Override
    public List<Profesional> personalClinico() {
        return personal.values().stream()
                .filter(p -> p.activo() && (p.esVeterinario() || "TECNICO".equals(p.rol()))).toList();
    }

    @Override
    public Optional<Profesional> buscarPorId(int idUsuario) {
        return Optional.ofNullable(personal.get(idUsuario));
    }
}
