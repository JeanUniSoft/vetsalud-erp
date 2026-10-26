package pe.vetsalud.erp.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pe.vetsalud.erp.servicio.ReglaNegocioException;

/**
 * P03 · Ficha del cliente: datos completos (documento sin enmascarar, RNF-15 solo enmascara en listados),
 * sus mascotas con edad, especie, estado y alertas, y accesos a editar y registrar mascota (RF-06, RF-07).
 * Ruta: /clientes/ver?id=
 */
@WebServlet("/clientes/ver")
public class FichaClienteServlet extends ControladorBase {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        mostrar(req, res, "clientes/ficha", () -> {
            Integer id = MascotaModulo.idValido(texto(req, "id"));
            if (id == null) {
                throw new ReglaNegocioException("Falta el identificador del cliente o no es válido.");
            }
            req.setAttribute("cliente", MascotaModulo.clientes().obtener(id));
            req.setAttribute("mascotas", MascotaModulo.mascotas().delCliente(id));
        });
    }
}
