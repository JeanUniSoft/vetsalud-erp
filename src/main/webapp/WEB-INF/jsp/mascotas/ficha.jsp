<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Ficha de la mascota"/><c:set var="seccion" value="clientes"/><c:set var="cssExtra" value="modulo-mascotas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P03 · Ficha de la mascota: estado, fotografía, identificación e historial de propietarios (RF-07, RF-08)</p>
<c:choose>
  <c:when test="${empty mascota}">
    <section class="tarjeta"><p class="vacio">No se encontró la mascota solicitada.</p>
      <a class="btn btn-secundario" href="${ctx}/mascotas">Buscar mascotas</a></section>
  </c:when>
  <c:otherwise>
    <c:set var="gestiona" value="${sesion.puede('CLIENTE_GESTIONAR')}"/>
    <section class="tarjeta">
      <div class="cabecera-tarjeta">
        <h2 class="titulo-mascota"><c:out value="${mascota.nombre}"/>
          <span class="${vs:claseEstado(mascota.estado)}"><c:out value="${vs:etiqueta(mascota.estado)}"/></span></h2>
        <div class="acciones-form no-imprimir">
          <a class="btn btn-secundario" href="${ctx}/clientes/ver?id=${mascota.idCliente}">← Ficha del propietario</a>
          <c:if test="${gestiona}">
            <a class="btn btn-secundario" href="${ctx}/mascotas/editar?id=${mascota.idMascota}">Editar datos</a>
            <c:if test="${not mascota.fallecida()}">
              <a class="btn btn-secundario" href="${ctx}/mascotas/propietario?id=${mascota.idMascota}">Cambiar propietario</a>
            </c:if>
          </c:if>
          <button type="button" class="btn btn-secundario" data-imprimir>Imprimir</button>
        </div>
      </div>

      <div class="ficha-mascota">
        <div class="foto-col">
          <c:choose>
            <c:when test="${mascota.tieneFoto()}">
              <img class="foto-mascota" src="${ctx}/mascotas/foto?id=${mascota.idMascota}" width="200" height="200"
                   alt="Fotografía de <c:out value='${mascota.nombre}'/>">
            </c:when>
            <c:otherwise>
              <div class="foto-vacia" role="img" aria-label="Sin fotografía">
                <span class="inicial-mascota"><c:out value="${mascota.inicial()}"/></span><small>Sin fotografía</small>
              </div>
            </c:otherwise>
          </c:choose>
          <c:if test="${gestiona}">
            <%-- multipart: el token CSRF va también en la URL porque el filtro lo lee antes de procesar el archivo --%>
            <form method="post" enctype="multipart/form-data" class="form-foto no-imprimir"
                  action="${ctx}/mascotas/foto?id=${mascota.idMascota}&amp;_csrf=${sessionScope.csrf}">
              <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
              <label for="foto">${mascota.tieneFoto() ? 'Reemplazar fotografía' : 'Subir fotografía'}</label>
              <input id="foto" name="foto" type="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png" required>
              <span class="ayuda">JPG o PNG, máximo 2 MB.</span>
              <button type="submit" class="btn btn-secundario btn-chico">Guardar foto</button>
            </form>
          </c:if>
        </div>
        <div>
          <dl class="ficha">
            <dt>Propietario</dt><dd><a href="${ctx}/clientes/ver?id=${mascota.idCliente}"><c:out value="${mascota.propietario}"/></a></dd>
            <dt>Especie / raza</dt><dd><c:out value="${mascota.especie}"/> · <c:out value="${mascota.raza}"/></dd>
            <dt>Sexo</dt><dd><c:out value="${mascota.sexoTexto()}"/>${mascota.esterilizado ? ' · esterilizado(a)' : ''}</dd>
            <dt>Nacimiento</dt><dd><c:choose><c:when test="${empty mascota.fechaNacimiento}">No registrado</c:when>
              <c:otherwise><c:out value="${vs:fecha(mascota.fechaNacimiento)}"/> (<c:out value="${vs:edad(mascota.fechaNacimiento)}"/>)</c:otherwise></c:choose></dd>
            <dt>Color</dt><dd><c:out value="${empty mascota.color ? '—' : mascota.color}"/></dd>
            <dt>Peso referencial</dt><dd><c:choose><c:when test="${empty mascota.pesoKg}">—</c:when><c:otherwise><c:out value="${vs:numero(mascota.pesoKg)}"/> kg</c:otherwise></c:choose></dd>
            <dt>Microchip</dt><dd><c:out value="${empty mascota.microchip ? 'Sin microchip registrado' : mascota.microchip}"/></dd>
            <dt>Ficha n.°</dt><dd><c:out value="${mascota.idMascota}"/> · registrada el <c:out value="${vs:fechaHora(mascota.creadoEn)}"/></dd>
          </dl>
          <c:choose>
            <c:when test="${mascota.tieneAlertas()}">
              <div class="nota-clinica mt18" role="note"><strong>⚠ Alertas clínicas:</strong> <c:out value="${mascota.alertas}"/></div>
            </c:when>
            <c:otherwise><p class="ayuda mt18">Sin alertas clínicas registradas.</p></c:otherwise>
          </c:choose>
        </div>
      </div>
    </section>

    <c:if test="${gestiona}">
      <section class="tarjeta no-imprimir">
        <h2>Estado de la mascota</h2>
        <c:choose>
          <c:when test="${mascota.fallecida()}">
            <p class="aviso aviso-aviso">Registrada como fallecida. Este estado es definitivo: no puede volver a activa ni a inactiva.</p>
          </c:when>
          <c:otherwise>
            <form method="post" action="${ctx}/mascotas/estado" class="estado-form"
                  data-confirmar="¿Confirma el cambio de estado? Si elige «Fallecida», el cambio no podrá revertirse.">
              <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
              <input type="hidden" name="id" value="${mascota.idMascota}">
              <div class="campo"><label for="estado">Nuevo estado</label>
                <select id="estado" name="estado" required>
                  <c:forEach var="e" items="${['ACTIVA','INACTIVA','FALLECIDA']}">
                    <c:if test="${e != mascota.estado}"><option value="${e}"><c:out value="${vs:etiqueta(e)}"/></option></c:if>
                  </c:forEach>
                </select></div>
              <button type="submit" class="btn btn-secundario">Actualizar estado</button>
            </form>
            <p class="ayuda">Activa ⇄ Inactiva en ambos sentidos (para activar, el propietario debe estar activo, RN-02). Fallecida es definitivo.</p>
          </c:otherwise>
        </c:choose>
      </section>
    </c:if>

    <section class="tarjeta">
      <h2>Historial de propietarios</h2>
      <c:choose>
        <c:when test="${empty propietarios}">
          <p class="vacio">Sin cambios de propietario: la mascota sigue con el propietario con el que fue registrada.</p>
        </c:when>
        <c:otherwise>
          <div class="tabla-scroll"><table class="tabla">
            <thead><tr><th>Fecha</th><th>Propietario anterior</th><th>Nuevo propietario</th><th>Motivo</th><th>Registrado por</th></tr></thead>
            <tbody>
            <c:forEach var="h" items="${propietarios}">
              <tr>
                <td><c:out value="${vs:fechaHora(h.fechaHora)}"/></td>
                <td><a href="${ctx}/clientes/ver?id=${h.idClienteAnterior}"><c:out value="${h.clienteAnterior}"/></a></td>
                <td><a href="${ctx}/clientes/ver?id=${h.idClienteNuevo}"><c:out value="${h.clienteNuevo}"/></a></td>
                <td><c:out value="${h.motivo}"/></td>
                <td><c:out value="${h.usuario}"/></td>
              </tr>
            </c:forEach>
            </tbody>
          </table></div>
        </c:otherwise>
      </c:choose>
    </section>

    <section class="tarjeta">
      <h2>Historial de cambios de la ficha</h2>
      <c:choose>
        <c:when test="${empty cambios}"><p class="vacio">No hay cambios registrados en la auditoría.</p></c:when>
        <c:otherwise>
          <div class="tabla-scroll"><table class="tabla">
            <thead><tr><th>Fecha</th><th>Usuario</th><th>Operación</th><th>Detalle</th></tr></thead>
            <tbody>
            <c:forEach var="cambio" items="${cambios}">
              <tr>
                <td><c:out value="${vs:fechaHora(cambio.fechaHora)}"/></td>
                <td><c:out value="${cambio.usuario}"/></td>
                <td><c:out value="${cambio.operacionTexto()}"/></td>
                <td><c:choose>
                  <c:when test="${empty cambio.campos}"><small class="suave">Alta de la ficha</small></c:when>
                  <c:otherwise><ul class="lista-cambios">
                    <c:forEach var="campo" items="${cambio.campos}"><li><c:out value="${campo.descripcion()}"/></li></c:forEach>
                  </ul></c:otherwise>
                </c:choose></td>
              </tr>
            </c:forEach>
            </tbody>
          </table></div>
          <p class="ayuda">Fuente: bitácora de auditoría de la base de datos (trigger de la tabla mascota, RF-04). Últimos 20 registros.</p>
        </c:otherwise>
      </c:choose>
    </section>
  </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
