package pe.vetsalud.erp.fakes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import pe.vetsalud.erp.dao.IngresoDao;
import pe.vetsalud.erp.modelo.IngresoDocumento;
import pe.vetsalud.erp.modelo.IngresoResumen;
import pe.vetsalud.erp.modelo.Lote;

/** Doble de IngresoDao: guarda los documentos y comparte los lotes del ProductoDaoEnMemoria. */
public class IngresoDaoEnMemoria implements IngresoDao {

    public final List<IngresoDocumento> registrados = new ArrayList<>();
    public final Set<String> documentos = new HashSet<>();
    private final ProductoDaoEnMemoria productos;

    public IngresoDaoEnMemoria(ProductoDaoEnMemoria productos) {
        this.productos = productos;
    }

    @Override
    public boolean existeDocumento(int idProveedor, String tipoDoc, String numDoc) {
        return documentos.contains(idProveedor + "|" + tipoDoc + "|" + numDoc.toUpperCase());
    }

    @Override
    public Optional<Lote> lotePorCodigo(int idProducto, String codigoLote) {
        return productos.lotes.values().stream()
                .filter(l -> l.idProducto() == idProducto && l.codigoLote().equalsIgnoreCase(codigoLote)).findFirst();
    }

    @Override
    public long registrar(IngresoDocumento documento, int idUsuario) {
        registrados.add(documento);
        documentos.add(documento.idProveedor() + "|" + documento.tipoDoc() + "|" + documento.numDoc());
        return registrados.size();
    }

    @Override
    public List<IngresoResumen> recientes(int limite) {
        return List.of();
    }
}
