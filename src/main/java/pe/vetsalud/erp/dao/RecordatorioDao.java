package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import pe.vetsalud.erp.modelo.Recordatorio;
import pe.vetsalud.erp.modelo.RecordatorioResumen;

/** Cola de recordatorios por correo (tabla recordatorio, RF-37). */
public interface RecordatorioDao {

    /** Últimos recordatorios, opcionalmente de un estado (null = todos). */
    List<Recordatorio> listar(String estado, int limite) throws SQLException;

    RecordatorioResumen resumen() throws SQLException;

    /**
     * Recordatorios PENDIENTES por procesar, del más antiguo al más reciente.
     *
     * @param incluirFuturos false = solo los que ya vencieron (programado_para &lt;= ahora); true = toda la cola
     */
    List<Recordatorio> pendientes(boolean incluirFuturos, int limite) throws SQLException;

    /**
     * Cambia el estado solo si sigue en {@code estadoActual} (evita que dos procesos envíen el mismo recordatorio).
     *
     * @return true si esta llamada hizo el cambio
     */
    boolean cambiarEstado(long idRecordatorio, String estadoActual, String estadoNuevo, LocalDateTime fecha,
                          Integer idUsuario) throws SQLException;

    /** Crea el recordatorio que falte para las próximas citas pendientes o confirmadas; devuelve cuántos creó. */
    int programarCitas(int horasAntes, Integer idUsuario) throws SQLException;

    /** Crea el recordatorio que falte para los controles futuros (consulta firmada con fecha_control); el día anterior a la hora dada. */
    int programarControles(LocalTime hora, Integer idUsuario) throws SQLException;
}
