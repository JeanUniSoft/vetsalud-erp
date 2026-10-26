package pe.vetsalud.erp.modelo;

import java.time.LocalDateTime;

/** Adenda de una consulta firmada (RN-05): agrega información sin modificar lo firmado; registra autor y fecha. */
public record Adenda(long idAdenda, long idConsulta, int idUsuario, String autor, String texto, LocalDateTime fechaHora) {
}
