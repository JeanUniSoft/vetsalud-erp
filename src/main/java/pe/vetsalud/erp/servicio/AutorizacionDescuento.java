package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import pe.vetsalud.erp.dao.UsuarioDao;
import pe.vetsalud.erp.modelo.Usuario;
import pe.vetsalud.erp.seguridad.ClaveHasher;
import pe.vetsalud.erp.seguridad.PoliticaAcceso;

/**
 * RN-08: un descuento mayor al límite (DESCUENTO_MAX_PCT) lo autoriza un ADMINISTRADOR activo, no bloqueado,
 * con el permiso DESCUENTO_AUTORIZAR, que escribe su usuario y contraseña en el mismo formulario del cajero.
 * La contraseña se verifica con PBKDF2 ({@link ClaveHasher}) y nunca se registra en los logs (RNF-16).
 */
public class AutorizacionDescuento {

    public static final String PERMISO = "DESCUENTO_AUTORIZAR";

    private static final Logger LOG = LoggerFactory.getLogger(AutorizacionDescuento.class);

    private final UsuarioDao usuarios;
    private final ClaveHasher hasher;

    public AutorizacionDescuento(UsuarioDao usuarios, ClaveHasher hasher) {
        this.usuarios = Preconditions.checkNotNull(usuarios);
        this.hasher = Preconditions.checkNotNull(hasher);
    }

    /**
     * Verifica las credenciales del administrador que autoriza.
     *
     * @return id del usuario autorizador (se guarda en venta.id_autoriza_descuento)
     */
    public int autorizar(String usuario, char[] clave) throws SQLException {
        try {
            if (StringUtils.isBlank(usuario) || clave == null || clave.length == 0) {
                throw new ReglaNegocioException("El descuento supera el límite permitido al cajero: ingrese el usuario "
                        + "y la contraseña de un administrador para autorizarlo (RN-08).");
            }
            Optional<Usuario> encontrado = usuarios.buscarPorUsuario(usuario.trim());
            boolean valido = encontrado.isPresent()
                    && hasher.verificar(clave, encontrado.get().claveHash())
                    && PoliticaAcceso.ADMINISTRADOR.equals(encontrado.get().rol())
                    && encontrado.get().activo() && !encontrado.get().bloqueado()
                    && usuarios.permisosDeRol(encontrado.get().rol()).contains(PERMISO);
            if (!valido) {
                // Mensaje genérico: no revela si falló el usuario, la contraseña o el rol
                LOG.warn("Autorización de descuento rechazada para el usuario {}", usuario.trim());
                throw new ReglaNegocioException("Autorización rechazada: se requiere un administrador activo con su "
                        + "contraseña correcta (RN-08).");
            }
            return encontrado.get().idUsuario();
        } finally {
            if (clave != null) {
                Arrays.fill(clave, '\0');
            }
        }
    }
}
