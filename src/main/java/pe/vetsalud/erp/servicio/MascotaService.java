package pe.vetsalud.erp.servicio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.UncheckedExecutionException;

import pe.vetsalud.erp.dao.CatalogoMascotaDao;
import pe.vetsalud.erp.dao.ClienteDao;
import pe.vetsalud.erp.dao.MascotaDao;
import pe.vetsalud.erp.dao.MascotaFotoAlmacen;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.CambioPropietario;
import pe.vetsalud.erp.modelo.Cliente;
import pe.vetsalud.erp.modelo.Especie;
import pe.vetsalud.erp.modelo.Mascota;
import pe.vetsalud.erp.modelo.MascotaCambio;
import pe.vetsalud.erp.modelo.Raza;

/**
 * UC-02 Gestionar mascotas (RF-07 registro con ficha única, RF-08 estado, foto, microchip y cambio de propietario
 * auditado; RN-02 toda mascota activa pertenece a un cliente activo).
 * Responsabilidad única: reglas de la mascota. No conoce HTTP ni SQL; recibe sus dependencias por constructor (DIP).
 *
 * <p>Regla de estados: ACTIVA ⇄ INACTIVA en ambos sentidos; FALLECIDA es definitiva (no vuelve a ACTIVA ni a
 * INACTIVA), porque el registro clínico de fallecimiento no debe revertirse desde la recepción.</p>
 */
public class MascotaService {

    public static final int LIMITE_BUSQUEDA = 50;
    public static final int MAX_FOTO_BYTES = 2 * 1024 * 1024;
    public static final int MIN_MOTIVO = 10;
    public static final int LIMITE_CAMBIOS = 20;
    static final BigDecimal PESO_MAXIMO = new BigDecimal("150");
    static final int EDAD_MAXIMA_ANIOS = 40;
    static final int LADO_MAXIMO_FOTO = 6000;
    static final Set<String> ESTADOS = ImmutableSet.of(Mascota.ACTIVA, Mascota.INACTIVA, Mascota.FALLECIDA);

    private static final Logger LOG = LoggerFactory.getLogger(MascotaService.class);
    private static final Pattern MICROCHIP = Pattern.compile("^[0-9]{15}$");
    private static final String CLAVE_CACHE = "todas";

    private final MascotaDao dao;
    private final CatalogoMascotaDao catalogo;
    private final ClienteDao clientes;
    private final MascotaFotoAlmacen fotos;
    private final Clock reloj;

    // Guava: especies y razas casi no cambian; se evitan dos consultas por cada formulario (RNF-01)
    private final Cache<String, ImmutableList<Especie>> cacheEspecies =
            CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build();
    private final Cache<String, ImmutableList<Raza>> cacheRazas =
            CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build();

    public MascotaService(MascotaDao dao, CatalogoMascotaDao catalogo, ClienteDao clientes, MascotaFotoAlmacen fotos) {
        this(dao, catalogo, clientes, fotos, Clock.systemDefaultZone());
    }

    public MascotaService(MascotaDao dao, CatalogoMascotaDao catalogo, ClienteDao clientes, MascotaFotoAlmacen fotos,
                          Clock reloj) {
        this.dao = Preconditions.checkNotNull(dao);
        this.catalogo = Preconditions.checkNotNull(catalogo);
        this.clientes = Preconditions.checkNotNull(clientes);
        this.fotos = Preconditions.checkNotNull(fotos);
        this.reloj = Preconditions.checkNotNull(reloj);
    }

    // ------------------------------------------------------------------ consultas

    public List<Mascota> buscar(String texto) throws SQLException {
        return dao.buscar(StringUtils.trimToNull(texto), LIMITE_BUSQUEDA);
    }

    public List<Mascota> delCliente(int idCliente) throws SQLException {
        return dao.listarPorCliente(idCliente);
    }

    public Mascota obtener(int idMascota) throws SQLException {
        return dao.buscarPorId(idMascota).orElseThrow(() -> new ReglaNegocioException("La mascota no existe."));
    }

    public List<CambioPropietario> historialPropietarios(int idMascota) throws SQLException {
        return dao.historialPropietarios(idMascota);
    }

    /** Bitácora de cambios de la ficha (RF-08); se omiten las actualizaciones que no cambiaron ningún dato. */
    public List<MascotaCambio> historialCambios(int idMascota) throws SQLException {
        return dao.historialCambios(idMascota, LIMITE_CAMBIOS).stream()
                .filter(c -> "INSERT".equals(c.operacion()) || !c.campos().isEmpty())
                .toList();
    }

