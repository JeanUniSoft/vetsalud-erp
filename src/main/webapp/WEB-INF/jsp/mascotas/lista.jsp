<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Clientes y mascotas"/><c:set var="seccion" value="clientes"/><c:set var="cssExtra" value="modulo-mascotas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P03 · Búsqueda de mascotas por nombre, microchip o propietario (RF-07, RF-08)</p>
<nav class="pestanas" aria-label="Clientes y mascotas">
  <a href="${ctx}/clientes">Clientes</a>
  <a class="activo" href="${ctx}/mascotas">Mascotas</a>
</nav>
<section class="tarjeta">
  <form method="get" action="${ctx}/mascotas" class="barra-herramientas">
    <div class="campo crece">
      <label for="q">Buscar mascota</label>
      <input id="q" name="q" type="search" value="<c:out value='${q}'/>" placeholder="Nombre, microchip, DNI o nombre del propietario" autofocus>
    </div>
    <button type="submit" class="btn btn-secundario">Buscar</button>
    <c:if test="${sesion.puede('CLIENTE_GESTIONAR')}">
      <a class="btn btn-primario" href="${ctx}/clientes">+ Mascota (elegir propietario)</a>
    </c:if>
  </form>
  <c:choose>
    <c:when test="${empty mascotas}">
      <p class="vacio">No se encontraron mascotas<c:if test="${not empty q}"> para «<c:out value="${q}"/>»</c:if>.</p>
    </c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Mascota</th><th>Especie / raza</th><th>Edad</th><th>Propietario</th><th>Estado</th><th>Alertas</th><th></th></tr></thead>
        <tbody>
        <c:forEach var="m" items="${mascotas}">
          <tr>
            <td><strong><c:out value="${m.nombre}"/></strong> <small>(<c:out value="${m.sexoTexto()}"/>)</small><c:if test="${not empty m.microchip}"><br><small>Chip <c:out value="${m.microchip}"/></small></c:if></td>
            <td><c:out value="${m.especie}"/><br><small><c:out value="${m.raza}"/></small></td>
            <td><c:out value="${vs:edad(m.fechaNacimiento)}"/></td>
            <td><a href="${ctx}/clientes/ver?id=${m.idCliente}"><c:out value="${m.propietario}"/></a></td>
            <td><span class="${vs:claseEstado(m.estado)}"><c:out value="${vs:etiqueta(m.estado)}"/></span></td>
            <td><c:if test="${m.tieneAlertas()}"><div class="nota-clinica"><strong>⚠</strong> <c:out value="${m.alertas}"/></div></c:if></td>
            <td class="acciones"><a class="btn btn-secundario btn-chico" href="${ctx}/mascotas/ver?id=${m.idMascota}">Ver ficha</a></td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
      <p class="ayuda">Se muestran hasta 50 resultados. Para registrar una mascota, abra la ficha de su propietario y use «+ Mascota».</p>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
