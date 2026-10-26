package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import pe.vetsalud.erp.dao.UsuarioDao;
import pe.vetsalud.erp.modelo.Usuario;

/** Doble de prueba de UsuarioDao: permite probar el servicio sin base de datos (TDD, DIP). */
public class UsuarioDaoEnMemoria implements UsuarioDao {

    public final Map<Integer, Usuario> usuarios = new HashMap<>();
    public final List<String> accesos = new ArrayList<>();
    public final Map<String, Set<String>> permisos = new HashMap<>();

    public void agregar(Usuario u) {
        usuarios.put(u.idUsuario(), u);
    }

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) {
        return usuarios.values().stream().filter(u -> u.usuario().equalsIgnoreCase(usuario)).findFirst();
    }

    @Override
    public Optional<Usuario> buscarPorId(int idUsuario) {
        return Optional.ofNullable(usuarios.get(idUsuario));
    }

    @Override
    public Optional<Usuario> buscarPorUsuarioOCorreo(String dato) {
        return usuarios.values().stream()
                .filter(u -> u.usuario().equalsIgnoreCase(dato) || u.correo().equalsIgnoreCase(dato)).findFirst();
    }

    @Override
    public Set<String> permisosDeRol(String rol) {
        return permisos.getOrDefault(rol, Set.of());
    }

    @Override
    public Optional<Integer> clienteVinculado(int idUsuario) {
        return Optional.empty();
    }

    @Override
    public void registrarIntento(Integer idUsuario, String usuarioIngresado, String ip, boolean exitoso) {
        accesos.add(usuarioIngresado + ":" + (exitoso ? "OK" : "FALLO"));
    }

    @Override
    public void registrarFallo(int idUsuario, int intentos, boolean bloquear) {
        Usuario u = usuarios.get(idUsuario);
        usuarios.put(idUsuario, new Usuario(u.idUsuario(), u.rol(), u.nombres(), u.apellidos(), u.usuario(),
                u.correo(), u.claveHash(), u.colegiatura(), intentos, bloquear, u.activo(),
                u.debeCambiarClave(), u.ultimoAcceso()));
    }

    @Override
    public void registrarIngresoExitoso(int idUsuario) {
        Usuario u = usuarios.get(idUsuario);
        usuarios.put(idUsuario, new Usuario(u.idUsuario(), u.rol(), u.nombres(), u.apellidos(), u.usuario(),
                u.correo(), u.claveHash(), u.colegiatura(), 0, u.bloqueado(), u.activo(),
                u.debeCambiarClave(), u.ultimoAcceso()));
    }

    @Override
    public void cambiarClave(int idUsuario, String nuevoHash, boolean debeCambiar) {
        Usuario u = usuarios.get(idUsuario);
        usuarios.put(idUsuario, new Usuario(u.idUsuario(), u.rol(), u.nombres(), u.apellidos(), u.usuario(),
                u.correo(), nuevoHash, u.colegiatura(), 0, false, u.activo(), debeCambiar, u.ultimoAcceso()));
    }
}
