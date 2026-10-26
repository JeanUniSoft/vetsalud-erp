package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.vetsalud.erp.fakes.MascotaClinicaDaoEnMemoria.BRUNO;
import static pe.vetsalud.erp.fakes.MascotaClinicaDaoEnMemoria.MAX;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.HistoriaDaoEnMemoria;
import pe.vetsalud.erp.fakes.MascotaClinicaDaoEnMemoria;
import pe.vetsalud.erp.modelo.Adenda;
import pe.vetsalud.erp.modelo.Consulta;
import pe.vetsalud.erp.modelo.Diagnostico;
import pe.vetsalud.erp.modelo.EventoHistoria;
import pe.vetsalud.erp.modelo.HistoriaClinica;
import pe.vetsalud.erp.modelo.ProcedimientoConsulta;
import pe.vetsalud.erp.modelo.Receta;
import pe.vetsalud.erp.modelo.RecetaLinea;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-13: historia clínica completa y cronológica de la mascota. */
class HistoriaServiceTest {

    private static final LocalDateTime PRIMERA = LocalDateTime.of(2026, 9, 10, 9, 0);
    private static final LocalDateTime SEGUNDA = LocalDateTime.of(2026, 10, 20, 15, 0);

    private HistoriaDaoEnMemoria dao;
    private HistoriaService servicio;

    @BeforeEach
    void preparar() {
        dao = new HistoriaDaoEnMemoria();
        servicio = new HistoriaService(dao, new MascotaClinicaDaoEnMemoria());
        dao.consultas.add(consulta(1, PRIMERA, LocalDate.of(2026, 9, 20), "FIRMADA"));
        dao.consultas.add(consulta(2, SEGUNDA, null, "ABIERTA"));
        dao.diagnosticos.add(new Diagnostico(11, 1, "Gastroenteritis aguda", true));
        dao.diagnosticos.add(new Diagnostico(12, 1, "Deshidratación leve", false));
        dao.procedimientos.add(new ProcedimientoConsulta(21, 1, 5, "Curación de herida", 5, "Jorge Huamán Cruz", 1,
                new BigDecimal("70.00"), "Sin complicaciones"));
        dao.recetas.add(new Receta(31, 2, SEGUNDA.plusMinutes(30), "Administrar con comida", List.of(
                new RecetaLinea(41, 31, 1, "Amoxicilina 250 mg", "CAJA", "1 tableta", "Cada 12 horas", 7, "ORAL",
                        BigDecimal.ONE))));
        dao.adendas.add(new Adenda(51, 1, 5, "Jorge Huamán Cruz", "Mejoría al segundo día", LocalDateTime.of(2026, 9, 12, 8, 0)));
    }

    private static Consulta consulta(long id, LocalDateTime fecha, LocalDate control, String estado) {
        return new Consulta(id, MAX, null, 4, "Carlos Mendoza Ríos", "04512", fecha, "Motivo " + id, "Anamnesis " + id,
                new BigDecimal("39.2"), 110, 28, new BigDecimal("31.00"), "Examen " + id, "Indicaciones " + id, control,
                estado, "FIRMADA".equals(estado) ? fecha.plusHours(1) : null);
    }

    private static List<String> tipos(HistoriaClinica h) {
        return h.eventos().stream().map(EventoHistoria::tipo).toList();
    }

    @Test
    @DisplayName("RF-13: reúne consultas, diagnósticos, procedimientos, recetas, adendas y controles en orden cronológico")
    void ordenCronologico() throws Exception {
        HistoriaClinica h = servicio.historia(MAX, false);
        assertEquals(List.of("CONSULTA", "DIAGNOSTICO", "DIAGNOSTICO", "PROCEDIMIENTO", "ADENDA", "CONTROL", "CONSULTA",
                "RECETA"), tipos(h));
        assertEquals("Diagnóstico principal", h.eventos().get(1).titulo());
        assertEquals(1L, h.consultasAbiertas());
        assertTrue(h.eventos().getFirst().lineas().contains("Signos vitales: T° 39.2 °C · FC 110 lpm · FR 28 rpm · Peso 31 kg"));
    }

    @Test
    @DisplayName("RF-13: con «recientes primero» se invierte el orden y la consulta sigue antes que sus registros")
    void recientesPrimero() throws Exception {
        HistoriaClinica h = servicio.historia(MAX, true);
        assertEquals(List.of("RECETA", "CONSULTA", "CONTROL", "ADENDA", "CONSULTA", "DIAGNOSTICO", "DIAGNOSTICO",
                "PROCEDIMIENTO"), tipos(h));
        assertTrue(h.recientesPrimero());
    }

    @Test
    @DisplayName("RF-17 / RF-13: la receta muestra cada medicamento con dosis, frecuencia, duración y vía")
    void recetaEnLaHistoria() throws Exception {
        EventoHistoria receta = servicio.historia(MAX, false).eventos().stream()
                .filter(e -> e.tipo().equals("RECETA")).findFirst().orElseThrow();
        assertEquals("Receta N.° 000031", receta.titulo());
        assertEquals(List.of("Amoxicilina 250 mg: 1 tableta, cada 12 horas, 7 días, vía oral"), receta.lineas());
    }

    @Test
    @DisplayName("RF-13: las alertas clínicas de la mascota (alergias) acompañan la historia, aunque no tenga consultas")
    void alertasClinicas() throws Exception {
        HistoriaClinica h = servicio.historia(BRUNO, false);
        assertEquals("Alergia alimentaria al pollo", h.mascota().alertas());
        assertTrue(h.eventos().isEmpty());
        assertThrows(ReglaNegocioException.class, () -> servicio.historia(777, false));
    }

    @Test
    @DisplayName("RF-18: el veterinario ve sus consultas abiertas; el técnico ve las de todos")
    void consultasAbiertas() throws Exception {
        servicio.consultasAbiertas(new UsuarioSesion(4, "cmendoza", "Carlos Mendoza", "VETERINARIO", Set.of(), null, false));
        assertEquals(4, dao.ultimoFiltroVeterinario);
        servicio.consultasAbiertas(new UsuarioSesion(5, "jhuaman", "Jorge Huamán", "TECNICO", Set.of(), null, false));
        assertNull(dao.ultimoFiltroVeterinario);
    }
}
