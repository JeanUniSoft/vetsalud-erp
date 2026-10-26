package pe.vetsalud.erp.web;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import pe.vetsalud.erp.dao.MascotaFotoAlmacen;
import pe.vetsalud.erp.dao.archivo.MascotaFotoAlmacenDisco;
import pe.vetsalud.erp.dao.jdbc.CatalogoMascotaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ClienteDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.MascotaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.PortalDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.RecordatorioDaoJdbc;
import pe.vetsalud.erp.notificacion.NotificadorCorreoLog;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.servicio.ClienteService;
import pe.vetsalud.erp.servicio.MascotaService;
import pe.vetsalud.erp.servicio.PortalService;
import pe.vetsalud.erp.servicio.RecordatorioService;

/**
 * Raíz de composición del módulo de mascotas, portal y recordatorios (inyección de dependencias manual):
 * arma cada servicio con sus DAO JDBC, el almacén de fotos en disco y el notificador por log.
 * Un único MascotaService comparte la caché Guava de especies y razas entre las pantallas.
 */
final class MascotaModulo {

    private static final MascotaFotoAlmacen FOTOS = MascotaFotoAlmacenDisco.desdeConfiguracion();
    private static final ClienteService CLIENTES = new ClienteService(new ClienteDaoJdbc(Db.get()));
    private static final MascotaService MASCOTAS = new MascotaService(new MascotaDaoJdbc(Db.get()),
            new CatalogoMascotaDaoJdbc(Db.get()), new ClienteDaoJdbc(Db.get()), FOTOS);
    private static final PortalService PORTAL = new PortalService(new PortalDaoJdbc(Db.get()));
    private static final RecordatorioService RECORDATORIOS =
            new RecordatorioService(new RecordatorioDaoJdbc(Db.get()), new NotificadorCorreoLog());

    private MascotaModulo() {
    }

    static ClienteService clientes() {
        return CLIENTES;
    }

    static MascotaService mascotas() {
        return MASCOTAS;
    }

    static PortalService portal() {
        return PORTAL;
    }

    static RecordatorioService recordatorios() {
        return RECORDATORIOS;
    }

    static MascotaFotoAlmacen fotos() {
        return FOTOS;
    }

    /** Identificador positivo de la URL o del formulario; null si falta o no es un entero válido. */
    static Integer idValido(String valor) {
        String v = StringUtils.trimToNull(valor);
        if (v == null || !NumberUtils.isDigits(v) || v.length() > 9) {
            return null;
        }
        int id = Integer.parseInt(v);
        return id > 0 ? id : null;
    }
}
