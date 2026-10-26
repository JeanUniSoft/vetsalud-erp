package pe.vetsalud.erp.persistencia;

import java.sql.SQLException;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import com.google.common.collect.ImmutableMap;

/**
 * Traduce los errores de PostgreSQL a mensajes comprensibles para el usuario.
 * Las reglas de negocio que protege la BD (RN-01 a RN-12) llegan como violaciones de restricción
 * o como RAISE EXCEPTION de los triggers; nunca se muestra SQL ni trazas en pantalla (RNF-16).
 */
public final class ErroresBD {

    /** Restricción violada → mensaje de negocio. */
    private static final Map<String, String> RESTRICCIONES = ImmutableMap.<String, String>builder()
            .put("uq_cliente_documento", "Ya existe un cliente con ese tipo y número de documento (RN-01).")
            .put("ck_cliente_dni", "El DNI debe tener 8 dígitos.")
            .put("ck_cliente_ruc", "El RUC debe tener 11 dígitos y empezar con 10 o 20.")
            .put("ex_cita_sin_solape", "El veterinario ya tiene una cita en ese horario (RN-03).")
            .put("ck_cita_rango", "La hora de fin debe ser posterior a la de inicio.")
            .put("uq_diagnostico_principal", "La consulta ya tiene un diagnóstico principal.")
            .put("ck_lote_stock_no_negativo", "No hay stock suficiente en el lote (RN-06).")
            .put("ck_ajuste_con_motivo", "Todo ajuste de inventario requiere un motivo (RF-25).")
            .put("ck_venta_descuento", "Un descuento mayor al 10 % requiere autorización del administrador (RN-08).")
            .put("ck_turno_diferencia_justificada", "La diferencia de caja requiere una observación (RN-10).")
            .put("uq_turno_abierto", "El cajero ya tiene un turno de caja abierto (RN-10).")
            .put("mascota_microchip_key", "Ese microchip ya está registrado en otra mascota.")
            .put("usuario_usuario_key", "Ese nombre de usuario ya existe.")
            .put("usuario_correo_key", "Ese correo ya pertenece a otro usuario.")
            .put("producto_codigo_key", "Ya existe un producto con ese código.")
            .put("lote_id_producto_codigo_lote_key", "Ese producto ya tiene un lote con ese código.")
            .put("ingreso_id_proveedor_tipo_doc_num_doc_key", "Ese documento de compra ya fue registrado.")
            .put("receta_detalle_id_receta_id_producto_key", "Ese producto ya está en la receta.")
            .put("consulta_id_cita_key", "La cita ya tiene una consulta registrada.")
            .put("cliente_usuario_id_cliente_key", "Ese cliente ya tiene un usuario del portal.")
            .put("pago_check", "Los pagos con tarjeta, Yape/Plin o transferencia requieren el número de operación.")
            .build();

    private ErroresBD() {
    }

    public static String mensaje(SQLException e) {
        if (e instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
            ServerErrorMessage sem = psql.getServerErrorMessage();
            String restriccion = sem.getConstraint();
            if (restriccion != null && RESTRICCIONES.containsKey(restriccion)) {
                return RESTRICCIONES.get(restriccion);
            }
            String estado = StringUtils.defaultString(sem.getSQLState());
            // P0001 = RAISE EXCEPTION sin código; 23514 = check_violation / 23000 = integrity (mensajes de triggers)
            if (estado.equals("P0001") || estado.equals("23514") || estado.equals("23000")) {
                return StringUtils.defaultIfBlank(sem.getMessage(), "La operación no cumple una regla de negocio.");
            }
            if (estado.equals("23505")) {
                return "El registro ya existe (dato duplicado).";
            }
            if (estado.equals("23503")) {
                return "El registro está relacionado con otros datos y no puede modificarse así.";
            }
            if (estado.equals("42501")) {
                return "La base de datos no permite esta operación a la aplicación (privilegio mínimo).";
            }
            if (estado.startsWith("22")) {
                return "Algún dato no tiene el formato esperado: " + StringUtils.defaultString(sem.getMessage());
            }
        }
        return "No se pudo completar la operación por un problema con la base de datos.";
    }
}
