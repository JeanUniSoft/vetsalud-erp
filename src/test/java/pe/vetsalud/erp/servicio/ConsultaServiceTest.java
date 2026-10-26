package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.vetsalud.erp.fakes.ConsultaDaoEnMemoria.AMOXICILINA;
import static pe.vetsalud.erp.fakes.ConsultaDaoEnMemoria.COLLAR;
import static pe.vetsalud.erp.fakes.MascotaClinicaDaoEnMemoria.BRUNO;
import static pe.vetsalud.erp.fakes.MascotaClinicaDaoEnMemoria.MAX;
import static pe.vetsalud.erp.fakes.ProfesionalDaoEnMemoria.MENDOZA;
import static pe.vetsalud.erp.fakes.ProfesionalDaoEnMemoria.RECEPCION;
import static pe.vetsalud.erp.fakes.ProfesionalDaoEnMemoria.SALAZAR;
import static pe.vetsalud.erp.fakes.ProfesionalDaoEnMemoria.TECNICO;
import static pe.vetsalud.erp.fakes.ServicioDaoEnMemoria.CONSULTA;
import static pe.vetsalud.erp.fakes.ServicioDaoEnMemoria.CURACION;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pe.vetsalud.erp.fakes.CitaDaoEnMemoria;
import pe.vetsalud.erp.fakes.ConsultaDaoEnMemoria;
import pe.vetsalud.erp.fakes.MascotaClinicaDaoEnMemoria;
import pe.vetsalud.erp.fakes.ProfesionalDaoEnMemoria;
import pe.vetsalud.erp.fakes.ServicioDaoEnMemoria;
import pe.vetsalud.erp.modelo.Cita;
import pe.vetsalud.erp.modelo.Consulta;
import pe.vetsalud.erp.modelo.ConsultaDatos;
import pe.vetsalud.erp.modelo.ConsultaFirma;
import pe.vetsalud.erp.modelo.ConsultaPermisos;
import pe.vetsalud.erp.modelo.ConsultaSignos;
import pe.vetsalud.erp.modelo.ProcedimientoConsulta;
import pe.vetsalud.erp.seguridad.UsuarioSesion;

/** RF-14 a RF-19, RN-04 y RN-05: atención clínica, diagnóstico, receta, firma y adendas. */
class ConsultaServiceTest {

    private static final LocalDate DIA_CONSULTA = LocalDate.of(2026, 10, 25);

    private final UsuarioSesion mendoza = vet(MENDOZA, "cmendoza", Set.of("CITA_GESTIONAR", "CONSULTA_FIRMAR"));
    private final UsuarioSesion salazar = vet(SALAZAR, "asalazar", Set.of("CITA_GESTIONAR", "CONSULTA_FIRMAR"));
    private final UsuarioSesion vetSinFirma = vet(MENDOZA, "cmendoza", Set.of("CITA_GESTIONAR"));
    private final UsuarioSesion tecnico = new UsuarioSesion(TECNICO, "jhuaman", "Jorge Huamán Cruz", "TECNICO", Set.of(), null, false);
    private final UsuarioSesion admin = new UsuarioSesion(1, "rparedes", "Rosa Paredes Quispe", "ADMINISTRADOR",
            Set.of("CONSULTA_FIRMAR", "CITA_GESTIONAR"), null, false);

    private ConsultaDaoEnMemoria dao;
    private CitaDaoEnMemoria citas;
    private ConsultaService servicio;

    private static UsuarioSesion vet(int id, String usuario, Set<String> permisos) {
        return new UsuarioSesion(id, usuario, usuario, "VETERINARIO", permisos, null, false);
    }

    @BeforeEach
    void preparar() {
        dao = new ConsultaDaoEnMemoria();
        citas = new CitaDaoEnMemoria();
        servicio = new ConsultaService(dao, citas, new MascotaClinicaDaoEnMemoria(), new ServicioDaoEnMemoria(),
                new ProfesionalDaoEnMemoria());
    }

    private long abrirParaMax() throws Exception {
        return servicio.abrir(null, MAX, "Vómitos desde ayer", mendoza);
    }

