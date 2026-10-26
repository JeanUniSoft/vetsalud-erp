package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.CatalogoMascotaDaoEnMemoria;
import pe.vetsalud.erp.fakes.ClienteDaoEnMemoria;
import pe.vetsalud.erp.fakes.MascotaDaoEnMemoria;
import pe.vetsalud.erp.fakes.MascotaFotoAlmacenEnMemoria;
import pe.vetsalud.erp.modelo.Cliente;
import pe.vetsalud.erp.modelo.Mascota;

import static pe.vetsalud.erp.fakes.CatalogoMascotaDaoEnMemoria.CANINO;
import static pe.vetsalud.erp.fakes.CatalogoMascotaDaoEnMemoria.FELINO;
import static pe.vetsalud.erp.fakes.CatalogoMascotaDaoEnMemoria.LABRADOR;
import static pe.vetsalud.erp.fakes.CatalogoMascotaDaoEnMemoria.SIAMES;

/** RF-07 / RF-08 / RN-02: registro, estado, foto, microchip y cambio de propietario de mascotas. */
class MascotaServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 25);

    private MascotaDaoEnMemoria dao;
    private CatalogoMascotaDaoEnMemoria catalogo;
    private ClienteDaoEnMemoria clientes;
    private MascotaFotoAlmacenEnMemoria fotos;
    private MascotaService servicio;
    private int maria;
    private int jose;
    private int valentina;

    @BeforeEach
    void preparar() {
        dao = new MascotaDaoEnMemoria();
        catalogo = new CatalogoMascotaDaoEnMemoria();
        clientes = new ClienteDaoEnMemoria();
        fotos = new MascotaFotoAlmacenEnMemoria();
        Clock reloj = Clock.fixed(HOY.atTime(10, 0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima"));
        servicio = new MascotaService(dao, catalogo, clientes, fotos, reloj);
        maria = clientes.insertar(Cliente.nuevo("DNI", "45678912", "María", "Gonzales", "987654321",
                "maria@gmail.com", null, "Surco", true), 2);
        jose = clientes.insertar(Cliente.nuevo("DNI", "70123456", "José", "Pérez", "912345678", null, null, null, false), 2);
        Cliente j = clientes.clientes.get(jose);
        clientes.actualizar(new Cliente(jose, j.tipoDocumento(), j.numDocumento(), j.nombres(), j.apellidos(), j.telefono(),
                j.correo(), j.direccion(), j.distrito(), false, false, null, 0), 2);   // inactivo
        valentina = clientes.insertar(Cliente.nuevo("CE", "001234567", "Valentina", "Rossi", "955112233",
                "vrossi@gmail.com", null, null, true), 2);
    }

    private static Mascota rocky(int idCliente) {
        return Mascota.datos(null, idCliente, CANINO, LABRADOR, "  rocky ", "m", LocalDate.of(2021, 3, 10), "dorado",
                new BigDecimal("28.456"), "985 141 000 123 456", true, "  Alérgico   a la penicilina ");
    }

    private int registrarRocky() throws Exception {
        return servicio.registrar(rocky(maria), 2, "ltorres");
    }

    @Test
    @DisplayName("RF-07: registra la mascota vinculada a un cliente activo y normaliza sus datos")
    void registroNormalizado() throws Exception {
        Mascota m = dao.mascotas.get(registrarRocky());
        assertEquals(maria, m.idCliente());
        assertEquals("Rocky", m.nombre());
        assertEquals("M", m.sexo());
        assertEquals("Dorado", m.color());
        assertEquals(new BigDecimal("28.46"), m.pesoKg());
        assertEquals("985141000123456", m.microchip());
        assertEquals("Alérgico a la penicilina", m.alertas());
        assertEquals("Canino", m.especie());
        assertEquals(Mascota.ACTIVA, m.estado());
    }

    @Test
    @DisplayName("RN-02: no se registra una mascota a un cliente inactivo ni inexistente")
    void clienteInactivo() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(rocky(jose), 2, "ltorres"));
        assertTrue(e.getMessage().contains("RN-02"));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(rocky(999), 2, "ltorres"));
        assertTrue(dao.mascotas.isEmpty());
    }

    @Test
    @DisplayName("RF-07: la raza debe corresponder a la especie seleccionada")
    void razaCoherente() {
        Mascota gatoLabrador = Mascota.datos(null, maria, FELINO, LABRADOR, "Michi", "H", null, null, null, null, false, null);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(gatoLabrador, 2, "ltorres"));
        assertTrue(e.getMessage().contains("no corresponde a la especie"));
        Mascota sinRaza = Mascota.datos(null, maria, FELINO, null, "Michi", "H", null, null, null, null, false, null);
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(sinRaza, 2, "ltorres"));
    }

    @Test
    @DisplayName("RF-07: valida nombre obligatorio, sexo M/H, nacimiento no futuro y peso entre 0 y 150 kg")
    void validacionesBasicas() {
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(
                Mascota.datos(null, maria, FELINO, SIAMES, "  ", "H", null, null, null, null, false, null), 2, "ltorres"));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(
                Mascota.datos(null, maria, FELINO, SIAMES, "Misha", "X", null, null, null, null, false, null), 2, "ltorres"));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(
                Mascota.datos(null, maria, FELINO, SIAMES, "Misha", "H", HOY.plusDays(1), null, null, null, false, null),
                2, "ltorres"));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(
                Mascota.datos(null, maria, FELINO, SIAMES, "Misha", "H", null, null, BigDecimal.ZERO, null, false, null),
                2, "ltorres"));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(
                Mascota.datos(null, maria, FELINO, SIAMES, "Misha", "H", null, null, new BigDecimal("150.01"), null, false,
                        null), 2, "ltorres"));
        assertTrue(dao.mascotas.isEmpty());
    }

    @Test
    @DisplayName("RF-08: el microchip tiene 15 dígitos y no se repite entre mascotas")
    void microchip() throws Exception {
        registrarRocky();
        Mascota chipCorto = Mascota.datos(null, valentina, CANINO, LABRADOR, "Toby", "M", null, null, null, "12345", false, null);
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(chipCorto, 2, "ltorres"));
        Mascota chipRepetido = Mascota.datos(null, valentina, CANINO, LABRADOR, "Toby", "M", null, null, null,
                "985141000123456", false, null);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(chipRepetido, 2, "ltorres"));
        assertTrue(e.getMessage().contains("ya está registrado"));
    }

    @Test
    @DisplayName("RF-07: ficha única — el cliente no repite una mascota vigente con igual nombre y especie")
    void fichaUnica() throws Exception {
        registrarRocky();
        Mascota otroRocky = Mascota.datos(null, maria, CANINO, LABRADOR, "ROCKY", "M", null, null, null, null, false, null);
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(otroRocky, 2, "ltorres"));
        // otro cliente sí puede tener un "Rocky"
        servicio.registrar(Mascota.datos(null, valentina, CANINO, LABRADOR, "Rocky", "M", null, null, null, null, false, null),
                2, "ltorres");
        assertEquals(2, dao.mascotas.size());
    }

    @Test
    @DisplayName("RF-08: la edición actualiza datos e identificación sin cambiar el propietario")
    void edicion() throws Exception {
        int id = registrarRocky();
        servicio.actualizar(Mascota.datos(id, valentina, CANINO, LABRADOR, "Rocky", "M", LocalDate.of(2021, 3, 10),
                "Dorado", new BigDecimal("30"), "985141000999999", true, null), 2, "ltorres");
        Mascota m = dao.mascotas.get(id);
        assertEquals("985141000999999", m.microchip());
        assertEquals(maria, m.idCliente());
        assertNull(m.alertas());
    }

    @Test
    @DisplayName("RF-08: una mascota FALLECIDA no vuelve a ACTIVA ni a INACTIVA")
    void fallecidaEsDefinitiva() throws Exception {
        int id = registrarRocky();
        servicio.cambiarEstado(id, "inactiva", 2, "ltorres");
        assertEquals(Mascota.INACTIVA, dao.mascotas.get(id).estado());
        servicio.cambiarEstado(id, "FALLECIDA", 2, "ltorres");
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarEstado(id, "ACTIVA", 2, "ltorres"));
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarEstado(id, "INACTIVA", 2, "ltorres"));
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarEstado(id, "PERDIDA", 2, "ltorres"));
        assertEquals(Mascota.FALLECIDA, dao.mascotas.get(id).estado());
    }

    @Test
    @DisplayName("RN-02: no se reactiva una mascota si su propietario está inactivo")
    void reactivarConClienteInactivo() throws Exception {
        int id = registrarRocky();
        servicio.cambiarEstado(id, "INACTIVA", 2, "ltorres");
        Cliente c = clientes.clientes.get(maria);
        clientes.actualizar(new Cliente(maria, c.tipoDocumento(), c.numDocumento(), c.nombres(), c.apellidos(),
                c.telefono(), c.correo(), c.direccion(), c.distrito(), true, false, null, 0), 2);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarEstado(id, "ACTIVA", 2, "ltorres"));
        assertTrue(e.getMessage().contains("RN-02"));
    }

    @Test
    @DisplayName("RF-08: el cambio de propietario a un cliente activo y distinto queda en el historial con su motivo")
    void cambioDePropietario() throws Exception {
        int id = registrarRocky();
        servicio.cambiarPropietario(id, valentina, "Adopción formal acordada en la clínica", 2, "ltorres");
        assertEquals(valentina, dao.mascotas.get(id).idCliente());
        assertEquals(1, servicio.historialPropietarios(id).size());
        assertEquals(maria, servicio.historialPropietarios(id).get(0).idClienteAnterior());
        assertEquals("Adopción formal acordada en la clínica", servicio.historialPropietarios(id).get(0).motivo());
    }

    @Test
    @DisplayName("RF-08 / RN-02: rechaza el cambio al mismo cliente, a uno inactivo o con motivo de menos de 10 caracteres")
    void cambioDePropietarioInvalido() throws Exception {
        int id = registrarRocky();
        assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarPropietario(id, maria, "Motivo suficientemente largo", 2, "ltorres"));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarPropietario(id, jose, "Motivo suficientemente largo", 2, "ltorres"));
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarPropietario(id, valentina, "Venta", 2, "ltorres"));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarPropietario(id, null, "Motivo suficientemente largo", 2, "ltorres"));
        assertEquals(maria, dao.mascotas.get(id).idCliente());
        assertTrue(dao.historial.isEmpty());
    }

    @Test
    @DisplayName("RF-08: una mascota fallecida no se transfiere a otro propietario")
    void fallecidaNoSeTransfiere() throws Exception {
        int id = registrarRocky();
        servicio.cambiarEstado(id, "FALLECIDA", 2, "ltorres");
        assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarPropietario(id, valentina, "Motivo suficientemente largo", 2, "ltorres"));
    }

    @Test
    @DisplayName("RF-08: acepta una foto PNG real, guarda la ruta y elimina la foto anterior")
    void fotoValida() throws Exception {
        int id = registrarRocky();
        servicio.actualizarFoto(id, imagen("png"), "rocky.PNG", 2, "ltorres");
        String primera = dao.mascotas.get(id).fotoRuta();
        assertTrue(primera.endsWith(".png"));
        servicio.actualizarFoto(id, imagen("jpg"), "rocky nuevo.jpeg", 2, "ltorres");
        String segunda = dao.mascotas.get(id).fotoRuta();
        assertTrue(segunda.endsWith(".jpg"));
        assertTrue(fotos.eliminados.contains(primera));
        assertFalse(fotos.archivos.containsKey(primera));
        assertTrue(fotos.archivos.containsKey(segunda));
    }

    @Test
    @DisplayName("RF-08: rechaza fotos de otro formato, con contenido falso o de más de 2 MB")
    void fotoInvalida() throws Exception {
        int id = registrarRocky();
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarFoto(id, imagen("png"), "rocky.gif", 2, "x"));
        byte[] falso = "<script>alert(1)</script>".getBytes();
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarFoto(id, falso, "rocky.png", 2, "x"));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarFoto(id, imagen("png"), "rocky.jpg", 2, "x"));
        byte[] grande = Arrays.copyOf(imagen("png"), MascotaService.MAX_FOTO_BYTES + 1);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.actualizarFoto(id, grande, "rocky.png", 2, "x"));
        assertTrue(e.getMessage().contains("2 MB"));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarFoto(id, new byte[0], "rocky.png", 2, "x"));
        assertTrue(fotos.archivos.isEmpty());
        assertNull(dao.mascotas.get(id).fotoRuta());
    }

    @Test
    @DisplayName("Guava: especies y razas se leen una sola vez y luego salen de la caché")
    void catalogoEnCache() throws Exception {
        servicio.especies();
        servicio.especies();
        servicio.razas();
        registrarRocky();
        assertEquals(1, catalogo.consultasEspecies);
        assertEquals(1, catalogo.consultasRazas);
    }

    private static byte[] imagen(String formato) throws Exception {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, salida);
        return salida.toByteArray();
    }
}
