package pe.vetsalud.erp.web;

import pe.vetsalud.erp.dao.jdbc.AlertaDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.ParametroDaoJdbc;
import pe.vetsalud.erp.dao.jdbc.UsuarioDaoJdbc;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.seguridad.ClaveHasher;
import pe.vetsalud.erp.servicio.AlertaService;
import pe.vetsalud.erp.servicio.AutenticacionService;
import pe.vetsalud.erp.servicio.ParametroService;

/**
 * Raíz de composición: arma los servicios compartidos con sus DAO JDBC (inyección de dependencias manual).
 * Cada módulo crea además sus propios servicios en el init() de su servlet.
 */
public final class Servicios {

    private static final ParametroService PARAMETROS = new ParametroService(new ParametroDaoJdbc(Db.get()));
    private static final AlertaService ALERTAS = new AlertaService(new AlertaDaoJdbc(Db.get()));
    private static final AutenticacionService AUTENTICACION = new AutenticacionService(
            new UsuarioDaoJdbc(Db.get()), new AlertaDaoJdbc(Db.get()), new ClaveHasher(), PARAMETROS);

    private Servicios() {
    }

    public static ParametroService parametros() {
        return PARAMETROS;
    }

    public static AlertaService alertas() {
        return ALERTAS;
    }

    public static AutenticacionService autenticacion() {
        return AUTENTICACION;
    }
}
