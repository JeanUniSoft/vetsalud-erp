<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Mis mascotas"/><c:set var="seccion" value="portal"/><c:set var="cssExtra" value="modulo-mascotas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P11 · Ficha básica de la mascota, citas y controles (RF-09, RF-19)</p>
<section class="tarjeta">
  <div class="cabecera-tarjeta">
    <h2 class="titulo-mascota"><c:out value="${mascota.nombre}"/>
      <span class="${vs:claseEstado(mascota.estado)}"><c:out value="${vs:etiqueta(mascota.estado)}"/></span></h2>
    <a class="btn btn-secundario" href="${ctx}/portal">← Mis mascotas</a>
  </div>
  <div class="ficha-mascota">
    <div class="foto-col">
      <c:choose>
        <c:when test="${mascota.tieneFoto()}">
          <img class="foto-mascota" src="${ctx}/portal/foto?id=${mascota.idMascota}" width="200" height="200" alt="Fotografía de <c:out value='${mascota.nombre}'/>">
        </c:when>
        <c:otherwise>
          <div class="foto-vacia" role="img" aria-label="Sin fotografía"><span class="inicial-mascota"><c:out value="${mascota.inicial()}"/></span><small>Sin fotografía</small></div>
        </c:otherwise>
      </c:choose>
    </div>
    <%-- Información autorizada para el cliente: datos de identificación; las alertas clínicas internas no se publican (UC-03) --%>
    <dl class="ficha">
      <dt>Especie / raza</dt><dd><c:out value="${mascota.especie}"/> · <c:out value="${mascota.raza}"/></dd>
      <dt>Sexo</dt><dd><c:out value="${mascota.sexoTexto()}"/>${mascota.esterilizado ? ' · esterilizado(a)' : ''}</dd>
      <dt>Nacimiento</dt><dd><c:choose><c:when test="${empty mascota.fechaNacimiento}">No registrado</c:when>
        <c:otherwise><c:out value="${vs:fecha(mascota.fechaNacimiento)}"/> (<c:out value="${vs:edad(mascota.fechaNacimiento)}"/>)</c:otherwise></c:choose></dd>
      <dt>Color</dt><dd><c:out value="${empty mascota.color ? '—' : mascota.color}"/></dd>
      <dt>Peso referencial</dt><dd><c:choose><c:when test="${empty mascota.pesoKg}">—</c:when><c:otherwise><c:out value="${vs:numero(mascota.pesoKg)}"/> kg</c:otherwise></c:choose></dd>
      <dt>Microchip</dt><dd><c:out value="${empty mascota.microchip ? 'Sin microchip registrado' : mascota.microchip}"/></dd>
      <dt>Ficha n.°</dt><dd><c:out value="${mascota.idMascota}"/></dd>
    </dl>
  </div>
  <p class="privacidad">¿Algún dato no es correcto? Avísenos en recepción para actualizarlo.</p>
</section>

<div class="rejilla rejilla-2">
  <section class="tarjeta">
    <h2>Próximas citas de <c:out value="${mascota.nombre}"/></h2>
    <c:choose>
      <c:when test="${empty citas}"><p class="vacio">Sin citas programadas desde hoy.</p></c:when>
      <c:otherwise>
        <div class="tabla-scroll"><table class="tabla">
          <thead><tr><th>Fecha</th><th>Hora</th><th>Servicio</th><th>Profesional</th><th>Estado</th></tr></thead>
          <tbody>
          <c:forEach var="ci" items="${citas}">
            <tr>
              <td><c:out value="${vs:fecha(ci.inicio)}"/></td>
              <td><strong><c:out value="${vs:hora(ci.inicio)}"/></strong></td>
              <td><c:out value="${ci.servicio}"/></td>
              <td><c:out value="${ci.profesional}"/></td>
              <td><span class="${vs:claseEstado(ci.estado)}"><c:out value="${vs:etiqueta(ci.estado)}"/></span></td>
            </tr>
          </c:forEach>
          </tbody>
        </table></div>
      </c:otherwise>
    </c:choose>
  </section>
  <section class="tarjeta">
    <h2>Controles programados</h2>
    <c:choose>
      <c:when test="${empty controles}"><p class="vacio">No hay controles pendientes.</p></c:when>
      <c:otherwise>
        <ul class="linea-tiempo">
          <c:forEach var="co" items="${controles}">
            <li><strong><c:out value="${vs:fecha(co.fechaControl)}"/></strong><br><small>Indicado por <c:out value="${co.profesional}"/></small></li>
          </c:forEach>
        </ul>
      </c:otherwise>
    </c:choose>
  </section>
</div>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
