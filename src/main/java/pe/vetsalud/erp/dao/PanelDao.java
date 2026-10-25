package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import pe.vetsalud.erp.modelo.AlertaInventario;
import pe.vetsalud.erp.modelo.CitaPanel;
import pe.vetsalud.erp.modelo.PuntoSerie;
import pe.vetsalud.erp.modelo.ResumenPanel;

/** Consultas agregadas del panel principal (P02, RF-32). */
public interface PanelDao {

    ResumenPanel resumen(LocalDate dia) throws SQLException;

    List<CitaPanel> citasDelDia(LocalDate dia, Integer idVeterinario) throws SQLException;

    /** Totales de ventas confirmadas por día (sin altura calculada). */
    List<PuntoSerie> ventasPorDia(LocalDate desde, LocalDate hasta) throws SQLException;

    List<AlertaInventario> alertasInventario() throws SQLException;
}
