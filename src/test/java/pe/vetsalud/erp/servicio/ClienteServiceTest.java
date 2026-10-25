package pe.vetsalud.erp.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.vetsalud.erp.fakes.ClienteDaoEnMemoria;
import pe.vetsalud.erp.modelo.Cliente;

/** RF-05 / RF-06 / RN-01: registro y validación de clientes. */
class ClienteServiceTest {

    private ClienteDaoEnMemoria dao;
    private ClienteService servicio;

    @BeforeEach
    void preparar() {
        dao = new ClienteDaoEnMemoria();
        servicio = new ClienteService(dao);
    }

    private static Cliente dni(String numero) {
        return Cliente.nuevo("DNI", numero, "  maría elena ", "gonzales díaz", "987 654 321", "Maria@Gmail.com",
                "Av. Primavera 120", "Surco", true);
    }

    @Test
    @DisplayName("Registra un cliente y normaliza nombres, teléfono y correo")
    void registroNormalizado() throws Exception {
        int id = servicio.registrar(dni("45678912"), 2, "ltorres");
        Cliente c = dao.clientes.get(id);
        assertEquals("María Elena", c.nombres());
        assertEquals("Gonzales Díaz", c.apellidos());
        assertEquals("987654321", c.telefono());
        assertEquals("maria@gmail.com", c.correo());
    }

    @Test
    @DisplayName("RN-01: no permite dos clientes con el mismo tipo y número de documento")
    void documentoUnico() throws Exception {
        servicio.registrar(dni("45678912"), 2, "ltorres");
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(dni("45678912"), 2, "ltorres"));
        assertTrue(e.getMessage().contains("RN-01"));
    }

    @Test
    @DisplayName("RF-05: valida DNI de 8 dígitos y RUC 10/20 de 11 dígitos")
    void formatoDeDocumento() {
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(dni("1234"), 2, "ltorres"));
        Cliente rucMalo = Cliente.nuevo("RUC", "30123456789", "Empresa SAC", null, "014567890", null, null, null, false);
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(rucMalo, 2, "ltorres"));
    }

    @Test
    @DisplayName("RUC: la razón social no necesita apellidos ni se capitaliza")
    void personaJuridica() throws Exception {
        int id = servicio.registrar(Cliente.nuevo("RUC", "20456789123", "Refugio Patitas Felices S.A.C.", null,
                "014456789", null, null, null, false), 2, "ltorres");
        assertEquals("Refugio Patitas Felices S.A.C.", dao.clientes.get(id).nombres());
    }

    @Test
    @DisplayName("RF-37: los recordatorios requieren correo")
    void recordatoriosSinCorreo() {
        Cliente sinCorreo = Cliente.nuevo("DNI", "70991234", "Camila", "Vargas", "945667788", null, null, null, true);
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar(sinCorreo, 2, "ltorres"));
    }

    @Test
    @DisplayName("RF-06: al editar, el documento solo choca con OTROS clientes")
    void edicion() throws Exception {
        int id = servicio.registrar(dni("45678912"), 2, "ltorres");
        Cliente editado = dao.clientes.get(id);
        servicio.actualizar(new Cliente(id, "DNI", "45678912", "María", "Gonzales", "999111222", null, null, "Lince",
                false, true, null, 0), 2, "ltorres");
        assertEquals("Lince", dao.clientes.get(id).distrito());
        assertEquals(editado.idCliente(), dao.clientes.get(id).idCliente());
    }
}
