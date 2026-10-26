package pe.vetsalud.erp.fakes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.ProductoDao;
import pe.vetsalud.erp.modelo.Lote;
import pe.vetsalud.erp.modelo.Producto;

/** Doble de ProductoDao: productos y lotes en memoria; el stock calculado se deriva de los lotes. */
public class ProductoDaoEnMemoria implements ProductoDao {

    public final Map<Integer, Producto> productos = new LinkedHashMap<>();
    public final Map<Integer, Lote> lotes = new LinkedHashMap<>();
    private int secuencia = 100;

    public Producto agregarProducto(int id, String categoria, String codigo, String nombre, String unidad,
                                    String precio, boolean receta) {
        Producto p = new Producto(id, 1, categoria, codigo, nombre, unidad, new BigDecimal(precio), BigDecimal.ONE,
                receta, true, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        productos.put(id, p);
        return p;
    }

    public Lote agregarLote(int idLote, int idProducto, String codigo, LocalDate vence, String stock) {
        Lote l = new Lote(idLote, idProducto, codigo, vence, new BigDecimal("10.00"), new BigDecimal(stock), "VIGENTE");
        lotes.put(idLote, l);
        return l;
    }

    private Producto conStock(Producto p) {
        LocalDate hoy = LocalDate.now();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal disponible = BigDecimal.ZERO;
        for (Lote l : lotes.values()) {
            if (l.idProducto() == p.idProducto()) {
                total = total.add(l.stockActual());
                if (l.vigenteEn(hoy)) {
                    disponible = disponible.add(l.stockActual());
                }
            }
        }
        return new Producto(p.idProducto(), p.idCategoria(), p.categoria(), p.codigo(), p.nombre(), p.unidad(),
                p.precioVenta(), p.stockMinimo(), p.requiereReceta(), p.activo(), disponible, total, 0, 0);
    }

    @Override
    public List<Producto> buscar(String texto, Integer idCategoria, boolean soloAlertas, boolean incluirInactivos,
                                 int diasPorVencer, int limite) {
        return productos.values().stream().map(this::conStock)
                .filter(p -> incluirInactivos || p.activo())
                .filter(p -> texto == null || p.nombre().toLowerCase().contains(texto.toLowerCase())
                        || p.codigo().equalsIgnoreCase(texto))
                .filter(p -> idCategoria == null || idCategoria.equals(p.idCategoria()))
                .filter(p -> !soloAlertas || p.conAlerta())
                .limit(limite).toList();
    }

    @Override
    public Optional<Producto> buscarPorId(int idProducto, int diasPorVencer) {
        return Optional.ofNullable(productos.get(idProducto)).map(this::conStock);
    }

    @Override
    public boolean existeCodigo(String codigo, Integer exceptoId) {
        return productos.values().stream().anyMatch(p -> p.codigo().equalsIgnoreCase(codigo)
                && (exceptoId == null || !exceptoId.equals(p.idProducto())));
    }

    @Override
    public int insertar(Producto producto, int idUsuario) {
        int id = secuencia++;
        productos.put(id, producto.conId(id));
        return id;
    }

    @Override
    public void actualizar(Producto producto, int idUsuario) {
        productos.put(producto.idProducto(), producto);
    }

    @Override
    public List<Lote> lotes(int idProducto, int diasPorVencer) {
        return new ArrayList<>(lotes.values().stream().filter(l -> l.idProducto() == idProducto).toList());
    }

    @Override
    public Optional<Lote> lotePorId(int idLote, int diasPorVencer) {
        return Optional.ofNullable(lotes.get(idLote));
    }

    /** Misma regla que fn_lote_sugerido: lote vigente que cubre la cantidad, el que vence primero. */
    @Override
    public Optional<Integer> loteSugerido(int idProducto, BigDecimal cantidad) {
        LocalDate hoy = LocalDate.now();
        return lotes.values().stream()
                .filter(l -> l.idProducto() == idProducto && l.stockActual().compareTo(cantidad) >= 0 && l.vigenteEn(hoy))
                .sorted((a, b) -> a.fechaVencimiento() == null ? 1 : b.fechaVencimiento() == null ? -1
                        : a.fechaVencimiento().compareTo(b.fechaVencimiento()))
                .map(Lote::idLote).findFirst();
    }
}