    public List<Especie> especies() throws SQLException {
        return desdeCache(cacheEspecies, () -> ImmutableList.copyOf(catalogo.especies()));
    }

    public List<Raza> razas() throws SQLException {
        return desdeCache(cacheRazas, () -> ImmutableList.copyOf(catalogo.razas()));
    }

    // ------------------------------------------------------------------ RF-07 registro y edición

    public int registrar(Mascota datos, int idUsuario, String usuario) throws SQLException {
        if (datos.idCliente() == null) {
            throw new ReglaNegocioException("Indique el cliente propietario de la mascota.");
        }
        Cliente propietario = clienteActivo(datos.idCliente(),
                "No se puede registrar una mascota a un cliente inactivo (RN-02).");
        Mascota m = normalizarYValidar(datos);
        validarFichaUnica(m);
        int id = dao.insertar(m, idUsuario);
        Auditoria.registrar("MASCOTA_REGISTRAR", "mascota",
                "id=" + id + " cliente=" + propietario.idCliente() + " por=" + usuario);
        return id;
    }

    /** Actualiza datos descriptivos e identificación (microchip). El propietario solo cambia con {@link #cambiarPropietario}. */
    public void actualizar(Mascota datos, int idUsuario, String usuario) throws SQLException {
        Preconditions.checkArgument(datos.idMascota() != null, "Falta el identificador de la mascota.");
        Mascota actual = obtener(datos.idMascota());
        Mascota m = normalizarYValidar(Mascota.datos(actual.idMascota(), actual.idCliente(), datos.idEspecie(),
                datos.idRaza(), datos.nombre(), datos.sexo(), datos.fechaNacimiento(), datos.color(), datos.pesoKg(),
                datos.microchip(), datos.esterilizado(), datos.alertas()));
        validarFichaUnica(m);
        dao.actualizar(m, idUsuario);
        Auditoria.registrar("MASCOTA_ACTUALIZAR", "mascota", "id=" + m.idMascota() + " por=" + usuario);
    }

    /** Limpia y valida los datos del formulario (RF-07). Devuelve la mascota lista para guardar. */
    Mascota normalizarYValidar(Mascota d) throws SQLException {
        String nombre = Validaciones.requerido(d.nombre(), "Nombre");
        nombre = WordUtils.capitalizeFully(StringUtils.normalizeSpace(nombre));
        Validaciones.longitudMaxima(nombre, 50, "Nombre");

        if (d.idEspecie() == null) {
            throw new ReglaNegocioException("Seleccione la especie de la mascota.");
        }
        if (d.idRaza() == null) {
            throw new ReglaNegocioException("Seleccione la raza de la mascota.");
        }
        Raza raza = raza(d.idRaza());
        if (raza.idEspecie() != d.idEspecie()) {
            throw new ReglaNegocioException("La raza «" + raza.nombre() + "» no corresponde a la especie seleccionada.");
        }

        String sexo = StringUtils.upperCase(StringUtils.trimToNull(d.sexo()));
        if (!"M".equals(sexo) && !"H".equals(sexo)) {
            throw new ReglaNegocioException("Seleccione el sexo de la mascota: macho (M) o hembra (H).");
        }

        LocalDate hoy = LocalDate.now(reloj);
        if (d.fechaNacimiento() != null) {
            if (d.fechaNacimiento().isAfter(hoy)) {
                throw new ReglaNegocioException("La fecha de nacimiento no puede ser futura.");
            }
            if (d.fechaNacimiento().isBefore(hoy.minusYears(EDAD_MAXIMA_ANIOS))) {
                throw new ReglaNegocioException("La fecha de nacimiento indica más de " + EDAD_MAXIMA_ANIOS
                        + " años; revise el dato.");
            }
        }

        BigDecimal peso = d.pesoKg();
        if (peso != null) {
            if (peso.signum() <= 0 || peso.compareTo(PESO_MAXIMO) > 0) {
                throw new ReglaNegocioException("El peso referencial debe ser mayor que 0 y como máximo 150 kg.");
            }
            peso = peso.setScale(2, RoundingMode.HALF_UP);
        }

        String microchip = StringUtils.deleteWhitespace(StringUtils.trimToNull(d.microchip()));
        if (microchip != null) {
            if (!MICROCHIP.matcher(microchip).matches()) {
                throw new ReglaNegocioException("El microchip debe tener exactamente 15 dígitos (norma ISO 11784).");
            }
            if (dao.existeMicrochip(microchip, d.idMascota())) {
                throw new ReglaNegocioException("El microchip " + microchip + " ya está registrado en otra mascota.");
            }
        }

        String color = StringUtils.capitalize(StringUtils.normalizeSpace(StringUtils.trimToNull(d.color())));
        Validaciones.longitudMaxima(color, 40, "Color");
        String alertas = StringUtils.normalizeSpace(StringUtils.trimToNull(d.alertas()));
        Validaciones.longitudMaxima(alertas, 300, "Alertas clínicas");

        return new Mascota(d.idMascota(), d.idCliente(), null, raza.idEspecie(), raza.especie(), raza.idRaza(),
                raza.nombre(), nombre, sexo, d.fechaNacimiento(), color, peso, microchip, d.esterilizado(), alertas,
                null, d.estado(), null);
    }

