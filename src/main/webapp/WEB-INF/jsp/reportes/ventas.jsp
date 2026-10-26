<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Reportes"/><c:set var="seccion" value="reportes"/><c:set var="cssExtra" value="modulo-reportes"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P10 · Ventas por periodo, servicio, producto, cajero y medio de pago (RF-33, RF-36)</p>
<c:set var="pestanaReporte" value="ventas"/><c:set var="tipoReporte" value="ventas"/>
<%@ include file="/WEB-INF/jsp/reportes/_navegacion.jspf" %>

<section class="tarjeta no-imprimir">
  <form method="get" action="${pageContext.request.contextPath}/reportes/ventas" class="filtros-reporte">
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
  <p class="leyenda-reporte">Periodo del <c:out value="${vs:fecha(filtro.desde)}"/> al <c:out value="${vs:fecha(filtro.hasta)}"/> · solo ventas confirmadas</p>
  <c:set var="g" value="${tabla.grafico}"/><%@ include file="/WEB-INF/jsp/reportes/_grafico.jspf" %>
  <c:set var="t" value="${tabla}"/><%@ include file="/WEB-INF/jsp/reportes/_tabla.jspf" %>
  <%@ include file="/WEB-INF/jsp/reportes/_notas.jspf" %>
</section>

<div class="rejilla rejilla-2">
  <c:forEach var="anexo" items="${tabla.anexos}">
    <section class="tarjeta">
      <h2><c:out value="${anexo.titulo}"/></h2>
      <c:set var="t" value="${anexo}"/><%@ include file="/WEB-INF/jsp/reportes/_tabla.jspf" %>
    </section>
  </c:forEach>
</div>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
