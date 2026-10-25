package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.util.List;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.AlertaDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.Alerta;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-38: alertas internas pendientes por rol y su atención con usuario y fecha. */
public class AlertaService {

    private final AlertaDao dao;

    public AlertaService(AlertaDao dao) {
        this.dao = Preconditions.checkNotNull(dao);
    }

    public List<Alerta> pendientes(UsuarioSesion usuario) throws SQLException {
        return dao.pendientesPorRol(usuario.rol());
    }

    public List<Alerta> recientes() throws SQLException {
        return dao.recientes(50);
    }

    public void crear(String rolDestino, String tipo, String mensaje) throws SQLException {
        dao.crear(rolDestino, tipo, mensaje);
    }

    public void atender(long idAlerta, UsuarioSesion usuario) throws SQLException {
        if (!usuario.puede("ALERTA_ATENDER")) {
            throw new ReglaNegocioException("Su rol no puede marcar alertas como atendidas.");
        }
        if (!dao.atender(idAlerta, usuario.idUsuario(), usuario.rol())) {
            throw new ReglaNegocioException("La alerta no existe, ya fue atendida o no corresponde a su rol.");
        }
        Auditoria.registrar("ALERTA_ATENDIDA", "alerta", "id=" + idAlerta + " por=" + usuario.usuario());
    }
}
