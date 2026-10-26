<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Reportes"/><c:set var="seccion" value="reportes"/><c:set var="cssExtra" value="modulo-reportes"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P10 · Citas, ausencias, atenciones y procedimientos por profesional, servicio y periodo (RF-35, RF-36)</p>
<c:set var="pestanaReporte" value="clinica"/><c:set var="tipoReporte" value="clinica"/>
<%@ include file="/WEB-INF/jsp/reportes/_navegacion.jspf" %>

<section class="tarjeta no-imprimir">
  <nav class="pestanas" aria-label="Vistas del reporte clínico">
    <c:forEach var="vi" items="${vistas}">
      <c:url var="urlVista" value="/reportes/clinica">
        <c:param name="vista" value="${vi.key}"/><c:param name="agrupacion" value="${filtro.agrupacion}"/>
        <c:param name="desde" value="${filtro.desde}"/><c:param name="hasta" value="${filtro.hasta}"/>
      </c:url>
      <a class="${filtro.vista == vi.key ? 'activo' : ''}" href="<c:out value='${urlVista}'/>"><c:out value="${vi.value}"/></a>
    </c:forEach>
  </nav>
  <form method="get" action="${pageContext.request.contextPath}/reportes/clinica" class="filtros-reporte">
    <input type="hidden" name="vista" value="${filtro.vista}">
    <div class="campo"><label for="desde">Desde</label>
      <input id="desde" name="desde" type="date" value="${filtro.desde}" required></div>
    <div class="campo"><label for="hasta">Hasta</label>
      <input id="hasta" name="hasta" type="date" value="${filtro.hasta}" required></div>
    <div class="campo"><label for="agrupacion">Agrupar por</label>
      <select id="agrupacion" name="agrupacion">
        <c:forEach var="a" items="${agrupaciones}">
          <option value="${a.key}" ${filtro.agrupacion == a.key ? 'selected' : ''}><c:out value="${a.value}"/></option>
        </c:forEach>
      </select></div>
    <button type="submit" class="btn btn-primario">Aplicar filtros</button>
  </form>
</section>

<%@ include file="/WEB-INF/jsp/reportes/_kpis.jspf" %>

<section class="tarjeta mt18">
  <div class="cabecera-tarjeta">
    <h2><c:out value="${tabla.titulo}"/></h2>
    <%@ include file="/WEB-INF/jsp/reportes/_exportar.jspf" %>
  </div>
  <p class="leyenda-reporte">Periodo del <c:out value="${vs:fecha(filtro.desde)}"/> al <c:out value="${vs:fecha(filtro.hasta)}"/> · fecha de la cita o de la consulta</p>
  <c:set var="g" value="${tabla.grafico}"/><%@ include file="/WEB-INF/jsp/reportes/_grafico.jspf" %>
  <c:set var="t" value="${tabla}"/><%@ include file="/WEB-INF/jsp/reportes/_tabla.jspf" %>
  <%@ include file="/WEB-INF/jsp/reportes/_notas.jspf" %>
</section>

<c:forEach var="anexo" items="${tabla.anexos}">
  <section class="tarjeta">
    <h2><c:out value="${anexo.titulo}"/></h2>
    <c:set var="t" value="${anexo}"/><%@ include file="/WEB-INF/jsp/reportes/_tabla.jspf" %>
  </section>
</c:forEach>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
