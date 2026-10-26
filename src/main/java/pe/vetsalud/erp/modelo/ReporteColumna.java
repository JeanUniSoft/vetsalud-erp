package pe.vetsalud.erp.modelo;

/** Columna de un reporte: título visible y tipo de dato (define el formato en pantalla, CSV y Excel). */
public record ReporteColumna(String titulo, Tipo tipo) {

    /** Tipos de dato con formato propio (RNF-17: soles, dd/mm/aaaa). */
    public enum Tipo { TEXTO, ENTERO, DECIMAL, MONEDA, PORCENTAJE, FECHA, FECHA_HORA }

    public static ReporteColumna texto(String titulo) {
        return new ReporteColumna(titulo, Tipo.TEXTO);
    }

    public static ReporteColumna entero(String titulo) {
        return new ReporteColumna(titulo, Tipo.ENTERO);
    }

    public static ReporteColumna decimal(String titulo) {
        return new ReporteColumna(titulo, Tipo.DECIMAL);
    }

    public static ReporteColumna moneda(String titulo) {
        return new ReporteColumna(titulo, Tipo.MONEDA);
    }

    public static ReporteColumna porcentaje(String titulo) {
        return new ReporteColumna(titulo, Tipo.PORCENTAJE);
    }

    public static ReporteColumna fecha(String titulo) {
        return new ReporteColumna(titulo, Tipo.FECHA);
    }

    public static ReporteColumna fechaHora(String titulo) {
        return new ReporteColumna(titulo, Tipo.FECHA_HORA);
    }

    /** ¿Se alinea a la derecha y se puede sumar o graficar? */
    public boolean numerica() {
        return tipo == Tipo.ENTERO || tipo == Tipo.DECIMAL || tipo == Tipo.MONEDA || tipo == Tipo.PORCENTAJE;
    }
}
