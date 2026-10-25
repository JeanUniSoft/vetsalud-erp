package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.ClienteDao;
import pe.vetsalud.erp.modelo.Cliente;

public class ClienteDaoEnMemoria implements ClienteDao {

    public final Map<Integer, Cliente> clientes = new LinkedHashMap<>();
    private int secuencia = 1;

    @Override
    public List<Cliente> buscar(String texto, int limite) {
        return new ArrayList<>(clientes.values()).stream()
                .filter(c -> texto == null || c.numDocumento().equals(texto) || c.nombreCompleto().toLowerCase().contains(texto.toLowerCase()))
                .limit(limite).toList();
    }

    @Override
    public Optional<Cliente> buscarPorId(int idCliente) {
        return Optional.ofNullable(clientes.get(idCliente));
    }

    @Override
    public boolean existeDocumento(String tipo, String numero, Integer exceptoId) {
        return clientes.values().stream().anyMatch(c -> c.tipoDocumento().equals(tipo) && c.numDocumento().equals(numero)
                && (exceptoId == null || !c.idCliente().equals(exceptoId)));
    }

    @Override
    public int insertar(Cliente cliente, int idUsuario) {
        int id = secuencia++;
        clientes.put(id, cliente.conId(id));
        return id;
    }

    @Override
    public void actualizar(Cliente cliente, int idUsuario) {
        clientes.put(cliente.idCliente(), cliente);
    }
}
