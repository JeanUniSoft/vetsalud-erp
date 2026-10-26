package pe.vetsalud.erp.modelo;

/**
 * Qué puede hacer el usuario actual sobre una consulta (lo calcula {@code ConsultaService}; la vista solo lo lee).
 * El técnico registra signos y procedimientos; el veterinario responsable además diagnostica, receta y firma.
 */
public record ConsultaPermisos(boolean datos, boolean signos, boolean diagnosticar, boolean procedimientos,
                               boolean recetar, boolean firmar, boolean adenda) {

    public boolean algunaEdicion() {
        return datos || signos || diagnosticar || procedimientos || recetar;
    }
}
