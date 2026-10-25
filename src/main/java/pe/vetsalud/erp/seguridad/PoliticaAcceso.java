package pe.vetsalud.erp.seguridad;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Matriz de acceso por rol (RF-02, tabla 28 del APF2: mapa de navegación y roles).
 * Cada prefijo de ruta indica qué roles pueden abrir esas pantallas. Las acciones sensibles
 * (firmar, anular, autorizar descuento, ajustar stock...) se controlan además con permisos
 * de la tabla rol_permiso (ver UsuarioSesion#puede).
 */
public final class PoliticaAcceso {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String RECEPCION = "RECEPCION";
    public static final String VETERINARIO = "VETERINARIO";
    public static final String TECNICO = "TECNICO";
    public static final String FARMACIA = "FARMACIA";
    public static final String CAJERO = "CAJERO";
    public static final String CLIENTE = "CLIENTE";

    private static final Set<String> PERSONAL =
            ImmutableSet.of(ADMINISTRADOR, RECEPCION, VETERINARIO, TECNICO, FARMACIA, CAJERO);

    /** Rutas que no requieren sesión. */
    public static final Set<String> PUBLICAS = ImmutableSet.of("/login", "/recuperar", "/static/", "/favicon.ico");

    /** Rutas a las que entra cualquier usuario autenticado. */
    private static final Set<String> COMUNES = ImmutableSet.of("/cuenta", "/logout", "/sin-permiso");

    // Prefijo de ruta → roles autorizados (pantallas P02 a P12)
    private static final Map<String, Set<String>> MATRIZ = ImmutableMap.<String, Set<String>>builder()
            .put("/panel", PERSONAL)                                                          // P02
            .put("/alertas", PERSONAL)                                                        // P02 / RF-38
            .put("/clientes", ImmutableSet.of(RECEPCION, VETERINARIO, TECNICO, CAJERO, ADMINISTRADOR)) // P03
            .put("/mascotas", ImmutableSet.of(RECEPCION, VETERINARIO, TECNICO, CAJERO, ADMINISTRADOR)) // P03
            .put("/citas", ImmutableSet.of(RECEPCION, VETERINARIO, TECNICO, ADMINISTRADOR))    // P04
            .put("/historia", ImmutableSet.of(VETERINARIO, TECNICO, ADMINISTRADOR))            // P05
            .put("/consultas", ImmutableSet.of(VETERINARIO, TECNICO, ADMINISTRADOR))           // P05 / P06
            .put("/inventario", ImmutableSet.of(FARMACIA, ADMINISTRADOR))                       // P07
            .put("/ventas", ImmutableSet.of(CAJERO, ADMINISTRADOR))                             // P08
            .put("/caja", ImmutableSet.of(CAJERO, ADMINISTRADOR))                               // P09
            .put("/reportes", ImmutableSet.of(ADMINISTRADOR))                                   // P10
            .put("/portal", ImmutableSet.of(CLIENTE))                                           // P11
            .put("/admin", ImmutableSet.of(ADMINISTRADOR))                                      // P12
            .build();

    private PoliticaAcceso() {
    }

    public static boolean esPublica(String ruta) {
        return PUBLICAS.stream().anyMatch(ruta::startsWith);
    }

    public static boolean permite(String rol, String ruta) {
        if (rol == null || ruta == null) {
            return false;
        }
        if (COMUNES.stream().anyMatch(ruta::startsWith)) {
            return true;
        }
        return MATRIZ.entrySet().stream()
                .filter(e -> ruta.equals(e.getKey()) || ruta.startsWith(e.getKey() + "/"))
                .findFirst()
                .map(e -> e.getValue().contains(rol))
                .orElse(false);
    }

    /** Pantalla inicial según el rol. */
    public static String inicio(String rol) {
        return CLIENTE.equals(rol) ? "/portal" : "/panel";
    }
}
