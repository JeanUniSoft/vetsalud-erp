package pe.vetsalud.erp.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** S13 · Partición de equivalencias y valores límite de la política de contraseñas (RF-03). */
class PoliticaClaveTest {

    @Test
    @DisplayName("Valor límite: 7 caracteres se rechaza y 8 se acepta")
    void limiteDeLongitud() {
        assertFalse(PoliticaClave.incumplimientos("Abcd12!", "ltorres").isEmpty());
        assertTrue(PoliticaClave.incumplimientos("Abcd123!", "ltorres").isEmpty());
    }

    @ParameterizedTest(name = "{0} incumple: {1}")
    @CsvSource({"abcd123!,mayúscula", "ABCD123!,minúscula", "Abcdefg!,número", "Abcd1234,símbolo"})
    void cadaClaseInvalida(String clave, String falta) {
        var faltas = PoliticaClave.incumplimientos(clave, "ltorres");
        assertEquals(1, faltas.size(), faltas.toString());
        assertTrue(faltas.get(0).contains(falta), faltas.toString());
    }

    @Test
    @DisplayName("La clave no puede contener el usuario, sin importar mayúsculas")
    void contieneUsuario() {
        assertFalse(PoliticaClave.incumplimientos("LTorres2026!", "ltorres").isEmpty());
    }

    @Test
    @DisplayName("Una clave nula se rechaza sin errores")
    void nula() {
        assertFalse(PoliticaClave.incumplimientos(null, "ltorres").isEmpty());
    }
}
