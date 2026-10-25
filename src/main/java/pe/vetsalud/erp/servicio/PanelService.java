package pe.vetsalud.erp.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import pe.vetsalud.erp.dao.PanelDao;
import pe.vetsalud.erp.modelo.AlertaInventario;
import pe.vetsalud.erp.modelo.CitaPanel;
import pe.vetsalud.erp.modelo.PuntoSerie;
import pe.vetsalud.erp.modelo.ResumenPanel;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** P02 · Panel principal: indicadores del día adaptados al rol (RF-32). */
public class PanelService {

    private final PanelDao dao;

    public PanelService(PanelDao dao) {
        this.dao = Preconditions.checkNotNull(dao);
    }

    public ResumenPanel resumen(LocalDate hoy) throws SQLException {
        return dao.resumen(hoy);
    }

    /** El veterinario ve solo su agenda; los demás roles ven la de toda la clínica. */
    public List<CitaPanel> agenda(LocalDate hoy, UsuarioSesion usuario) throws SQLException {
        Integer soloVeterinario = "VETERINARIO".equals(usuario.rol()) ? usuario.idUsuario() : null;
        return dao.citasDelDia(hoy, soloVeterinario);
    }

    /** Ventas de los últimos 7 días con la altura relativa de cada barra. */
    public List<PuntoSerie> ventasSemana(LocalDate hoy) throws SQLException {
        return escalar(dao.ventasPorDia(hoy.minusDays(6), hoy));
    }

    public List<AlertaInventario> alertasInventario() throws SQLException {
        return dao.alertasInventario();
    }

    /** Calcula la altura (0-100 %) de cada punto respecto del mayor; mínimo 2 % si hubo ventas. */
    static List<PuntoSerie> escalar(List<PuntoSerie> serie) {
        BigDecimal maximo = serie.stream().map(PuntoSerie::total).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        ImmutableList.Builder<PuntoSerie> salida = ImmutableList.builder();
        for (PuntoSerie p : serie) {
            int altura = 0;
            if (maximo.signum() > 0 && p.total().signum() > 0) {
                altura = Math.max(2, p.total().multiply(BigDecimal.valueOf(100))
                        .divide(maximo, 0, RoundingMode.HALF_UP).intValue());
            }
            salida.add(new PuntoSerie(p.dia(), p.total(), altura));
        }
        return salida.build();
    }
}
