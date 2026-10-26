package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.math.NumberUtils;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;

import pe.vetsalud.erp.dao.ParametroDao;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.Parametro;

/**
 * Parámetros del sistema con caché de Google Guava (5 minutos): se consultan en cada login y en las
 * alertas, así se evita ir a la BD en cada solicitud (RNF-01). Al actualizar se invalida la caché.
 */
public class ParametroService {

    public static final String MAX_INTENTOS_LOGIN = "MAX_INTENTOS_LOGIN";
    public static final String DESCUENTO_MAX_PCT = "DESCUENTO_MAX_PCT";
    public static final String DIAS_POR_VENCER = "DIAS_POR_VENCER";
    public static final String MINUTOS_SESION = "MINUTOS_SESION";

    private final ParametroDao dao;
    private final LoadingCache<String, String> cache;

    public ParametroService(ParametroDao dao) {
        this.dao = Preconditions.checkNotNull(dao);
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(50)
                .build(new CacheLoader<>() {
                    @Override
                    public String load(String clave) throws SQLException {
                        return dao.valor(clave).orElse("");
                    }
                });
    }

    public int entero(String clave, int porDefecto) {
        try {
            return NumberUtils.toInt(cache.get(clave), porDefecto);
        } catch (ExecutionException | UncheckedExecutionException e) {
            return porDefecto;
        }
    }

    public List<Parametro> todos() throws SQLException {
        return dao.todos();
    }

    public void actualizar(String clave, String valor, int idUsuario, String usuario) throws SQLException {
        Preconditions.checkArgument(NumberUtils.isDigits(valor), "El valor de %s debe ser un número entero positivo.", clave);
        dao.actualizar(clave, valor, idUsuario);
        cache.invalidate(clave);
        Auditoria.registrar("PARAMETRO_ACTUALIZAR", "parametro", "clave=" + clave + " valor=" + valor + " por=" + usuario);
    }
}
