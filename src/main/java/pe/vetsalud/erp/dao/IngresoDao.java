package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.IngresoDocumento;
import pe.vetsalud.erp.modelo.IngresoResumen;
import pe.vetsalud.erp.modelo.Lote;

/** Ingresos de mercadería por documento de compra (RF-21, UC-09). */
public interface IngresoDao {

    /** Documento único por proveedor (UNIQUE id_proveedor, tipo_doc, num_doc). */
    boolean existeDocumento(int idProveedor, String tipoDoc, String numDoc) throws SQLException;

    /** Lote existente del mismo producto y código (se reutiliza en el ingreso). */
    Optional<Lote> lotePorCodigo(int idProducto, String codigoLote) throws SQLException;

    /**
     * En UNA transacción: inserta el ingreso, crea o reutiliza cada lote y registra el movimiento
     * INGRESO del kardex (el trigger fn_kardex_aplicar aumenta el stock del lote).
     */
    long registrar(IngresoDocumento documento, int idUsuario) throws SQLException;

    List<IngresoResumen> recientes(int limite) throws SQLException;
}
