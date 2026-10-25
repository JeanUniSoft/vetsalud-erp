package pe.vetsalud.erp.seguridad;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

/**
 * Hash de contraseñas con PBKDF2-HMAC-SHA256, sal aleatoria de 16 bytes y 210 000 iteraciones
 * (recomendación OWASP). Formato almacenado: pbkdf2_sha256$iteraciones$sal$hash (RNF-04).
 * La comparación es en tiempo constante para no filtrar información por temporización.
 */
public final class ClaveHasher {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIJO = "pbkdf2_sha256";
    private static final int ITERACIONES = 210_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_CLAVE = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public String hashear(char[] clave) {
        Preconditions.checkArgument(clave != null && clave.length > 0, "La contraseña es obligatoria");
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] hash = derivar(clave, sal, ITERACIONES);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIJO + "$" + ITERACIONES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    public boolean verificar(char[] clave, String almacenado) {
        if (clave == null || almacenado == null) {
            return false;
        }
        List<String> partes = Splitter.on('$').splitToList(almacenado);
        if (partes.size() != 4 || !PREFIJO.equals(partes.get(0))) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes.get(1));
            byte[] sal = Base64.getDecoder().decode(partes.get(2));
            byte[] esperado = Base64.getDecoder().decode(partes.get(3));
            return MessageDigest.isEqual(esperado, derivar(clave, sal, iteraciones));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(char[] clave, byte[] sal, int iteraciones) {
        try {
            KeySpec spec = new PBEKeySpec(clave, sal, iteraciones, BITS_CLAVE);
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 no disponible en la JVM", e);
        }
    }
}