    private static ConsultaDatos datos(LocalDate control) {
        return new ConsultaDatos("Vómitos desde ayer", "Comió basura en el parque", "Abdomen sensible",
                "Dieta blanda 3 días", control);
    }

    private Cita citaEnAtencion() {
        LocalDateTime inicio = DIA_CONSULTA.atTime(9, 0);
        return citas.agregar(MAX, "Max", MENDOZA, "Carlos Mendoza Ríos", CONSULTA, inicio, inicio.plusMinutes(30), "EN_ATENCION");
    }

    // ------------------------------------------------------------------ abrir (RF-14, RN-04)

    @Test
    @DisplayName("RN-04: solo un médico veterinario abre consultas (técnico y administrador no)")
    void soloVeterinarioAbre() throws Exception {
        assertThrows(ReglaNegocioException.class, () -> servicio.abrir(null, MAX, "Control", tecnico));
        assertThrows(ReglaNegocioException.class, () -> servicio.abrir(null, MAX, "Control", admin));
        Consulta c = servicio.obtener(abrirParaMax());
        assertEquals(MENDOZA, c.idVeterinario());
        assertTrue(c.abierta());
    }

    @Test
    @DisplayName("RF-14: desde una cita en atención la consulta queda vinculada y hereda el motivo")
    void desdeCita() throws Exception {
        Cita cita = citaEnAtencion();
        Consulta c = servicio.obtener(servicio.abrir(cita.idCita(), null, null, mendoza));
        assertEquals(cita.idCita(), c.idCita());
        assertEquals(MAX, c.idMascota());
        assertEquals("Motivo original", c.motivo());
        assertThrows(ReglaNegocioException.class, () -> servicio.abrir(cita.idCita(), null, null, mendoza),
                "una cita solo tiene una consulta");
    }

