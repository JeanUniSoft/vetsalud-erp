package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.ClienteDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.Cliente;

/**
 * UC-02 Gestionar clientes (RF-05, RF-06; RN-01 documento único).
 * Responsabilidad única: reglas del cliente. No conoce HTTP ni SQL (SRP y DIP de SOLID).
 */
public class ClienteService {

    public static final int LIMITE_BUSQUEDA = 50;

    private final ClienteDao dao;

    public ClienteService(ClienteDao dao) {
        this.dao = Preconditions.checkNotNull(dao);
    }

    public List<Cliente> buscar(String texto) throws SQLException {
        return dao.buscar(StringUtils.trimToNull(texto), LIMITE_BUSQUEDA);
    }

    public Cliente obtener(int idCliente) throws SQLException {
        return dao.buscarPorId(idCliente).orElseThrow(() -> new ReglaNegocioException("El cliente no existe."));
    }

    public int registrar(Cliente datos, int idUsuario, String usuario) throws SQLException {
        Cliente c = normalizarYValidar(datos);
        if (dao.existeDocumento(c.tipoDocumento(), c.numDocumento(), null)) {
            throw new ReglaNegocioException("Ya existe un cliente con " + c.tipoDocumento() + " " + c.numDocumento() + " (RN-01).");
        }
        int id = dao.insertar(c, idUsuario);
        Auditoria.registrar("CLIENTE_REGISTRAR", "cliente", "id=" + id + " por=" + usuario);
        return id;
    }

    public void actualizar(Cliente datos, int idUsuario, String usuario) throws SQLException {
        Preconditions.checkArgument(datos.idCliente() != null, "Falta el identificador del cliente.");
        Cliente c = normalizarYValidar(datos);
        if (dao.existeDocumento(c.tipoDocumento(), c.numDocumento(), c.idCliente())) {
            throw new ReglaNegocioException("Otro cliente ya usa " + c.tipoDocumento() + " " + c.numDocumento() + " (RN-01).");
        }
        dao.actualizar(c, idUsuario);
        Auditoria.registrar("CLIENTE_ACTUALIZAR", "cliente", "id=" + c.idCliente() + " por=" + usuario);
    }

    /** Limpia espacios, capitaliza nombres y valida formato (RF-05). */
    Cliente normalizarYValidar(Cliente d) {
        String tipo = Validaciones.requerido(d.tipoDocumento(), "Tipo de documento");
        String numero = StringUtils.upperCase(StringUtils.deleteWhitespace(Validaciones.requerido(d.numDocumento(), "Número de documento")));
        Validaciones.documento(tipo, numero);
        String nombres = Validaciones.requerido(d.nombres(), tipo.equals("RUC") ? "Razón social" : "Nombres");
        String apellidos = StringUtils.trimToNull(d.apellidos());
        if (!tipo.equals("RUC") && apellidos == null) {
            throw new ReglaNegocioException("El campo «Apellidos» es obligatorio para personas naturales.");
        }
        String telefono = StringUtils.deleteWhitespace(Validaciones.requerido(d.telefono(), "Teléfono"));
        Validaciones.telefono(telefono);
        String correo = StringUtils.lowerCase(StringUtils.trimToNull(d.correo()));
        Validaciones.correo(correo);
        if (d.aceptaRecordatorios() && correo == null) {
            throw new ReglaNegocioException("Para enviar recordatorios se necesita un correo (RF-37).");
        }
        Validaciones.longitudMaxima(nombres, 80, "Nombres");
        Validaciones.longitudMaxima(apellidos, 80, "Apellidos");
        Validaciones.longitudMaxima(d.direccion(), 200, "Dirección");
        return new Cliente(d.idCliente(), tipo, numero,
                tipo.equals("RUC") ? nombres : WordUtils.capitalizeFully(nombres),
                apellidos == null ? null : WordUtils.capitalizeFully(apellidos), telefono, correo,
                StringUtils.trimToNull(d.direccion()), StringUtils.trimToNull(d.distrito()),
                d.aceptaRecordatorios(), d.activo(), d.creadoEn(), d.numMascotas());
    }
}
