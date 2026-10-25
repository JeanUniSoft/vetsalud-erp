package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Punto de una serie diaria; altura = porcentaje (0-100) respecto del máximo, para dibujar la barra. */
public record PuntoSerie(LocalDate dia, BigDecimal total, int altura) {
}
