package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;

import pe.vetsalud.erp.modelo.Parametro;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * Edición de parámetros del sistema desde P12 (RF-24 configurable, RNF-05). Valida que el valor sea
 * entero y esté en un rango razonable (Guava {@link Range}) y delega en el {@link ParametroService}
 * compartido, que guarda e invalida su caché.
 */
public class AdminParametroService {

    /** Rangos permitidos por parámetro (lista blanca: otro parámetro no se puede editar). */
    public static final Map<String, Range<Integer>> RANGOS = ImmutableMap.of(
            ParametroService.DIAS_POR_VENCER, Range.closed(1, 365),
            ParametroService.MAX_INTENTOS_LOGIN, Range.closed(3, 10),
            ParametroService.DESCUENTO_MAX_PCT, Range.closed(0, 10),   // RN-08: la BD (ck_venta_descuento) fija el tope en 10 %
            ParametroService.MINUTOS_SESION, Range.closed(5, 60));

    private final ParametroService parametros;

    public AdminParametroService(ParametroService parametros) {
        this.parametros = Preconditions.checkNotNull(parametros);
    }

    public List<Parametro> todos() throws SQLException {
        return parametros.todos();
    }

    public void actualizar(String clave, String valor, UsuarioSesion actor) throws SQLException {
        if (actor == null || !actor.puede(AdminUsuarioService.PERMISO)) {
            throw new ReglaNegocioException("Su rol no tiene el permiso " + AdminUsuarioService.PERMISO
                    + " para cambiar parámetros.");
        }
        Range<Integer> rango = RANGOS.get(StringUtils.defaultString(clave));
        if (rango == null) {
            throw new ReglaNegocioException("El parámetro indicado no se puede modificar desde la aplicación.");
        }
        String v = StringUtils.trimToEmpty(valor);
        if (!NumberUtils.isDigits(v) || v.length() > 4) {
            throw new ReglaNegocioException("El valor de " + clave + " debe ser un número entero.");
        }
        int numero = Integer.parseInt(v);
        if (!rango.contains(numero)) {
            throw new ReglaNegocioException("El valor de " + clave + " debe estar entre " + rango.lowerEndpoint()
                    + " y " + rango.upperEndpoint() + ".");
        }
        // ParametroService guarda, invalida su caché y registra PARAMETRO_ACTUALIZAR en el log de auditoría
        parametros.actualizar(clave, String.valueOf(numero), actor.idUsuario(), actor.usuario());
    }
}
