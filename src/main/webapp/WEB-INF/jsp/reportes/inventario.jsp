<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Reportes"/><c:set var="seccion" value="reportes"/><c:set var="cssExtra" value="modulo-reportes"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P10 · Stock, kardex, vencimientos y rotación por producto y lote (RF-34, RF-36)</p>
<c:set var="pestanaReporte" value="inventario"/><c:set var="tipoReporte" value="inventario"/>
<%@ include file="/WEB-INF/jsp/reportes/_navegacion.jspf" %>

<section class="tarjeta no-imprimir">
  <nav class="pestanas" aria-label="Vistas del reporte de inventario">
    <c:forEach var="vi" items="${vistas}">
      <c:url var="urlVista" value="/reportes/inventario">
        <c:param name="vista" value="${vi.key}"/><c:param name="desde" value="${filtro.desde}"/>
        <c:param name="hasta" value="${filtro.hasta}"/><c:param name="dias" value="${filtro.dias}"/>
      </c:url>
      <a class="${filtro.vista == vi.key ? 'activo' : ''}" href="<c:out value='${urlVista}'/>"><c:out value="${vi.value}"/></a>
    </c:forEach>
  </nav>
  <form method="get" action="${pageContext.request.contextPath}/reportes/inventario" class="filtros-reporte">
    <input type="hidden" name="vista" value="${filtro.vista}">
    <div class="campo"><label for="desde">Desde</label>
      <input id="desde" name="desde" type="date" value="${filtro.desde}" required></div>
    <div class="campo"><label for="hasta">Hasta</label>
      <input id="hasta" name="hasta" type="date" value="${filtro.hasta}" required></div>
    <c:if test="${filtro.vista == 'kardex'}">
      <div class="campo crece"><label for="producto">Producto (código o nombre)</label>
        <input id="producto" name="producto" type="search" maxlength="60" value="<c:out value='${filtro.producto}'/>" placeholder="Ej.: MED-005 o NexGard"></div>
    </c:if>
    <c:if test="${filtro.vista == 'stock' or filtro.vista == 'vencimientos'}">
      <div class="campo"><label for="dias">Días de alerta de vencimiento</label>
        <input id="dias" name="dias" type="number" min="1" max="365" value="${filtro.dias}" required></div>
    </c:if>
    <button type="submit" class="btn btn-primario">Aplicar filtros</button>
    <span class="ayuda">El periodo se aplica al kardex, a la rotación y a las unidades vendidas; stock y vencimientos son al corte de hoy.</span>
  </form>
</section>

<%@ include file="/WEB-INF/jsp/reportes/_kpis.jspf" %>

<section class="tarjeta mt18">
  <div class="cabecera-tarjeta">
    <h2><c:out value="${tabla.titulo}"/></h2>
    <%@ include file="/WEB-INF/jsp/reportes/_exportar.jspf" %>
  </div>
  <p class="leyenda-reporte">
    <c:forEach var="fl" items="${tabla.filtros}" varStatus="ifl"><c:out value="${fl.key}"/>: <c:out value="${fl.value}"/><c:if test="${not ifl.last}"> · </c:if></c:forEach>
  </p>
  <c:if test="${filtro.vista == 'rotacion'}">
    <div class="formula" role="note">
      <p><strong>Cómo se calcula:</strong> <c:out value="${formulaRotacion}"/></p>
      <p><c:out value="${formulaCobertura}"/></p>
    </div>
  </c:if>
  <c:set var="g" value="${tabla.grafico}"/><%@ include file="/WEB-INF/jsp/reportes/_grafico.jspf" %>
  <c:set var="t" value="${tabla}"/><%@ include file="/WEB-INF/jsp/reportes/_tabla.jspf" %>
  <%@ include file="/WEB-INF/jsp/reportes/_notas.jspf" %>
</section>

<c:if test="${sesion.puede('REPORTE_EXPORTAR')}">
  <section class="tarjeta no-imprimir">
    <div class="cabecera-tarjeta">
      <div>
        <h2>Libro Excel de inventario y ventas (S10)</h2>
        <p class="ayuda">Resumen con fórmulas, stock por lote con formato condicional y ventas por medio de pago, generado con Apache POI.</p>
      </div>
      <c:url var="urlS10" value="/reportes/exportar">
        <c:param name="tipo" value="s10"/><c:param name="desde" value="${filtro.desde}"/>
        <c:param name="hasta" value="${filtro.hasta}"/><c:param name="dias" value="${filtro.dias}"/>
      </c:url>
      <a class="btn btn-secundario btn-chico" href="<c:out value='${urlS10}'/>">Descargar libro .xlsx</a>
    </div>
  </section>
</c:if>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
