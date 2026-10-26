package pe.vetsalud.erp.fakes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.ConsultaDao;
import pe.vetsalud.erp.modelo.Adenda;
import pe.vetsalud.erp.modelo.Consulta;
import pe.vetsalud.erp.modelo.ConsultaDatos;
import pe.vetsalud.erp.modelo.ConsultaFirma;
import pe.vetsalud.erp.modelo.ConsultaSignos;
import pe.vetsalud.erp.modelo.Diagnostico;
import pe.vetsalud.erp.modelo.ProcedimientoConsulta;
import pe.vetsalud.erp.modelo.ProductoReceta;
import pe.vetsalud.erp.modelo.Receta;
import pe.vetsalud.erp.modelo.RecetaLinea;

/** Doble en memoria de {@link ConsultaDao}; registra las firmas para verificar recordatorio y cita atendida. */
public class ConsultaDaoEnMemoria implements ConsultaDao {

    public static final int AMOXICILINA = 1;
    public static final int COLLAR = 5;
    public static final int RETIRADO = 99;

    /** Momento en que se «abren» las consultas nuevas. */
    public LocalDateTime ahora = LocalDateTime.of(2026, 10, 25, 10, 0);

    public final Map<Long, Consulta> consultas = new LinkedHashMap<>();
    public final List<Diagnostico> diagnosticos = new ArrayList<>();
    public final List<ProcedimientoConsulta> procedimientos = new ArrayList<>();
    public final Map<Long, Receta> recetas = new LinkedHashMap<>();
    public final List<Adenda> adendas = new ArrayList<>();
    public final List<ConsultaFirma> firmas = new ArrayList<>();
    public final Map<Integer, ProductoReceta> productos = new LinkedHashMap<>();
    private long secuencia = 1;

    public ConsultaDaoEnMemoria() {
        productos.put(AMOXICILINA, new ProductoReceta(AMOXICILINA, "MED-001", "Amoxicilina 250 mg x 10 tab", "CAJA", true, true));
        productos.put(COLLAR, new ProductoReceta(COLLAR, "ACC-001", "Collar isabelino talla M", "UND", false, true));
        productos.put(RETIRADO, new ProductoReceta(RETIRADO, "MED-099", "Producto retirado", "UND", false, false));
    }

    @Override
    public Optional<Consulta> buscarPorId(long idConsulta) {
        return Optional.ofNullable(consultas.get(idConsulta));
    }

    @Override
    public Optional<Consulta> buscarPorCita(long idCita) {
        return consultas.values().stream().filter(c -> c.idCita() != null && c.idCita() == idCita).findFirst();
    }

    @Override
    public long insertar(int idMascota, Long idCita, int idVeterinario, String motivo, int idUsuario) {
        long id = secuencia++;
        consultas.put(id, new Consulta(id, idMascota, idCita, idVeterinario, "Vet " + idVeterinario, "00000", ahora,
                motivo, null, null, null, null, null, null, null, null, Consulta.ABIERTA, null));
        return id;
    }

    @Override
    public boolean actualizarDatos(long idConsulta, ConsultaDatos datos, int idUsuario) {
        Consulta c = consultas.get(idConsulta);
        if (c == null || !c.abierta()) {
            return false;
        }
        consultas.put(idConsulta, c.conDatos(datos));
        return true;
    }

    @Override
    public boolean actualizarSignos(long idConsulta, ConsultaSignos signos, int idUsuario) {
        Consulta c = consultas.get(idConsulta);
        if (c == null || !c.abierta()) {
            return false;
        }
        consultas.put(idConsulta, c.conSignos(signos));
        return true;
    }

    @Override
    public List<Diagnostico> diagnosticos(long idConsulta) {
        return diagnosticos.stream().filter(d -> d.idConsulta() == idConsulta).toList();
    }

    @Override
    public long insertarDiagnostico(long idConsulta, String descripcion, boolean principal, int idUsuario) {
        long id = secuencia++;
        diagnosticos.add(new Diagnostico(id, idConsulta, descripcion, principal));
        return id;
    }

    @Override
    public List<ProcedimientoConsulta> procedimientos(long idConsulta) {
        return procedimientos.stream().filter(p -> p.idConsulta() == idConsulta).toList();
    }

    @Override
    public long insertarProcedimiento(ProcedimientoConsulta p, int idUsuario) {
        long id = secuencia++;
        procedimientos.add(new ProcedimientoConsulta(id, p.idConsulta(), p.idServicio(), p.servicio(), p.idResponsable(),
                p.responsable(), p.cantidad(), p.precioUnitario(), p.resultado()));
        return id;
    }

    @Override
    public Optional<Receta> receta(long idConsulta) {
        return Optional.ofNullable(recetas.get(idConsulta));
    }

    @Override
    public long insertarReceta(long idConsulta, String indicaciones, int idUsuario) {
        long id = secuencia++;
        recetas.put(idConsulta, new Receta(id, idConsulta, ahora, indicaciones, List.of()));
        return id;
    }

    @Override
    public void actualizarReceta(long idReceta, String indicaciones, int idUsuario) {
        recetas.replaceAll((k, r) -> r.idReceta() == idReceta
                ? new Receta(r.idReceta(), r.idConsulta(), r.fecha(), indicaciones, r.lineas()) : r);
    }

    @Override
    public long insertarLineaReceta(long idConsulta, RecetaLinea l, int idUsuario) {
        if (!recetas.containsKey(idConsulta)) {
            insertarReceta(idConsulta, null, idUsuario);
        }
        Receta r = recetas.get(idConsulta);
        long id = secuencia++;
        List<RecetaLinea> lineas = new ArrayList<>(r.lineas());
        lineas.add(new RecetaLinea(id, r.idReceta(), l.idProducto(), l.producto(), l.unidad(), l.dosis(), l.frecuencia(),
                l.duracionDias(), l.via(), l.cantidad()));
        recetas.put(idConsulta, new Receta(r.idReceta(), idConsulta, r.fecha(), r.indicaciones(), lineas));
        return id;
    }

    @Override
    public List<ProductoReceta> productosActivos() {
        return productos.values().stream().filter(ProductoReceta::activo).toList();
    }

    @Override
    public Optional<ProductoReceta> producto(int idProducto) {
        return Optional.ofNullable(productos.get(idProducto));
    }

    @Override
    public List<Adenda> adendas(long idConsulta) {
        return adendas.stream().filter(a -> a.idConsulta() == idConsulta).toList();
    }

    @Override
    public long insertarAdenda(long idConsulta, String texto, int idUsuario) {
        long id = secuencia++;
        adendas.add(new Adenda(id, idConsulta, idUsuario, "Usuario " + idUsuario, texto, ahora.plusHours(2)));
        return id;
    }

    @Override
    public boolean firmar(ConsultaFirma f, int idUsuario) {
        Consulta c = consultas.get(f.idConsulta());
        if (c == null || !c.abierta()) {
            return false;
        }
        consultas.put(f.idConsulta(), c.firmadaEl(ahora.plusHours(1)));
        firmas.add(f);
        return true;
    }
}
