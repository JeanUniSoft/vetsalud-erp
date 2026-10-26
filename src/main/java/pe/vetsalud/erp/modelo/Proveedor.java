package pe.vetsalud.erp.modelo;

/** Proveedor de mercadería (RF-21). Se desactiva en lugar de borrarse. */
public record Proveedor(Integer idProveedor, String ruc, String razonSocial, String telefono, String correo,
                        boolean activo) {

    public Proveedor conId(int id) {
        return new Proveedor(id, ruc, razonSocial, telefono, correo, activo);
    }
}
