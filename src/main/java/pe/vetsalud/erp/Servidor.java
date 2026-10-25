package pe.vetsalud.erp;

import java.io.File;
import java.nio.file.Files;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.config.ConexionBD;

/**
 * Lanzador de desarrollo: levanta el mismo WAR en un Apache Tomcat 9 embebido.
 * En producción el WAR (target/vetsalud-erp.war) se despliega en un Tomcat 9 instalado (RNF-13).
 *
 * <pre>mvn compile exec:java   →   http://localhost:8080</pre>
 */
public final class Servidor {

    private static final Logger LOG = LoggerFactory.getLogger(Servidor.class);

    private Servidor() {
    }

    public static void main(String[] args) throws Exception {
        int puerto = Integer.parseInt(System.getProperty("puerto",
                ConexionBD.Configuracion.cargar().getProperty("app.puerto", "8080")));

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(puerto);
        tomcat.setBaseDir(Files.createTempDirectory("vetsalud-tomcat").toString());
        tomcat.getConnector().setProperty("relaxedQueryChars", "");

        Context ctx = tomcat.addWebapp("", new File("src/main/webapp").getAbsolutePath());
        ctx.setParentClassLoader(Servidor.class.getClassLoader());
        Rfc6265CookieProcessor cookies = new Rfc6265CookieProcessor();
        cookies.setSameSiteCookies("strict");            // la cookie de sesión no viaja en peticiones de otros sitios
        ctx.setCookieProcessor(cookies);

        StandardRoot recursos = new StandardRoot(ctx);
        recursos.addPreResources(new DirResourceSet(recursos, "/WEB-INF/classes",
                new File("target/classes").getAbsolutePath(), "/"));
        ctx.setResources(recursos);

        tomcat.start();
        LOG.info("VetSalud ERP disponible en http://localhost:{}", puerto);
        tomcat.getServer().await();
    }
}
