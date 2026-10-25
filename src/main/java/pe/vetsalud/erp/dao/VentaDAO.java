package pe.vetsalud.erp.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.vetsalud.erp.modelo.VentaDiaria;

/** Ventas confirmadas por día y medio de pago sobre la vista v_ventas_diarias. */
public class VentaDAO {

    private static final Logger LOG = LoggerFactory.getLogger(VentaDAO.class);

    private static final String SQL_VENTAS = """
            SELECT dia, medio::text AS medio, ventas, total
            FROM vetsalud.v_ventas_diarias
            WHERE dia BETWEEN ? AND ?
            ORDER BY dia, medio""";

    public List<VentaDiaria> listarVentasDiarias(Connection cn, LocalDate desde, LocalDate hasta)
            throws SQLException {
        List<VentaDiaria> filas = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(SQL_VENTAS)) {
            ps.setObject(1, desde);
            ps.setObject(2, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filas.add(new VentaDiaria(rs.getObject("dia", LocalDate.class), rs.getString("medio"),
                            rs.getInt("ventas"), rs.getBigDecimal("total")));
                }
            }
        }
        LOG.debug("v_ventas_diarias {}..{}: {} filas", desde, hasta, filas.size());
        return filas;
    }
}
