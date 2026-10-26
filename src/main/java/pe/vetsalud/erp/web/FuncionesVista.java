package pe.vetsalud.erp.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;

import com.google.common.collect.ImmutableMap;

/**
 * Funciones EL de las vistas (prefijo vs:). RNF-17: español, soles (PEN), fechas dd/mm/aaaa.
 * RNF-15: enmascarado de documentos en listados no clínicos.
 */
public final class FuncionesVista {

    private static final Locale PERU = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter INPUT_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private static final Map<String, String> ETIQUETAS = ImmutableMap.<String, String>builder()
            .put("PENDIENTE", "Pendiente").put("CONFIRMADA", "Confirmada").put("EN_ESPERA", "En espera")
            .put("EN_ATENCION", "En atención").put("ATENDIDA", "Atendida").put("CANCELADA", "Cancelada")
            .put("NO_ASISTIO", "No asistió").put("ABIERTA", "Abierta").put("FIRMADA", "Firmada")
            .put("CERRADA", "Cerrada").put("ANULADA", "Anulada").put("ACTIVA", "Activa").put("INACTIVA", "Inactiva")
            .put("FALLECIDA", "Fallecida").put("YAPE_PLIN", "Yape / Plin").put("EFECTIVO", "Efectivo")
            .put("TARJETA", "Tarjeta").put("TRANSFERENCIA", "Transferencia").put("STOCK_MINIMO", "Stock mínimo")
            .put("POR_VENCER", "Por vencer").put("VENCIDO", "Vencido").put("INGRESO", "Ingreso").put("VENTA", "Venta")
            .put("AJUSTE_ENTRADA", "Ajuste (+)").put("AJUSTE_SALIDA", "Ajuste (−)").put("DEVOLUCION", "Devolución")
            .put("ENVIADO", "Enviado").put("FALLIDO", "Fallido").put("CANCELADO", "Cancelado").put("RECEPCION", "Recepción")
            .put("VETERINARIO", "Veterinario").put("ADMINISTRADOR", "Administrador").put("TECNICO", "Técnico")
            .put("FARMACIA", "Farmacia").put("CAJERO", "Caja").put("CLIENTE", "Cliente").put("CLINICA", "Clínica")
            .build();

    private FuncionesVista() {
    }

    public static String fecha(Object valor) {
        if (valor instanceof LocalDate d) {
            return d.format(FECHA);
        }
        if (valor instanceof LocalDateTime dt) {
            return dt.format(FECHA);
        }
        return "";
    }

    public static String fechaHora(LocalDateTime valor) {
        return valor == null ? "" : valor.format(FECHA_HORA);
    }

    public static String hora(Object valor) {
        if (valor instanceof LocalDateTime dt) {
            return dt.format(HORA);
        }
        if (valor instanceof LocalTime t) {
            return t.format(HORA);
        }
        return "";
    }

    /** Valor para un input type=datetime-local. */
    public static String inputFechaHora(LocalDateTime valor) {
        return valor == null ? "" : valor.format(INPUT_FECHA_HORA);
    }

    public static String soles(BigDecimal monto) {
        NumberFormat f = NumberFormat.getNumberInstance(PERU);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "S/ " + f.format(monto == null ? BigDecimal.ZERO : monto.setScale(2, RoundingMode.HALF_UP));
    }

    public static String numero(BigDecimal valor) {
        if (valor == null) {
            return "0";
        }
        return valor.stripTrailingZeros().toPlainString();
    }

    /** Muestra solo los 3 últimos dígitos del documento: ****5678 (RNF-15). */
    public static String enmascarar(String documento) {
        if (StringUtils.length(documento) <= 3) {
            return StringUtils.defaultString(documento);
        }
        return StringUtils.repeat('•', documento.length() - 3) + StringUtils.right(documento, 3);
    }

    public static String etiqueta(String codigo) {
        if (codigo == null) {
            return "";
        }
        return ETIQUETAS.getOrDefault(codigo, WordUtils.capitalizeFully(codigo.replace('_', ' ')));
    }

    /** Clase CSS del distintivo según el estado. */
    public static String claseEstado(String codigo) {
        if (codigo == null) {
            return "badge";
        }
        return switch (codigo) {
            case "ATENDIDA", "FIRMADA", "CONFIRMADA", "ACTIVA", "ENVIADO", "CERRADA" -> "badge badge-ok";
            case "EN_ATENCION", "EN_ESPERA", "ABIERTA", "POR_VENCER", "STOCK_MINIMO" -> "badge badge-aviso";
            case "CANCELADA", "NO_ASISTIO", "ANULADA", "VENCIDO", "FALLIDO", "FALLECIDA", "INACTIVA" -> "badge badge-error";
            default -> "badge badge-neutro";
        };
    }

    /** Edad legible a partir de la fecha de nacimiento. */
    public static String edad(LocalDate nacimiento) {
        if (nacimiento == null) {
            return "—";
        }
        Period p = Period.between(nacimiento, LocalDate.now());
        if (p.getYears() > 0) {
            return p.getYears() + (p.getYears() == 1 ? " año" : " años");
        }
        return p.getMonths() + (p.getMonths() == 1 ? " mes" : " meses");
    }

    public static boolean contiene(java.util.Collection<?> coleccion, Object valor) {
        return coleccion != null && coleccion.contains(valor);
    }
}
