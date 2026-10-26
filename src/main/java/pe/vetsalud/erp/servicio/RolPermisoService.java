package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import pe.vetsalud.erp.dao.RolPermisoDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.PermisoFila;
import pe.vetsalud.erp.modelo.RolResumen;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * RF-02 Administrar la matriz de permisos por rol. Calcula la diferencia entre la matriz actual y la
 * enviada (Guava {@link Sets#difference}) y la aplica en una sola transacción.
 * Regla de continuidad: el administrador no puede quitarle a su propio rol USUARIO_ADMINISTRAR, para
 * que el sistema nunca se quede sin quien administre usuarios.
 */
public class RolPermisoService {

    public static final String PERMISO = AdminUsuarioService.PERMISO;

    private final RolPermisoDao dao;

    public RolPermisoService(RolPermisoDao dao) {
        this.dao = Preconditions.checkNotNull(dao);
    }

    public List<RolResumen> roles() throws SQLException {
        return dao.roles();
    }

    public List<PermisoFila> matriz() throws SQLException {
        return dao.matriz();
    }

    /** Asignaciones actuales como multimapa rol → permisos. */
    SetMultimap<String, String> actual() throws SQLException {
        SetMultimap<String, String> m = HashMultimap.create();
        for (PermisoFila p : dao.matriz()) {
            p.roles().forEach(r -> m.put(r, p.codigo()));
        }
        return m;
    }

    /**
     * Guarda la matriz enviada por el formulario.
     *
     * @param marcadas valores "ROL|PERMISO" de las casillas marcadas
     * @return cantidad de cambios aplicados (altas + bajas)
     */
    public int guardar(Collection<String> marcadas, UsuarioSesion actor) throws SQLException {
        if (actor == null || !actor.puede(PERMISO)) {
            throw new ReglaNegocioException("Su rol no tiene el permiso " + PERMISO + " para esta acción.");
        }
        Set<String> roles = dao.roles().stream().map(RolResumen::nombre).collect(Collectors.toSet());
        Set<String> permisos = dao.matriz().stream().map(PermisoFila::codigo).collect(Collectors.toSet());
        SetMultimap<String, String> nueva = HashMultimap.create();
        for (String valor : marcadas == null ? List.<String>of() : marcadas) {
            List<String> partes = Splitter.on('|').trimResults().splitToList(StringUtils.defaultString(valor));
            if (partes.size() != 2 || !roles.contains(partes.get(0)) || !permisos.contains(partes.get(1))) {
                throw new ReglaNegocioException("La matriz enviada contiene un rol o permiso desconocido.");
            }
            nueva.put(partes.get(0), partes.get(1));
        }
        if (!nueva.containsEntry(actor.rol(), PERMISO)) {
            throw new ReglaNegocioException("No puede quitarle a su propio rol (" + actor.rol() + ") el permiso "
                    + PERMISO + ": el sistema se quedaría sin quien administre usuarios.");
        }
        SetMultimap<String, String> actual = actual();
        SetMultimap<String, String> agregar = HashMultimap.create();
        SetMultimap<String, String> quitar = HashMultimap.create();
        for (String rol : roles) {
            Sets.difference(nueva.get(rol), actual.get(rol)).forEach(p -> agregar.put(rol, p));
            Sets.difference(actual.get(rol), nueva.get(rol)).forEach(p -> quitar.put(rol, p));
        }
        int cambios = agregar.size() + quitar.size();
        if (cambios == 0) {
            throw new ReglaNegocioException("No hay cambios que guardar en la matriz de permisos.");
        }
        dao.aplicar(agregar, quitar, actor.idUsuario());
        Auditoria.registrar("PERMISOS_ACTUALIZAR", "rol_permiso", "agregados=" + agregar + " quitados=" + quitar
                + " por=" + actor.usuario());
        return cambios;
    }
}
