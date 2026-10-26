package pe.vetsalud.erp.modelo;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

/** Fila de la matriz rol × permiso (RF-02): el permiso y los roles que lo tienen. */
public record PermisoFila(int idPermiso, String codigo, String modulo, String descripcion, Set<String> roles) {

    public PermisoFila {
        roles = ImmutableSet.copyOf(roles);
    }
}
