package pe.vetsalud.erp.servicio;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import pe.vetsalud.erp.dao.HistoriaDao;
import pe.vetsalud.erp.dao.MascotaClinicaDao;
import pe.vetsalud.erp.modelo.Adenda;
import pe.vetsalud.erp.modelo.Consulta;
import pe.vetsalud.erp.modelo.ConsultaResumen;
import pe.vetsalud.erp.modelo.Diagnostico;
import pe.vetsalud.erp.modelo.EventoHistoria;
import pe.vetsalud.erp.modelo.HistoriaClinica;
import pe.vetsalud.erp.modelo.MascotaClinica;
import pe.vetsalud.erp.modelo.ProcedimientoConsulta;
import pe.vetsalud.erp.modelo.Receta;
import pe.vetsalud.erp.modelo.RecetaLinea;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/**
 * UC-07 Consultar historia clínica (RF-13): reúne consultas, diagnósticos, procedimientos, recetas, adendas
 * y controles de la mascota en una sola línea de tiempo cronológica, con sus alertas clínicas.
 */
public class HistoriaService {

    public static final int LIMITE_BUSQUEDA = 30;

    /** Orden dentro de un mismo instante: la consulta antes que sus diagnósticos, procedimientos y recetas. */
    private static final Map<String, Integer> ORDEN_TIPO = ImmutableMap.<String, Integer>builder()
            .put("CONSULTA", 0).put("DIAGNOSTICO", 1).put("PROCEDIMIENTO", 2).put("RECETA", 3)
            .put("ADENDA", 4).put("CONTROL", 5).build();

    /** Nombre visible de cada tipo de evento de la línea de tiempo. */
    public static final Map<String, String> TIPOS = ImmutableMap.<String, String>builder()
            .put("CONSULTA", "Consulta").put("DIAGNOSTICO", "Diagnóstico").put("PROCEDIMIENTO", "Procedimiento")
            .put("RECETA", "Receta").put("ADENDA", "Adenda").put("CONTROL", "Control").build();

    private final HistoriaDao dao;
    private final MascotaClinicaDao mascotaDao;

    public HistoriaService(HistoriaDao dao, MascotaClinicaDao mascotaDao) {
        this.dao = Preconditions.checkNotNull(dao);
        this.mascotaDao = Preconditions.checkNotNull(mascotaDao);
    }

    public List<MascotaClinica> buscar(String texto) throws SQLException {
        return mascotaDao.buscar(StringUtils.trimToNull(texto), LIMITE_BUSQUEDA);
    }

    /** Consultas sin firmar: el veterinario ve las suyas; técnico y administrador, todas (RF-18). */
    public List<ConsultaResumen> consultasAbiertas(UsuarioSesion u) throws SQLException {
        return dao.consultasAbiertas(ConsultaService.VETERINARIO.equals(u.rol()) ? u.idUsuario() : null);
    }

    public HistoriaClinica historia(int idMascota, boolean recientesPrimero) throws SQLException {
        MascotaClinica mascota = mascotaDao.buscarPorId(idMascota)
                .orElseThrow(() -> new ReglaNegocioException("La mascota solicitada no existe."));
        List<Consulta> consultas = dao.consultas(idMascota);
        Map<Long, Consulta> porId = Maps.uniqueIndex(consultas, Consulta::idConsulta);
        List<EventoHistoria> eventos = new ArrayList<>();

        for (Consulta c : consultas) {
            eventos.add(new EventoHistoria(c.fechaHora(), "CONSULTA", "Consulta: " + c.motivo(), c.anamnesis(),
                    "M.V. " + c.veterinario(), c.estado(), c.idConsulta(), lineasConsulta(c)));
            if (c.fechaControl() != null) {
                eventos.add(new EventoHistoria(c.fechaControl().atStartOfDay(), "CONTROL", "Control programado",
                        "Control posterior a la consulta N.° " + c.idConsulta() + ".", "M.V. " + c.veterinario(),
                        null, c.idConsulta(), List.of()));
            }
        }
        for (Diagnostico d : dao.diagnosticos(idMascota)) {
            Consulta c = porId.get(d.idConsulta());
            if (c != null) {
                eventos.add(new EventoHistoria(c.fechaHora(), "DIAGNOSTICO",
                        d.esPrincipal() ? "Diagnóstico principal" : "Diagnóstico secundario", d.descripcion(),
                        "M.V. " + c.veterinario(), null, c.idConsulta(), List.of()));
            }
        }
        for (ProcedimientoConsulta p : dao.procedimientos(idMascota)) {
            Consulta c = porId.get(p.idConsulta());
            if (c != null) {
                eventos.add(new EventoHistoria(c.fechaHora(), "PROCEDIMIENTO",
                        p.servicio() + (p.cantidad() > 1 ? " × " + p.cantidad() : ""), p.resultado(), p.responsable(),
                        null, c.idConsulta(), List.of()));
            }
        }
        for (Receta r : dao.recetas(idMascota)) {
            Consulta c = porId.get(r.idConsulta());
            eventos.add(new EventoHistoria(r.fecha(), "RECETA", "Receta N.° " + r.numero(), r.indicaciones(),
                    c == null ? null : "M.V. " + c.veterinario(), null, r.idConsulta(),
                    r.lineas().stream().map(RecetaLinea::resumen).toList()));
        }
        for (Adenda a : dao.adendas(idMascota)) {
            eventos.add(new EventoHistoria(a.fechaHora(), "ADENDA", "Adenda a la consulta N.° " + a.idConsulta(),
                    a.texto(), a.autor(), null, a.idConsulta(), List.of()));
        }
        return new HistoriaClinica(mascota, ordenar(eventos, recientesPrimero), consultas, recientesPrimero);
    }

    /** RF-13: orden cronológico (o el inverso), manteniendo juntos los registros de una misma consulta. */
    static List<EventoHistoria> ordenar(List<EventoHistoria> eventos, boolean recientesPrimero) {
        Comparator<EventoHistoria> porFecha = Comparator.comparing(EventoHistoria::fechaHora);
        Comparator<EventoHistoria> orden = (recientesPrimero ? porFecha.reversed() : porFecha)
                .thenComparing(EventoHistoria::idConsulta, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(e -> ORDEN_TIPO.getOrDefault(e.tipo(), 9));
        return ImmutableList.sortedCopyOf(orden, eventos);
    }

    private static List<String> lineasConsulta(Consulta c) {
        List<String> l = new ArrayList<>();
        List<String> signos = new ArrayList<>();
        if (c.temperaturaC() != null) {
            signos.add("T° " + c.temperaturaC().stripTrailingZeros().toPlainString() + " °C");
        }
        if (c.frecCardiaca() != null) {
            signos.add("FC " + c.frecCardiaca() + " lpm");
        }
        if (c.frecRespiratoria() != null) {
            signos.add("FR " + c.frecRespiratoria() + " rpm");
        }
        if (c.pesoKg() != null) {
            signos.add("Peso " + c.pesoKg().stripTrailingZeros().toPlainString() + " kg");
        }
        if (!signos.isEmpty()) {
            l.add("Signos vitales: " + String.join(" · ", signos));
        }
        if (c.examenClinico() != null) {
            l.add("Examen clínico: " + c.examenClinico());
        }
        if (c.indicaciones() != null) {
            l.add("Indicaciones: " + c.indicaciones());
        }
        return l;
    }
}
