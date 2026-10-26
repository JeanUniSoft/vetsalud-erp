package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import pe.vetsalud.erp.dao.AuditoriaConsultaDao;
import pe.vetsalud.erp.modelo.AccesoRegistro;
import pe.vetsalud.erp.modelo.AuditoriaRegistro;
import pe.vetsalud.erp.modelo.CambioCampo;
import pe.vetsalud.erp.modelo.Opcion;

/**
 * Consultas de trazabilidad de P12 (RF-01 bitácora de accesos, RF-04 auditoría). Solo lectura (RNF-06):
 * no ofrece ningún método para modificar o borrar registros.
 * La diferencia entre valores_antes y valores_despues se calcula aquí y nunca muestra campos sensibles.
 */
public class AuditoriaConsultaService {

    public static final int LIMITE = 300;
    public static final int MAX_DIAS = 366;
    public static final Set<String> OPERACIONES = ImmutableSet.of("INSERT", "UPDATE", "DELETE");
    /** Campos que jamás se muestran aunque llegaran a estar en el JSONB (defensa en profundidad, RNF-15). */
    public static final Set<String> CAMPOS_OCULTOS = ImmutableSet.of("clave_hash", "clave", "contrasena", "password");

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AuditoriaConsultaDao dao;

    public AuditoriaConsultaService(AuditoriaConsultaDao dao) {
        this.dao = Preconditions.checkNotNull(dao);
    }

    /** Periodo por defecto: últimos 7 días. */
    public LocalDate[] periodo(LocalDate desde, LocalDate hasta, LocalDate hoy) {
        LocalDate h = hasta != null ? hasta : hoy;
        LocalDate d = desde != null ? desde : h.minusDays(6);
        if (d.isAfter(h)) {
            throw new ReglaNegocioException("La fecha «Desde» (" + FECHA.format(d) + ") no puede ser posterior a «Hasta» ("
                    + FECHA.format(h) + ").");
        }
        if (ChronoUnit.DAYS.between(d, h) + 1 > MAX_DIAS) {
            throw new ReglaNegocioException("El periodo no puede superar " + MAX_DIAS + " días.");
        }
        return new LocalDate[]{d, h};
    }

    public List<AuditoriaRegistro> buscar(String tabla, String operacion, Integer idUsuario, LocalDate desde,
                                          LocalDate hasta) throws SQLException {
        String op = StringUtils.upperCase(StringUtils.trimToNull(operacion));
        if (op != null && !OPERACIONES.contains(op)) {
            throw new ReglaNegocioException("La operación elegida no es válida.");
        }
        String t = StringUtils.trimToNull(tabla);
        if (t != null && !dao.tablas().contains(t)) {
            throw new ReglaNegocioException("La tabla elegida no tiene registros de auditoría.");
        }
        return dao.buscar(t, op, idUsuario, desde, hasta.plusDays(1), LIMITE);
    }

    public AuditoriaRegistro obtener(long idAuditoria) throws SQLException {
        return dao.buscarPorId(idAuditoria)
                .orElseThrow(() -> new ReglaNegocioException("El registro de auditoría no existe."));
    }

    /** Campos que cambiaron en el registro (todos los campos si es alta o eliminación). */
    public List<CambioCampo> cambios(long idAuditoria) throws SQLException {
        return diferencias(dao.valores(idAuditoria, true), dao.valores(idAuditoria, false));
    }

    /**
     * Diferencia campo a campo entre dos versiones (JSONB ya convertido a texto). Omite los campos ocultos
     * (clave_hash) y los que no cambiaron. Resultado ordenado por nombre de campo.
     */
    public static List<CambioCampo> diferencias(Map<String, String> antes, Map<String, String> despues) {
        Set<String> campos = new TreeSet<>();
        if (antes != null) {
            campos.addAll(antes.keySet());
        }
        if (despues != null) {
            campos.addAll(despues.keySet());
        }
        List<CambioCampo> cambios = new ArrayList<>();
        for (String campo : campos) {
            if (CAMPOS_OCULTOS.contains(campo.toLowerCase())) {
                continue;
            }
            boolean enAntes = antes != null && antes.containsKey(campo);
            boolean enDespues = despues != null && despues.containsKey(campo);
            String a = enAntes ? antes.get(campo) : null;
            String d = enDespues ? despues.get(campo) : null;
            if (enAntes && enDespues) {
                if (!Objects.equal(a, d)) {
                    cambios.add(new CambioCampo(campo, a, d, "MODIFICADO"));
                }
            } else if (enDespues) {
                cambios.add(new CambioCampo(campo, null, d, "AGREGADO"));
            } else {
                cambios.add(new CambioCampo(campo, a, null, "ELIMINADO"));
            }
        }
        return cambios;
    }

    public List<String> tablas() throws SQLException {
        return dao.tablas();
    }

    public List<Opcion> usuarios() throws SQLException {
        return dao.usuarios();
    }

    /** Totales de la bitácora mostrada: total, exitosos, fallidos y usuarios distintos. */
    public static Map<String, Long> resumenAccesos(List<AccesoRegistro> accesos) {
        long exitosos = accesos.stream().filter(AccesoRegistro::exitoso).count();
        long usuarios = accesos.stream().map(a -> StringUtils.lowerCase(a.usuarioIngresado())).distinct().count();
        return ImmutableMap.of("total", (long) accesos.size(), "exitosos", exitosos,
                "fallidos", accesos.size() - exitosos, "usuarios", usuarios);
    }

    /**
     * RF-01: bitácora de accesos.
     *
     * @param resultado EXITOSO, FALLIDO o null (todos)
     */
    public List<AccesoRegistro> accesos(String usuario, String resultado, LocalDate desde, LocalDate hasta)
            throws SQLException {
        String r = StringUtils.upperCase(StringUtils.trimToNull(resultado));
        Boolean exitoso = null;
        if (r != null) {
            exitoso = switch (r) {
                case "EXITOSO" -> Boolean.TRUE;
                case "FALLIDO" -> Boolean.FALSE;
                default -> throw new ReglaNegocioException("El filtro de resultado no es válido.");
            };
        }
        String u = StringUtils.trimToNull(usuario);
        Validaciones.longitudMaxima(u, 40, "Usuario");
        return dao.accesos(u, exitoso, desde, hasta.plusDays(1), LIMITE);
    }
}
