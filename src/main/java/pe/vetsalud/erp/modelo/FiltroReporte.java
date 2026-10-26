package pe.vetsalud.erp.modelo;

import java.time.LocalDate;

/** Filtros que el usuario elige en la pantalla de reportes; se imprimen en el Excel (RF-36). */
public record FiltroReporte(LocalDate desde, LocalDate hasta, int diasAlerta) {

    public FiltroReporte {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("El periodo del reporte es obligatorio");
        }
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException(
                    "Periodo inválido: la fecha inicial " + desde + " es posterior a la final " + hasta);
        }
        if (diasAlerta < 1 || diasAlerta > 365) {
            throw new IllegalArgumentException("Los días de alerta deben estar entre 1 y 365: " + diasAlerta);
        }
    }
}
