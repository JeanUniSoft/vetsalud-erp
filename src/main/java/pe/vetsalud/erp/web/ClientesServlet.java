package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.dao.jdbc.ClienteDaoJdbc;
import pe.vetsalud.erp.modelo.Cliente;
import pe.vetsalud.erp.persistencia.Db;
import pe.vetsalud.erp.seguridad.UsuarioSesion;
import pe.vetsalud.erp.servicio.ClienteService;

/**
 * P03 · Clientes: búsqueda, registro y edición (RF-05, RF-06).
 * Rutas: /clientes (lista y búsqueda) · /clientes/nuevo · /clientes/editar?id=
 */
@WebServlet({"/clientes", "/clientes/nuevo", "/clientes/editar"})
public class ClientesServlet extends ControladorBase {

    private ClienteService servicio;

    @Override
    public void init() {
        servicio = new ClienteService(new ClienteDaoJdbc(Db.get()));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        switch (req.getServletPath()) {
            case "/clientes/nuevo" -> mostrar(req, res, "clientes/formulario", () -> req.setAttribute("cliente", null));
            case "/clientes/editar" -> mostrar(req, res, "clientes/formulario",
                    () -> req.setAttribute("cliente", servicio.obtener(entero(req, "id"))));
            default -> mostrar(req, res, "clientes/lista", () -> {
                req.setAttribute("q", texto(req, "q"));
                req.setAttribute("clientes", servicio.buscar(texto(req, "q")));
            });
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        UsuarioSesion u = usuario(req);
        boolean edicion = "/clientes/editar".equals(req.getServletPath());
        Integer id = edicion ? entero(req, "id") : null;
        String volver = edicion ? "/clientes/editar?id=" + id : "/clientes/nuevo";
        int[] nuevoId = new int[1];
        procesar(req, res, edicion ? "/clientes/ver?id=" + id : "/clientes", volver,
                edicion ? "Datos del cliente actualizados." : "Cliente registrado correctamente.", () -> {
                    exigirPermiso(req, "CLIENTE_GESTIONAR");
                    Cliente datos = new Cliente(id, texto(req, "tipoDocumento"), texto(req, "numDocumento"),
                            texto(req, "nombres"), texto(req, "apellidos"), texto(req, "telefono"), texto(req, "correo"),
                            texto(req, "direccion"), texto(req, "distrito"), marcado(req, "aceptaRecordatorios"),
                            !edicion || marcado(req, "activo"), null, 0);
                    if (edicion) {
                        servicio.actualizar(datos, u.idUsuario(), u.usuario());
                    } else {
                        nuevoId[0] = servicio.registrar(datos, u.idUsuario(), u.usuario());
                    }
                });
    }
}
