package pe.vetsalud.erp.dao;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import pe.vetsalud.erp.modelo.ResumenTurno;
import pe.vetsalud.erp.modelo.TurnoCaja;

/** Turnos de caja: apertura, resumen y cierre conciliado (RF-30, RN-10). */
public interface CajaDao {

    Optional<TurnoCaja> turnoAbierto(int idCajero) throws SQLException;

    Optional<TurnoCaja> buscarPorId(int idTurno) throws SQLException;

    int abrir(int idCajero, BigDecimal montoInicial, int idUsuario) throws SQLException;

    ResumenTurno resumen(int idTurno) throws SQLException;

    /** fn_cerrar_caja: calcula el efectivo esperado, guarda el contado y devuelve la diferencia. */
    BigDecimal cerrar(int idTurno, BigDecimal efectivoContado, String observacion, int idUsuario) throws SQLException;

    /** Turnos por fecha de apertura (idCajero null = de todos los cajeros). */
    List<TurnoCaja> historial(Integer idCajero, LocalDate desde, LocalDate hasta, int limite) throws SQLException;

    List<TurnoCaja> abiertos() throws SQLException;
}
