package pe.vetsalud.erp.servicio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.config.ConexionBD;
import pe.vetsalud.erp.dao.InventarioDAO;
import pe.vetsalud.erp.dao.VentaDAO;
import pe.vetsalud.erp.log.Auditoria;
import pe.vetsalud.erp.modelo.CondicionLote;
import pe.vetsalud.erp.modelo.FiltroReporte;
import pe.vetsalud.erp.modelo.LoteStock;
import pe.vetsalud.erp.modelo.VentaDiaria;
import pe.vetsalud.erp.reporte.ReporteExcelInventario;

/** Capa de negocio del módulo de reportes (RF-33, RF-34, RF-36). */
public class ReporteService {

    private static final Logger LOG = LoggerFactory.getLogger(ReporteService.class);
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ConexionBD conexion;
    private final InventarioDAO inventarioDAO = new InventarioDAO();
    private final VentaDAO ventaDAO = new VentaDAO();
    private final ReporteExcelInventario excel = new ReporteExcelInventario();
    private final ZoneId zona;

    public ReporteService(ConexionBD conexion, ZoneId zona) {
        this.conexion = conexion;
        this.zona = zona;
    }

    /** Genera el reporte en una carpeta y devuelve la ruta del archivo .xlsx. */
    public Path generarArchivo(FiltroReporte filtro, String usuario, Path carpeta) {
        ZonedDateTime emision = ZonedDateTime.now(zona);
        Path archivo = carpeta.resolve("reporte_inventario_" + SELLO.format(emision) + ".xlsx");
        Path temporal = null;
        try {
            Files.createDirectories(carpeta);
            // Se escribe en un temporal y se renombra al final: nunca queda un .xlsx incompleto
            temporal = Files.createTempFile(carpeta, "reporte_", ".tmp");
            try (OutputStream out = Files.newOutputStream(temporal)) {
                escribir(out, filtro, usuario, emision);
            }
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Archivo generado: {} ({} KB)", archivo.getFileName(), Files.size(archivo) / 1024);
            return archivo;
        } catch (IOException e) {
            LOG.error("No se pudo escribir el archivo {}", archivo, e);
            throw new ReporteException("No se pudo guardar el reporte", e);
        } finally {
            borrarTemporal(temporal);
        }
    }

    private static void borrarTemporal(Path temporal) {
        if (temporal != null) {
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException e) {
                LOG.warn("No se pudo borrar el temporal {}", temporal, e);
            }
        }
    }

    /** Escribe el reporte en cualquier flujo (archivo o respuesta HTTP). */
    public void escribir(OutputStream salida, FiltroReporte filtro, String usuario, ZonedDateTime emision)
            throws IOException {
        long inicio = System.nanoTime();
        LOG.info("Generando reporte de inventario y ventas con {}", filtro);
        List<LoteStock> lotes;
        List<VentaDiaria> ventas;
        try (Connection cn = conexion.abrir()) {
            lotes = inventarioDAO.listarStockPorLote(cn);
            ventas = ventaDAO.listarVentasDiarias(cn, filtro.desde(), filtro.hasta());
        } catch (SQLException e) {
            // RNF-16: se registra el componente, el código SQL y la traza, sin datos sensibles
            LOG.error("Fallo al consultar la base de datos (SQLState {})", e.getSQLState(), e);
            Auditoria.registrar("EXPORTAR_REPORTE_FALLIDO", "reporte_inventario", "motivo=" + e.getSQLState());
            throw new ReporteException("El reporte no está disponible en este momento. Intente más tarde.", e);
        }

        alertarVencidos(lotes, emision);
        excel.escribir(salida, lotes, ventas, filtro, usuario, emision);

        long ms = (System.nanoTime() - inicio) / 1_000_000;
        LOG.info("Reporte listo: {} lotes, {} filas de ventas en {} ms", lotes.size(), ventas.size(), ms);
        if (ms > 3000) {
            LOG.warn("El reporte superó el tiempo objetivo de 3 s (RNF-01): {} ms", ms);
        }
        Auditoria.registrar("EXPORTAR_REPORTE", "reporte_inventario",
                "formato=xlsx lotes=" + lotes.size() + " ventas=" + ventas.size()
                        + " periodo=" + filtro.desde() + ".." + filtro.hasta());
    }

    private void alertarVencidos(List<LoteStock> lotes, ZonedDateTime emision) {
        List<String> vencidos = lotes.stream()
                .filter(l -> l.condicion(emision.toLocalDate(), 30) == CondicionLote.VENCIDO)
                .map(l -> l.codigo() + "/" + l.codigoLote())
                .collect(Collectors.toList());
        if (!vencidos.isEmpty()) {
            LOG.warn("{} lote(s) vencido(s) con stock; deben retirarse de la venta (RN-06): {}",
                    vencidos.size(), vencidos);
        }
    }
}
