package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.UsuarioAdminDao;
import pe.vetsalud.erp.modelo.Opcion;
import pe.vetsalud.erp.modelo.UsuarioAdmin;

/** Doble de prueba de UsuarioAdminDao (TDD sin base de datos). */
public class UsuarioAdminDaoEnMemoria implements UsuarioAdminDao {

    public final Map<Integer, UsuarioAdmin> usuarios = new LinkedHashMap<>();
    public final Map<Integer, String> hashes = new HashMap<>();
    /** documento → cliente (id, nombre). */
    public final Map<String, Opcion> clientes = new HashMap<>();
    /** id_usuario → id_cliente (tabla cliente_usuario). */
    public final Map<Integer, Integer> vinculos = new HashMap<>();
    public final List<String> roles = new ArrayList<>(List.of("ADMINISTRADOR", "RECEPCION", "VETERINARIO",
            "TECNICO", "FARMACIA", "CAJERO", "CLIENTE"));
    private int secuencia = 100;

    public UsuarioAdmin agregar(int id, String rol, String usuario, String correo) {
        UsuarioAdmin u = new UsuarioAdmin(id, rol, "Nombre", "Apellido", usuario, correo, null, 0, false, true,
                false, null, null, null, null);
        usuarios.put(id, u);
        return u;
    }

    @Override
    public List<UsuarioAdmin> listar(String texto, String rol, String estado) {
        return usuarios.values().stream().filter(u -> rol == null || u.rol().equals(rol)).toList();
    }

    @Override
    public Optional<UsuarioAdmin> buscarPorId(int idUsuario) {
        return Optional.ofNullable(usuarios.get(idUsuario));
    }

    @Override
    public boolean existeUsuario(String usuario, Integer exceptoId) {
        return usuarios.values().stream().anyMatch(u -> u.usuario().equalsIgnoreCase(usuario)
                && (exceptoId == null || !u.idUsuario().equals(exceptoId)));
    }

    @Override
    public boolean existeCorreo(String correo, Integer exceptoId) {
        return usuarios.values().stream().anyMatch(u -> u.correo().equalsIgnoreCase(correo)
                && (exceptoId == null || !u.idUsuario().equals(exceptoId)));
    }

    @Override
    public List<String> roles() {
        return roles;
    }

    @Override
    public Optional<Opcion> clientePorDocumento(String numDocumento) {
        return Optional.ofNullable(clientes.get(numDocumento));
    }

    @Override
    public boolean clienteTieneUsuario(int idCliente, Integer exceptoUsuario) {
        return vinculos.entrySet().stream().anyMatch(e -> e.getValue() == idCliente
                && (exceptoUsuario == null || !e.getKey().equals(exceptoUsuario)));
    }

    @Override
    public List<Opcion> clientesSinUsuario(int limite) {
        return clientes.values().stream().filter(c -> !vinculos.containsValue((int) c.id())).limit(limite).toList();
    }

    @Override
    public int insertar(UsuarioAdmin d, String claveHash, Integer idCliente, int idActor) {
        int id = ++secuencia;
        usuarios.put(id, new UsuarioAdmin(id, d.rol(), d.nombres(), d.apellidos(), d.usuario(), d.correo(),
                d.colegiatura(), 0, false, true, true, null, null, d.documentoCliente(), null));
        hashes.put(id, claveHash);
        if (idCliente != null) {
            vinculos.put(id, idCliente);
        }
        return id;
    }

    @Override
    public void actualizar(UsuarioAdmin d, Integer idCliente, int idActor) {
        UsuarioAdmin a = usuarios.get(d.idUsuario());
        usuarios.put(d.idUsuario(), new UsuarioAdmin(d.idUsuario(), d.rol(), d.nombres(), d.apellidos(), d.usuario(),
                d.correo(), d.colegiatura(), a.intentosFallidos(), a.bloqueado(), a.activo(), a.debeCambiarClave(),
                a.ultimoAcceso(), a.creadoEn(), d.documentoCliente(), null));
        if (idCliente != null) {
            vinculos.put(d.idUsuario(), idCliente);
        }
    }

    @Override
    public void restablecerClave(int idUsuario, String claveHash, int idActor) {
        UsuarioAdmin a = usuarios.get(idUsuario);
        usuarios.put(idUsuario, new UsuarioAdmin(a.idUsuario(), a.rol(), a.nombres(), a.apellidos(), a.usuario(),
                a.correo(), a.colegiatura(), 0, false, a.activo(), true, a.ultimoAcceso(), a.creadoEn(),
                a.documentoCliente(), a.cliente()));
        hashes.put(idUsuario, claveHash);
    }

    @Override
    public void desbloquear(int idUsuario, int idActor) {
        UsuarioAdmin a = usuarios.get(idUsuario);
        usuarios.put(idUsuario, new UsuarioAdmin(a.idUsuario(), a.rol(), a.nombres(), a.apellidos(), a.usuario(),
                a.correo(), a.colegiatura(), 0, false, a.activo(), a.debeCambiarClave(), a.ultimoAcceso(),
                a.creadoEn(), a.documentoCliente(), a.cliente()));
    }

    @Override
    public void cambiarEstado(int idUsuario, boolean activo, int idActor) {
        UsuarioAdmin a = usuarios.get(idUsuario);
        usuarios.put(idUsuario, new UsuarioAdmin(a.idUsuario(), a.rol(), a.nombres(), a.apellidos(), a.usuario(),
                a.correo(), a.colegiatura(), a.intentosFallidos(), a.bloqueado(), activo, a.debeCambiarClave(),
                a.ultimoAcceso(), a.creadoEn(), a.documentoCliente(), a.cliente()));
    }

    /** Marca la cuenta como bloqueada (simula N intentos fallidos). */
    public void bloquear(int idUsuario) {
        UsuarioAdmin a = usuarios.get(idUsuario);
        usuarios.put(idUsuario, new UsuarioAdmin(a.idUsuario(), a.rol(), a.nombres(), a.apellidos(), a.usuario(),
                a.correo(), a.colegiatura(), 5, true, a.activo(), a.debeCambiarClave(), a.ultimoAcceso(),
                a.creadoEn(), a.documentoCliente(), a.cliente()));
    }
}