    /** Ficha única (RF-07): el mismo cliente no puede tener dos mascotas vigentes con igual nombre y especie. */
    private void validarFichaUnica(Mascota m) throws SQLException {
        boolean duplicada = dao.listarPorCliente(m.idCliente()).stream()
                .anyMatch(o -> !o.fallecida() && !o.idMascota().equals(m.idMascota())
                        && o.idEspecie().equals(m.idEspecie()) && o.nombre().equalsIgnoreCase(m.nombre()));
        if (duplicada) {
            throw new ReglaNegocioException("El cliente ya tiene registrada una mascota «" + m.nombre() + "» de la especie "
                    + m.especie() + ". Cada mascota debe tener una sola ficha.");
        }
    }

    // ------------------------------------------------------------------ RF-08 estado

    public void cambiarEstado(int idMascota, String nuevoEstado, int idUsuario, String usuario) throws SQLException {
        String estado = StringUtils.upperCase(StringUtils.trimToNull(nuevoEstado));
        if (!ESTADOS.contains(estado)) {
            throw new ReglaNegocioException("Seleccione un estado válido: activa, inactiva o fallecida.");
        }
        Mascota m = obtener(idMascota);
        if (m.fallecida()) {
            throw new ReglaNegocioException("La mascota está registrada como fallecida; ese estado es definitivo "
                    + "y no puede volver a activa ni a inactiva.");
        }
        if (estado.equals(m.estado())) {
            throw new ReglaNegocioException("La mascota ya se encuentra en ese estado.");
        }
        if (Mascota.ACTIVA.equals(estado)) {
            clienteActivo(m.idCliente(), "No se puede activar la mascota porque su propietario está inactivo (RN-02). "
                    + "Reactive al cliente o registre el cambio de propietario.");
        }
        dao.cambiarEstado(idMascota, estado, idUsuario);
        Auditoria.registrar("MASCOTA_ESTADO", "mascota",
                "id=" + idMascota + " de=" + m.estado() + " a=" + estado + " por=" + usuario);
    }

    // ------------------------------------------------------------------ RF-08 cambio de propietario

    public void cambiarPropietario(int idMascota, Integer idClienteNuevo, String motivo, int idUsuario, String usuario)
            throws SQLException {
        Mascota m = obtener(idMascota);
        if (m.fallecida()) {
            throw new ReglaNegocioException("No se puede transferir una mascota registrada como fallecida.");
        }
        if (idClienteNuevo == null) {
            throw new ReglaNegocioException("Seleccione al nuevo propietario.");
        }
        if (idClienteNuevo.equals(m.idCliente())) {
            throw new ReglaNegocioException("El nuevo propietario debe ser distinto del propietario actual.");
        }
        clienteActivo(idClienteNuevo, "El nuevo propietario debe ser un cliente activo (RN-02).");
        String texto = StringUtils.normalizeSpace(StringUtils.trimToNull(motivo));
        if (texto == null || texto.length() < MIN_MOTIVO) {
            throw new ReglaNegocioException("Indique el motivo del cambio de propietario (mínimo " + MIN_MOTIVO
                    + " caracteres).");
        }
        Validaciones.longitudMaxima(texto, 200, "Motivo");
        if (!dao.cambiarPropietario(idMascota, m.idCliente(), idClienteNuevo, texto, idUsuario)) {
            throw new ReglaNegocioException("La mascota cambió de propietario mientras se registraba la operación. "
                    + "Revise la ficha e intente nuevamente.");
        }
        Auditoria.registrar("MASCOTA_CAMBIO_PROPIETARIO", "mascota",
                "id=" + idMascota + " de=" + m.idCliente() + " a=" + idClienteNuevo + " por=" + usuario);
    }

    // ------------------------------------------------------------------ RF-08 fotografía

