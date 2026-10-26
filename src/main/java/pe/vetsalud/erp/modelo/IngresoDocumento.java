package pe.vetsalud.erp.modelo;

import java.time.LocalDate;
import java.util.List;

import com.google.common.collect.ImmutableList;

/** Documento de compra (FACTURA, BOLETA o GUIA) con una o más líneas por lote (RF-21, UC-09). */
public record IngresoDocumento(Integer idProveedor, String tipoDoc, String numDoc, LocalDate fecha,
                               List<LineaIngreso> lineas) {

    public IngresoDocumento {
        lineas = lineas == null ? ImmutableList.of() : ImmutableList.copyOf(lineas);
    }
}