    @Test
    @DisplayName("RF-12: no se inicia la consulta si la cita no está «En atención»")
    void citaNoEnAtencion() {
        LocalDateTime inicio = DIA_CONSULTA.atTime(11, 0);
        Cita cita = citas.agregar(BRUNO, "Bruno", MENDOZA, "Carlos Mendoza Ríos", CONSULTA, inicio, inicio.plusMinutes(30), "CONFIRMADA");
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.abrir(cita.idCita(), null, null, mendoza));
        assertTrue(e.getMessage().contains("En atención"));
    }

    // ------------------------------------------------------------------ signos vitales (RF-14)

    @Test
    @DisplayName("RF-14: los signos vitales dentro de rango se guardan (temperatura con un decimal)")
    void signosValidos() throws Exception {
        long id = abrirParaMax();
        servicio.registrarSignos(id, new ConsultaSignos(new BigDecimal("38.55"), 110, 28, new BigDecimal("31")), tecnico);
        Consulta c = servicio.obtener(id);
        assertEquals(new BigDecimal("38.6"), c.temperaturaC());
        assertEquals(110, c.frecCardiaca());
        assertEquals(new BigDecimal("31.00"), c.pesoKg());
    }

    @ParameterizedTest(name = "T={0} FC={1} FR={2} peso={3}")
    @CsvSource({"29.9,,,", "45.1,,,", ",0,,", ",401,,", ",,0,", ",,201,", ",,,0", ",,,-2", ",,,1000"})
    @DisplayName("RF-14: signos vitales fuera de rango rechazados (temperatura 30–45 °C, FC, FR y peso)")
    void signosFueraDeRango(String t, Integer fc, Integer fr, String peso) throws Exception {
        long id = abrirParaMax();
        ConsultaSignos s = new ConsultaSignos(t == null ? null : new BigDecimal(t), fc, fr, peso == null ? null : new BigDecimal(peso));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrarSignos(id, s, mendoza));
        assertFalse(servicio.obtener(id).conSignos());
    }

    @Test
    @DisplayName("RF-14: se exige al menos un signo vital")
    void signosVacios() throws Exception {
        long id = abrirParaMax();
        assertThrows(ReglaNegocioException.class,
                () -> servicio.registrarSignos(id, new ConsultaSignos(null, null, null, null), mendoza));
    }

    // ------------------------------------------------------------------ diagnósticos (RF-15)

    @Test
    @DisplayName("RF-15: uno o más diagnósticos, pero solo uno principal")
    void unSoloPrincipal() throws Exception {
        long id = abrirParaMax();
        servicio.agregarDiagnostico(id, "Gastroenteritis aguda", true, mendoza);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.agregarDiagnostico(id, "Pancreatitis", true, mendoza));
        assertTrue(e.getMessage().contains("RF-15"));
        servicio.agregarDiagnostico(id, "Deshidratación leve", false, mendoza);
        assertEquals(2, servicio.diagnosticos(id).size());
        assertEquals(1, servicio.diagnosticos(id).stream().filter(d -> d.esPrincipal()).count());
        assertThrows(ReglaNegocioException.class, () -> servicio.agregarDiagnostico(id, "deshidratación LEVE", false, mendoza),
                "no se repite el mismo diagnóstico");
    }

    @Test
    @DisplayName("RN-04: el técnico registra signos y procedimientos, pero no diagnostica, no receta ni firma")
    void alcanceDelTecnico() throws Exception {
        long id = abrirParaMax();
        servicio.registrarSignos(id, new ConsultaSignos(new BigDecimal("39.2"), null, null, null), tecnico);
        servicio.agregarProcedimiento(id, CURACION, TECNICO, 1, "Limpieza y vendaje", tecnico);
        assertThrows(ReglaNegocioException.class, () -> servicio.agregarDiagnostico(id, "Gastritis", true, tecnico));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarDatos(id, datos(null), tecnico));
        assertThrows(ReglaNegocioException.class, () -> servicio.agregarMedicamento(id, AMOXICILINA, "1 tableta",
                "Cada 12 horas", 7, "ORAL", BigDecimal.ONE, tecnico));
        assertThrows(ReglaNegocioException.class, () -> servicio.firmar(id, tecnico));
    }

    // ------------------------------------------------------------------ procedimientos y receta (RF-16, RF-17)

    @Test
    @DisplayName("RF-16: el procedimiento copia la tarifa vigente y calcula el importe; responsable clínico obligatorio")
    void procedimientoConTarifa() throws Exception {
        long id = abrirParaMax();
        servicio.agregarProcedimiento(id, CURACION, MENDOZA, 2, "Sin complicaciones", mendoza);
        ProcedimientoConsulta p = servicio.procedimientos(id).getFirst();
        assertEquals(new BigDecimal("70.00"), p.precioUnitario());
        assertEquals(new BigDecimal("140.00"), p.importe());
        assertThrows(ReglaNegocioException.class,
                () -> servicio.agregarProcedimiento(id, CURACION, RECEPCION, 1, null, mendoza), "recepción no es personal clínico");
        assertThrows(ReglaNegocioException.class,
                () -> servicio.agregarProcedimiento(id, CONSULTA, MENDOZA, 1, null, mendoza), "la consulta no es un procedimiento");
        assertThrows(ReglaNegocioException.class,
                () -> servicio.agregarProcedimiento(id, CURACION, MENDOZA, 0, null, mendoza));
    }

    @Test
    @DisplayName("RF-17: la receta se crea con el primer medicamento y se vincula a la consulta")
    void recetaConMedicamentos() throws Exception {
        long id = abrirParaMax();
        servicio.agregarMedicamento(id, AMOXICILINA, "1 tableta (250 mg)", "Cada 12 horas", 7, "ORAL", BigDecimal.ONE, mendoza);
        servicio.agregarMedicamento(id, COLLAR, "Uso continuo", "Permanente", 10, "TOPICA", BigDecimal.ONE, mendoza);
        servicio.guardarReceta(id, "Administrar con comida", mendoza);
        var receta = servicio.receta(id).orElseThrow();
        assertEquals(id, receta.idConsulta());
        assertEquals(2, receta.lineas().size());
        assertEquals("Administrar con comida", receta.indicaciones());
        assertEquals("Amoxicilina 250 mg x 10 tab: 1 tableta (250 mg), cada 12 horas, 7 días, vía oral",
                receta.lineas().getFirst().resumen());
    }

    @ParameterizedTest(name = "producto={0} duración={1} vía={2} cantidad={3}")
    @CsvSource({"1,0,ORAL,1", "1,366,ORAL,1", "1,7,NASAL,1", "1,7,,1", "1,7,ORAL,0", "99,7,ORAL,1", ",7,ORAL,1"})
    @DisplayName("RF-17: valida producto activo (99 = retirado), duración 1–365 días, vía permitida y cantidad")
    void recetaInvalida(Integer producto, Integer dias, String via, String cantidad) throws Exception {
        long id = abrirParaMax();
        assertThrows(ReglaNegocioException.class, () -> servicio.agregarMedicamento(id, producto, "1 tableta",
                "Cada 12 horas", dias, via, new BigDecimal(cantidad), mendoza));
        assertTrue(servicio.receta(id).isEmpty());
    }

    @Test
    @DisplayName("RF-17: un producto no se repite en la misma receta")
    void productoRepetido() throws Exception {
        long id = abrirParaMax();
        servicio.agregarMedicamento(id, AMOXICILINA, "1 tableta", "Cada 12 horas", 7, "ORAL", BigDecimal.ONE, mendoza);
        assertThrows(ReglaNegocioException.class, () -> servicio.agregarMedicamento(id, AMOXICILINA, "2 tabletas",
                "Cada 8 horas", 5, "ORAL", BigDecimal.ONE, mendoza));
    }

    // ------------------------------------------------------------------ control y firma (RF-18, RF-19, RN-04, RN-05)

    @Test
    @DisplayName("RF-19: la fecha de control debe ser posterior a la consulta (y dentro de un año)")
    void fechaDeControl() throws Exception {
        long id = abrirParaMax();
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarDatos(id, datos(DIA_CONSULTA), mendoza));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarDatos(id, datos(DIA_CONSULTA.minusDays(3)), mendoza));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarDatos(id, datos(DIA_CONSULTA.plusDays(400)), mendoza));
        servicio.actualizarDatos(id, datos(DIA_CONSULTA.plusDays(1)), mendoza);
        assertEquals(DIA_CONSULTA.plusDays(1), servicio.obtener(id).fechaControl());
    }

    @Test
    @DisplayName("RN-04: no se firma sin diagnóstico principal")
    void firmaSinPrincipal() throws Exception {
        long id = abrirParaMax();
        assertThrows(ReglaNegocioException.class, () -> servicio.firmar(id, mendoza));
        servicio.agregarDiagnostico(id, "Deshidratación leve", false, mendoza);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.firmar(id, mendoza));
        assertTrue(e.getMessage().contains("diagnóstico principal"));
        servicio.agregarDiagnostico(id, "Gastroenteritis aguda", true, mendoza);
        servicio.firmar(id, mendoza);
        assertTrue(servicio.obtener(id).firmada());
    }

    @Test
    @DisplayName("RF-18: solo el veterinario responsable firma, y necesita el permiso CONSULTA_FIRMAR")
    void quienFirma() throws Exception {
        long id = abrirParaMax();
        servicio.agregarDiagnostico(id, "Gastroenteritis aguda", true, mendoza);
        assertThrows(ReglaNegocioException.class, () -> servicio.firmar(id, salazar), "otro veterinario");
        assertThrows(ReglaNegocioException.class, () -> servicio.firmar(id, admin), "administrador");
        assertThrows(ReglaNegocioException.class, () -> servicio.firmar(id, vetSinFirma), "sin permiso");
        assertTrue(servicio.obtener(id).abierta());
    }

    @Test
    @DisplayName("RF-19: al firmar con control se programa el recordatorio el día anterior a las 09:00 para el dueño")
    void firmaConRecordatorio() throws Exception {
        long id = abrirParaMax();
        servicio.actualizarDatos(id, datos(LocalDate.of(2026, 10, 30)), mendoza);
        servicio.agregarDiagnostico(id, "Gastroenteritis aguda", true, mendoza);
        ConsultaFirma f = servicio.firmar(id, mendoza);
        assertTrue(f.conRecordatorio());
        assertEquals(7, f.idClienteRecordatorio());
        assertEquals(LocalDateTime.of(2026, 10, 29, 9, 0), f.recordatorioPara());
        assertEquals(f, dao.firmas.getFirst());
    }

    @Test
    @DisplayName("RF-37: si el dueño no aceptó recordatorios, se firma sin programar el aviso")
    void sinConsentimientoNoHayRecordatorio() throws Exception {
        long id = servicio.abrir(null, BRUNO, "Prurito", mendoza);
        servicio.actualizarDatos(id, new ConsultaDatos("Prurito", null, null, null, LocalDate.of(2026, 11, 8)), mendoza);
        servicio.agregarDiagnostico(id, "Dermatitis alérgica", true, mendoza);
        ConsultaFirma f = servicio.firmar(id, mendoza);
        assertFalse(f.conRecordatorio());
        assertNull(f.recordatorioPara());
        assertEquals(LocalDate.of(2026, 11, 8), f.fechaControl());
    }

    @Test
    @DisplayName("RF-18: al firmar, la cita en atención pasa a «Atendida» en la misma operación")
    void firmaCierraLaCita() throws Exception {
        Cita cita = citaEnAtencion();
        long id = servicio.abrir(cita.idCita(), null, null, mendoza);
        servicio.agregarDiagnostico(id, "Gastroenteritis aguda", true, mendoza);
        ConsultaFirma f = servicio.firmar(id, mendoza);
        assertEquals(cita.idCita(), f.idCitaAtendida());
    }

    @Test
    @DisplayName("RN-05: la consulta firmada no se edita; solo admite adendas con autor y fecha")
    void firmadaSoloAdendas() throws Exception {
        long id = abrirParaMax();
        assertThrows(ReglaNegocioException.class, () -> servicio.agregarAdenda(id, "Nota", mendoza),
                "las adendas son solo para consultas firmadas");
        servicio.agregarDiagnostico(id, "Gastroenteritis aguda", true, mendoza);
        servicio.firmar(id, mendoza);

        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarDatos(id, datos(null), mendoza));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrarSignos(id,
                new ConsultaSignos(new BigDecimal("38"), null, null, null), tecnico));
        assertThrows(ReglaNegocioException.class, () -> servicio.agregarDiagnostico(id, "Otro", false, mendoza));
        assertThrows(ReglaNegocioException.class, () -> servicio.agregarProcedimiento(id, CURACION, MENDOZA, 1, null, mendoza));
        assertThrows(ReglaNegocioException.class, () -> servicio.guardarReceta(id, "Cambio", mendoza));
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.firmar(id, mendoza));
        assertTrue(e.getMessage().contains("RN-05"));

        servicio.agregarAdenda(id, "El propietario reporta mejoría al segundo día.", tecnico);
        var adenda = servicio.adendas(id).getFirst();
        assertEquals(TECNICO, adenda.idUsuario());
        assertTrue(adenda.fechaHora() != null && adenda.autor() != null);
        assertThrows(ReglaNegocioException.class, () -> servicio.agregarAdenda(id, "   ", mendoza));
    }

    @Test
    @DisplayName("Permisos de pantalla coherentes con las reglas por rol (RN-04, RN-05)")
    void permisosDePantalla() throws Exception {
        long id = abrirParaMax();
        Consulta abierta = servicio.obtener(id);
        assertEquals(new ConsultaPermisos(true, true, true, true, true, true, false), servicio.permisos(abierta, mendoza));
        assertEquals(new ConsultaPermisos(false, true, false, true, false, false, false), servicio.permisos(abierta, tecnico));
        assertEquals(new ConsultaPermisos(false, false, false, false, false, false, false), servicio.permisos(abierta, salazar));
        assertFalse(servicio.permisos(abierta, vetSinFirma).firmar());
        servicio.agregarDiagnostico(id, "Gastroenteritis aguda", true, mendoza);
        servicio.firmar(id, mendoza);
        Consulta firmada = servicio.obtener(id);
        assertEquals(new ConsultaPermisos(false, false, false, false, false, false, true), servicio.permisos(firmada, mendoza));
        assertFalse(servicio.permisos(firmada, admin).adenda());
    }
}
