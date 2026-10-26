package pe.vetsalud.erp.fakes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.vetsalud.erp.dao.CajaDao;
import pe.vetsalud.erp.modelo.ResumenTurno;
import pe.vetsalud.erp.modelo.TurnoCaja;

/** Doble de CajaDao. El efectivo cobrado y las ventas pendientes se configuran en cada prueba. */
public class CajaDaoEnMemoria implements CajaDao {

    public final Map<Integer, TurnoCaja> turnos = new LinkedHashMap<>();
    public BigDecimal efectivoCobrado = BigDecimal.ZERO;
    public int ventasPendientes;
    private int secuencia = 1;

    @Override
    public Optional<TurnoCaja> turnoAbierto(int idCajero) {
        return turnos.values().stream().filter(t -> t.idCajero() == idCajero && t.abierto()).findFirst();
    }

    @Override
    public Optional<TurnoCaja> buscarPorId(int idTurno) {
        return Optional.ofNullable(turnos.get(idTurno));
    }

    @Override
    public int abrir(int idCajero, BigDecimal montoInicial, int idUsuario) {
        int id = secuencia++;
        turnos.put(id, new TurnoCaja(id, idCajero, "Cajero " + idCajero, LocalDateTime.now(), montoInicial, null, null,
                null, null, null, "ABIERTA"));
        return id;
    }

    @Override
    public ResumenTurno resumen(int idTurno) {
        TurnoCaja t = turnos.get(idTurno);
        return new ResumenTurno(t, List.of(new ResumenTurno.PorMedio("EFECTIVO", 1, efectivoCobrado)), 1,
                efectivoCobrado, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, ventasPendientes,
                t.montoInicial().add(efectivoCobrado));
    }

    @Override
    public BigDecimal cerrar(int idTurno, BigDecimal efectivoContado, String observacion, int idUsuario) {
        TurnoCaja t = turnos.get(idTurno);
        BigDecimal esperado = t.montoInicial().add(efectivoCobrado);
        BigDecimal diferencia = efectivoContado.subtract(esperado);
        turnos.put(idTurno, new TurnoCaja(idTurno, t.idCajero(), t.cajero(), t.apertura(), t.montoInicial(),
                LocalDateTime.now(), esperado, efectivoContado, diferencia, observacion, "CERRADA"));
        return diferencia;
    }

    @Override
    public List<TurnoCaja> historial(Integer idCajero, LocalDate desde, LocalDate hasta, int limite) {
        return turnos.values().stream().filter(t -> idCajero == null || t.idCajero() == idCajero).toList();
    }

    @Override
    public List<TurnoCaja> abiertos() {
        return turnos.values().stream().filter(TurnoCaja::abierto).toList();
    }
}
