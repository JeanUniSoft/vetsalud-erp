package pe.vetsalud.erp.seguridad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RNF-04: contraseñas con hash robusto y sal. */
class ClaveHasherTest {

    private final ClaveHasher hasher = new ClaveHasher();

    @Test
    @DisplayName("El hash no contiene la clave y cambia en cada llamada por la sal aleatoria")
    void hashConSal() {
        String a = hasher.hashear("VetSalud2026!".toCharArray());
        String b = hasher.hashear("VetSalud2026!".toCharArray());
        assertTrue(a.startsWith("pbkdf2_sha256$210000$"));
        assertFalse(a.contains("VetSalud2026!"));
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Verifica la clave correcta y rechaza una incorrecta o un hash corrupto")
    void verificacion() {
        String h = hasher.hashear("VetSalud2026!".toCharArray());
        assertTrue(hasher.verificar("VetSalud2026!".toCharArray(), h));
        assertFalse(hasher.verificar("vetsalud2026!".toCharArray(), h));
        assertFalse(hasher.verificar("VetSalud2026!".toCharArray(), "$2a$10$hashBCryptAntiguo"));
    }

    @Test
    @DisplayName("Es compatible con los hashes de 09_datos_demo.sql generados fuera de Java")
    void compatibilidadConScriptSql() {
        assertTrue(hasher.verificar("VetSalud2026!".toCharArray(),
                "pbkdf2_sha256$210000$OiZ1kVjP1Y8n7KOOaL6vmQ==$yvlEQbgdBdOe3E1K/UoqoHo93GLSjxts/VMFa8ZxO6U="));
    }
}