    /**
     * Valida y guarda la foto (JPG o PNG, máximo 2 MB). El archivo anterior se elimina solo después de
     * actualizar la BD; si la BD falla, se borra el archivo nuevo para no dejar huérfanos.
     */
    public void actualizarFoto(int idMascota, byte[] contenido, String nombreArchivo, int idUsuario, String usuario)
            throws SQLException {
        Mascota m = obtener(idMascota);
        String extension = validarFoto(contenido, nombreArchivo);
        String ruta;
        try {
            ruta = fotos.guardar(idMascota, contenido, extension);
        } catch (IOException e) {
            LOG.error("No se pudo guardar la foto de la mascota {}: {}", idMascota, e.getMessage(), e);
            throw new ReglaNegocioException("No se pudo guardar la fotografía en el servidor. Intente nuevamente.");
        }
        try {
            dao.actualizarFoto(idMascota, ruta, idUsuario);
        } catch (SQLException | RuntimeException e) {
            fotos.eliminar(ruta);
            throw e;
        }
        if (m.tieneFoto()) {
            fotos.eliminar(m.fotoRuta());
        }
        Auditoria.registrar("MASCOTA_FOTO", "mascota", "id=" + idMascota + " por=" + usuario);
    }

    /**
     * Acepta solo JPG o PNG reales: la extensión debe coincidir con la firma del contenido ("magic bytes") y la
     * imagen debe poder leerse con dimensiones razonables (sin decodificarla completa: evita "bombas" de píxeles).
     *
     * @return extensión normalizada: "jpg" o "png"
     */
    String validarFoto(byte[] contenido, String nombreArchivo) {
        if (contenido == null || contenido.length == 0) {
            throw new ReglaNegocioException("Seleccione una fotografía en formato JPG o PNG.");
        }
        if (contenido.length > MAX_FOTO_BYTES) {
            throw new ReglaNegocioException("La fotografía supera el tamaño máximo de 2 MB.");
        }
        String extension = StringUtils.lowerCase(StringUtils.substringAfterLast(StringUtils.defaultString(nombreArchivo), "."));
        if ("jpeg".equals(extension)) {
            extension = "jpg";
        }
        if (!"jpg".equals(extension) && !"png".equals(extension)) {
            throw new ReglaNegocioException("Solo se aceptan fotografías JPG o PNG.");
        }
        if (!extension.equals(tipoPorFirma(contenido))) {
            throw new ReglaNegocioException("El archivo no es una imagen " + extension.toUpperCase()
                    + " válida: su contenido no coincide con la extensión.");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            Iterator<ImageReader> lectores = in == null ? null : ImageIO.getImageReaders(in);
            if (lectores == null || !lectores.hasNext()) {
                throw new ReglaNegocioException("La imagen está dañada o no se puede leer.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(in, true, true);
                if (lector.getWidth(0) > LADO_MAXIMO_FOTO || lector.getHeight(0) > LADO_MAXIMO_FOTO) {
                    throw new ReglaNegocioException("La imagen supera " + LADO_MAXIMO_FOTO + " × " + LADO_MAXIMO_FOTO
                            + " píxeles; redúzcala antes de subirla.");
                }
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw new ReglaNegocioException("La imagen está dañada o no se puede leer.");
        }
        return extension;
    }

    /** Tipo real según los primeros bytes: JPEG FF D8 FF, PNG 89 50 4E 47 0D 0A 1A 0A. */
    static String tipoPorFirma(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        return null;
    }

    // ------------------------------------------------------------------ apoyo

    private Cliente clienteActivo(int idCliente, String mensajeInactivo) throws SQLException {
        Cliente c = clientes.buscarPorId(idCliente)
                .orElseThrow(() -> new ReglaNegocioException("El cliente seleccionado no existe."));
        if (!c.activo()) {
            throw new ReglaNegocioException(mensajeInactivo);
        }
        return c;
    }

    /** Raza del catálogo en caché; si no está (raza recién creada), recarga una vez. */
    private Raza raza(int idRaza) throws SQLException {
        for (int intento = 0; intento < 2; intento++) {
            for (Raza r : razas()) {
                if (r.idRaza() == idRaza) {
                    return r;
                }
            }
            cacheRazas.invalidateAll();
        }
        throw new ReglaNegocioException("La raza seleccionada no existe.");
    }

    private static <T> T desdeCache(Cache<String, T> cache, Callable<T> carga) throws SQLException {
        try {
            return cache.get(CLAVE_CACHE, carga);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof SQLException sql) {
                throw sql;
            }
            throw new IllegalStateException("No se pudo cargar el catálogo de mascotas", e.getCause());
        } catch (UncheckedExecutionException e) {
            throw e.getCause() instanceof RuntimeException r ? r : e;
        }
    }
}
