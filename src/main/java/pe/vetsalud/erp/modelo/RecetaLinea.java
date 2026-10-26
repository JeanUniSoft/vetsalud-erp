package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/** Medicamento de una receta (RF-17): producto, dosis, frecuencia, duración, vía y cantidad a dispensar. */
public record RecetaLinea(long idRecetaDet, long idReceta, int idProducto, String producto, String unidad,
                          String dosis, String frecuencia, int duracionDias, String via, BigDecimal cantidad) {

    /** Resumen de una línea para la historia clínica. */
    public String resumen() {
        return producto + ": " + dosis + ", " + frecuencia.toLowerCase() + ", " + duracionDias
                + (duracionDias == 1 ? " día" : " días") + ", vía " + via.toLowerCase();
    }
}
