<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Historia clínica"/><c:set var="seccion" value="historia"/><c:set var="cssExtra" value="modulo-consulta"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P05 · Búsqueda de pacientes y consultas pendientes de firma (RF-13, RF-18)</p>

<c:if test="${not empty abiertas}">
  <section class="tarjeta">
    <div class="cabecera-tarjeta">
      <h2>${sesion.rol == 'VETERINARIO' ? 'Mis consultas abiertas' : 'Consultas abiertas'}</h2>
      <span class="badge badge-aviso">${fn:length(abiertas)} sin firmar</span>
    </div>
    <div class="tabla-scroll"><table class="tabla">
      <thead><tr><th>Apertura</th><th>Paciente</th><th>Propietario</th><th>Veterinario</th><th>Motivo</th><th></th></tr></thead>
      <tbody>
      <c:forEach var="a" items="${abiertas}">
        <tr>
          <td><c:out value="${vs:fechaHora(a.fechaHora)}"/></td>
          <td><strong><c:out value="${a.mascota}"/></strong> <small>(<c:out value="${a.especie}"/>)</small></td>
          <td><c:out value="${a.cliente}"/></td>
          <td>M.V. <c:out value="${a.veterinario}"/></td>
          <td><c:out value="${a.motivo}"/></td>
          <td class="acciones"><a class="btn btn-primario btn-chico" href="${ctx}/consultas/ver?id=${a.idConsulta}">Continuar</a></td>
        </tr>
      </c:forEach>
      </tbody>
    </table></div>
  </section>
</c:if>

<section class="tarjeta">
  <form method="get" action="${ctx}/historia" class="barra-herramientas">
    <div class="campo crece">
      <label for="q">Buscar paciente</label>
      <input id="q" name="q" type="search" maxlength="80" value="<c:out value='${q}'/>"
             placeholder="Nombre de la mascota, documento o nombre del propietario, microchip" autofocus>
    </div>
    <button type="submit" class="btn btn-secundario">Buscar</button>
  </form>
  <h2>${empty q ? 'Pacientes atendidos recientemente' : 'Resultados de la búsqueda'}</h2>
  <c:choose>
    <c:when test="${empty resultados}">
      <p class="vacio">No se encontraron pacientes<c:if test="${not empty q}"> para «<c:out value="${q}"/>»</c:if>.</p>
    </c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Paciente</th><th>Propietario</th><th>Alertas clínicas</th><th>Última consulta</th><th>Estado</th><th></th></tr></thead>
        <tbody>
        <c:forEach var="m" items="${resultados}">
          <tr>
            <td><strong><c:out value="${m.nombre}"/></strong><br>
              <small><c:out value="${m.especie}"/> · <c:out value="${m.raza}"/> · <c:out value="${m.sexoTexto()}"/> · <c:out value="${vs:edad(m.fechaNacimiento)}"/></small></td>
            <td><c:out value="${m.cliente}"/><br><small><c:out value="${m.tipoDocumento}"/> <c:out value="${vs:enmascarar(m.numDocumento)}"/></small></td>
            <td><c:if test="${not empty m.alertas}"><span class="alerta-mini">⚠ <c:out value="${m.alertas}"/></span></c:if></td>
            <td><c:out value="${empty m.ultimaConsulta ? 'Sin consultas' : vs:fecha(m.ultimaConsulta)}"/></td>
            <td><span class="${vs:claseEstado(m.estado)}"><c:out value="${vs:etiqueta(m.estado)}"/></span></td>
            <td class="acciones"><a class="btn btn-secundario btn-chico" href="${ctx}/historia/mascota?id=${m.idMascota}">Ver historia</a></td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
      <p class="ayuda">Se muestran hasta 30 pacientes. El documento se enmascara en los listados (RNF-15).</p>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
