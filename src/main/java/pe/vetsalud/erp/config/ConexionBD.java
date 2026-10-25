package pe.vetsalud.erp.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abre conexiones JDBC a PostgreSQL con los datos de vetsalud.properties.
 * Hay dos perfiles: la aplicación (vetsalud_app, lectura/escritura con privilegio mínimo)
 * y los reportes (vetsalud_reportes, solo lectura sobre vistas).
 */
public class ConexionBD {

    private static final Logger LOG = LoggerFactory.getLogger(ConexionBD.class);

    private final String url;
    private final String usuario;
    private final String clave;
    private final boolean soloLectura;

    public ConexionBD(String url, String usuario, String clave, boolean soloLectura) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
        this.soloLectura = soloLectura;
    }

    /** Conexión de la aplicación web (rol vetsalud_app). */
    public static ConexionBD desdeConfiguracion() {
        Properties p = Configuracion.cargar();
        return new ConexionBD(p.getProperty("db.url"), p.getProperty("db.usuario"),
                StringUtils.defaultIfBlank(System.getenv("VETSALUD_DB_CLAVE"), p.getProperty("db.clave")), false);
    }

    /** Conexión de solo lectura para reportes (rol vetsalud_reportes, S10). */
    public static ConexionBD paraReportes() {
        Properties p = Configuracion.cargar();
        return new ConexionBD(p.getProperty("db.url"), p.getProperty("db.reportes.usuario"),
                StringUtils.defaultIfBlank(System.getenv("VETSALUD_DB_CLAVE_REPORTES"),
                        p.getProperty("db.reportes.clave")), true);
    }

    public Connection abrir() throws SQLException {
        // Nunca se registra la clave (RNF-16)
        LOG.debug("Abriendo conexión a {} con el rol {}", url, usuario);
        Connection cn = DriverManager.getConnection(url, usuario, clave);
        cn.setReadOnly(soloLectura);
        return cn;
    }

    /** Acceso a vetsalud.properties. */
    public static final class Configuracion {
        private Configuracion() {
        }

        public static Properties cargar() {
            Properties p = new Properties();
            try (InputStream in = ConexionBD.class.getResourceAsStream("/vetsalud.properties")) {
                if (in == null) {
                    throw new IllegalStateException("No se encontró vetsalud.properties en el classpath");
                }
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                return p;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
