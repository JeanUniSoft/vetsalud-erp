package pe.vetsalud.erp.dao.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.SetMultimap;

import pe.vetsalud.erp.dao.RolPermisoDao;
import pe.vetsalud.erp.modelo.PermisoFila;
import pe.vetsalud.erp.modelo.RolResumen;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.persistencia.Jdbc;

/** Implementación JDBC de {@link RolPermisoDao}. vetsalud_app tiene DELETE sobre rol_permiso (04_seguridad.sql). */
public class RolPermisoDaoJdbc implements RolPermisoDao {

    private final Db db;

    public RolPermisoDaoJdbc(Db db) {
        this.db = Preconditions.checkNotNull(db);
    }

    @Override
    public List<RolResumen> roles() throws SQLException {
        return db.consultar(cn -> Jdbc.lista(cn, """
                SELECT r.id_rol, r.nombre, r.descripcion, r.activo,
                       (SELECT count(*) FROM usuario u WHERE u.id_rol = r.id_rol AND u.activo) AS usuarios,
                       (SELECT count(*) FROM rol_permiso rp WHERE rp.id_rol = r.id_rol) AS permisos
                FROM rol r ORDER BY r.id_rol
                """, rs -> new RolResumen(rs.getInt("id_rol"), rs.getString("nombre"), rs.getString("descripcion"),
                        rs.getBoolean("activo"), rs.getInt("usuarios"), rs.getInt("permisos"))));
    }

    @Override
    public List<PermisoFila> matriz() throws SQLException {
        record Celda(int id, String codigo, String modulo, String descripcion, String rol) {
        }
        List<Celda> celdas = db.consultar(cn -> Jdbc.lista(cn, """
                SELECT p.id_permiso, p.codigo, p.modulo, p.descripcion, r.nombre AS rol
                FROM permiso p
                LEFT JOIN rol_permiso rp ON rp.id_permiso = p.id_permiso
                LEFT JOIN rol r ON r.id_rol = rp.id_rol
                ORDER BY p.modulo, p.codigo
                """, rs -> new Celda(rs.getInt("id_permiso"), rs.getString("codigo"), rs.getString("modulo"),
                        rs.getString("descripcion"), rs.getString("rol"))));
        Map<Integer, List<Celda>> porPermiso = new LinkedHashMap<>();
        celdas.forEach(c -> porPermiso.computeIfAbsent(c.id(), k -> new ArrayList<>()).add(c));
        List<PermisoFila> filas = new ArrayList<>();
        porPermiso.values().forEach(lista -> {
            Celda p = lista.get(0);
            TreeSet<String> roles = new TreeSet<>();
            lista.stream().map(Celda::rol).filter(r -> r != null).forEach(roles::add);
            filas.add(new PermisoFila(p.id(), p.codigo(), p.modulo(), p.descripcion(), roles));
        });
        return filas;
    }

    @Override
    public void aplicar(SetMultimap<String, String> agregar, SetMultimap<String, String> quitar, int idActor)
            throws SQLException {
        db.enTransaccion(idActor, cn -> {
            for (Map.Entry<String, String> e : quitar.entries()) {
                Jdbc.ejecutar(cn, """
                        DELETE FROM rol_permiso
                        WHERE id_rol = (SELECT id_rol FROM rol WHERE nombre = ?)
                          AND id_permiso = (SELECT id_permiso FROM permiso WHERE codigo = ?)
                        """, e.getKey(), e.getValue());
            }
            for (Map.Entry<String, String> e : agregar.entries()) {
                Jdbc.ejecutar(cn, """
                        INSERT INTO rol_permiso (id_rol, id_permiso)
                        SELECT r.id_rol, p.id_permiso FROM rol r, permiso p WHERE r.nombre = ? AND p.codigo = ?
                        ON CONFLICT DO NOTHING
                        """, e.getKey(), e.getValue());
            }
            return null;
        });
    }
}
