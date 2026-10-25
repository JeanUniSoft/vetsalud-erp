package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

public record Parametro(String clave, String valor, String descripcion, LocalDateTime actualizadoEn) {
}
