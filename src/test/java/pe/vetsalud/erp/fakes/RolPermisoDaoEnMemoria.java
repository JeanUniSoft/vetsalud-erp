package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import pe.vetsalud.erp.dao.RolPermisoDao;
import pe.vetsalud.erp.modelo.PermisoFila;
import pe.vetsalud.erp.modelo.RolResumen;

/** Doble de prueba de RolPermisoDao: matriz en memoria y registro de los cambios aplicados. */
public class RolPermisoDaoEnMemoria implements RolPermisoDao {

    /** código de permiso → módulo. */
    public final Map<String, String> permisos = new LinkedHashMap<>();
    public final List<String> roles = new ArrayList<>();
    public final SetMultimap<String, String> asignacion = HashMultimap.create();
    public SetMultimap<String, String> ultimoAgregar;
    public SetMultimap<String, String> ultimoQuitar;

    @Override
    public List<RolResumen> roles() {
        List<RolResumen> lista = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            String r = roles.get(i);
            lista.add(new RolResumen(i + 1, r, "Rol " + r, true, 1, asignacion.get(r).size()));
        }
        return lista;
    }

    @Override
    public List<PermisoFila> matriz() {
        List<PermisoFila> filas = new ArrayList<>();
        int id = 1;
        for (Map.Entry<String, String> p : permisos.entrySet()) {
            List<String> con = roles.stream().filter(r -> asignacion.containsEntry(r, p.getKey())).toList();
            filas.add(new PermisoFila(id++, p.getKey(), p.getValue(), "Permiso " + p.getKey(), new java.util.TreeSet<>(con)));
        }
        return filas;
    }

    @Override
    public void aplicar(SetMultimap<String, String> agregar, SetMultimap<String, String> quitar, int idActor) {
        ultimoAgregar = HashMultimap.create(agregar);
        ultimoQuitar = HashMultimap.create(quitar);
        quitar.entries().forEach(e -> asignacion.remove(e.getKey(), e.getValue()));
        agregar.entries().forEach(e -> asignacion.put(e.getKey(), e.getValue()));
    }
}
