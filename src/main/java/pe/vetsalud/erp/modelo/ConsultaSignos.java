package pe.vetsalud.erp.modelo;

import java.math.BigDecimal;

/** Signos vitales de la consulta (RF-14): temperatura (°C), frecuencia cardiaca (lpm), respiratoria (rpm) y peso (kg). */
public record ConsultaSignos(BigDecimal temperaturaC, Integer frecCardiaca, Integer frecRespiratoria, BigDecimal pesoKg) {
}
