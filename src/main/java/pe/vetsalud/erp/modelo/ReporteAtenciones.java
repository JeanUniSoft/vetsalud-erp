package pe.vetsalud.erp.modelo;

/** Atenciones clínicas (consultas) de un grupo (RF-35). */
public record ReporteAtenciones(String grupo, long consultas, long firmadas, long abiertas, long mascotas) {
}
