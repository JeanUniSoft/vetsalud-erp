package pe.vetsalud.erp.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;

class MascaraDatosSensiblesTest {

    @Test
    void enmascaraDniCorreoCelularRucYClave() {
        assertEquals("DNI 45****12", MascaraDatosSensibles.enmascarar("DNI 45678912"));
        assertEquals("correo m***@gmail.com", MascaraDatosSensibles.enmascarar("correo maria.gonzales@gmail.com"));
        assertEquals("cel 9*****321", MascaraDatosSensibles.enmascarar("cel 987654321"));
        assertEquals("RUC 10*******67", MascaraDatosSensibles.enmascarar("RUC 10456789167"));
        assertEquals("clave=******", MascaraDatosSensibles.enmascarar("clave=Secreta123"));
    }

    @Test
    void noAlteraFechasNiImportes() {
        String texto = "Venta 2026-09-18 por S/ 183.00, 7 lotes en 45 ms";
        assertEquals(texto, MascaraDatosSensibles.enmascarar(texto));
    }

    @Test
    void elArchivoDeLogNoContieneDatosPersonales() throws IOException {
        Logger log = LoggerFactory.getLogger(MascaraDatosSensiblesTest.class);
        log.info("Prueba RNF-15: cliente 70123456 jperez@hotmail.com");
        ((LoggerContext) LoggerFactory.getILoggerFactory()).stop();   // vacía el appender asíncrono
        String contenido = Files.readString(Path.of(System.getProperty("vetsalud.logdir", "logs"), "vetsalud.log"));
        assertFalse(contenido.contains("70123456"));
        assertFalse(contenido.contains("jperez@"));
    }
}
