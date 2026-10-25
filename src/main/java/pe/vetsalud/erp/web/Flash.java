package pe.vetsalud.erp.web;

import java.io.Serializable;

/** Mensaje que sobrevive a una redirección (patrón Post/Redirect/Get). tipo: exito | error | aviso. */
public record Flash(String tipo, String mensaje) implements Serializable {
}
