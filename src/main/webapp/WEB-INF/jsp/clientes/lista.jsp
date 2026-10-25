<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Clientes y mascotas"/><c:set var="seccion" value="clientes"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P03 · Búsqueda por documento, nombre, teléfono o correo (RF-06)</p>
<section class="tarjeta">
  <form method="get" action="${pageContext.request.contextPath}/clientes" class="barra-herramientas">
    <div class="campo crece">
      <label for="q">Buscar cliente</label>
      <input id="q" name="q" type="search" value="<c:out value='${q}'/>" placeholder="DNI, nombre, teléfono o correo" autofocus>
    </div>
    <button type="submit" class="btn btn-secundario">Buscar</button>
    <c:if test="${sesion.puede('CLIENTE_GESTIONAR')}">
      <a class="btn btn-primario" href="${pageContext.request.contextPath}/clientes/nuevo">+ Nuevo cliente</a>
    </c:if>
  </form>
  <c:choose>
    <c:when test="${empty clientes}">
      <p class="vacio">No se encontraron clientes<c:if test="${not empty q}"> para «<c:out value="${q}"/>»</c:if>.</p>
    </c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Cliente</th><th>Documento</th><th>Teléfono</th><th>Distrito</th><th class="num">Mascotas</th><th>Estado</th><th></th></tr></thead>
        <tbody>
        <c:forEach var="c" items="${clientes}">
          <tr>
            <td><strong><c:out value="${c.nombreCompleto()}"/></strong><br><small><c:out value="${c.correo}"/></small></td>
            <%-- RNF-15: en listados el documento se enmascara; completo solo en la ficha --%>
            <td><c:out value="${c.tipoDocumento}"/> <c:out value="${vs:enmascarar(c.numDocumento)}"/></td>
            <td><c:out value="${c.telefono}"/></td>
            <td><c:out value="${c.distrito}"/></td>
            <td class="num"><c:out value="${c.numMascotas}"/></td>
            <td><span class="${c.activo ? 'badge badge-ok' : 'badge badge-error'}">${c.activo ? 'Activo' : 'Inactivo'}</span></td>
            <td class="acciones"><a class="btn btn-secundario btn-chico" href="${pageContext.request.contextPath}/clientes/ver?id=${c.idCliente}">Ver ficha</a></td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
      <p class="ayuda">Se muestran hasta 50 resultados. Refine la búsqueda para encontrar más.</p>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
