package pe.vetsalud.erp.modelo;

/** Personal clínico que atiende citas o procedimientos (RF-09, RF-16). La colegiatura CMVP solo la tienen los veterinarios. */
public record Profesional(int idUsuario, String nombres, String apellidos, String rol, String colegiatura,
                          boolean activo) {

    public static final String VETERINARIO = "VETERINARIO";

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }

    public boolean esVeterinario() {
        return VETERINARIO.equals(rol);
    }

    /** Texto para listas: «M.V. Carlos Mendoza Ríos» o «Jorge Huamán Cruz (técnico)». */
    public String etiqueta() {
        return esVeterinario() ? "M.V. " + nombreCompleto() : nombreCompleto() + " (técnico)";
    }
}
