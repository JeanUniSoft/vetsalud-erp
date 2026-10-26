<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Reportes"/><c:set var="seccion" value="reportes"/><c:set var="cssExtra" value="modulo-reportes"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P10 · Indicadores del periodo y reportes disponibles (RF-32 a RF-36)</p>
<c:set var="pestanaReporte" value="indice"/>
<%@ include file="/WEB-INF/jsp/reportes/_navegacion.jspf" %>

<section class="tarjeta no-imprimir">
  <form method="get" action="${pageContext.request.contextPath}/reportes" class="filtros-reporte">
    <div class="campo"><label for="desde">Desde</label>
      <input id="desde" name="desde" type="date" value="${filtro.desde}" required></div>
    <div class="campo"><label for="hasta">Hasta</label>
      <input id="hasta" name="hasta" type="date" value="${filtro.hasta}" required></div>
    <button type="submit" class="btn btn-primario">Aplicar periodo</button>
    <span class="ayuda">Periodo de hasta 366 días. Los indicadores de inventario muestran el estado actual.</span>
  </form>
</section>

<%@ include file="/WEB-INF/jsp/reportes/_kpis.jspf" %>

<div class="rejilla rejilla-2 mt18">
  <section class="tarjeta tarjeta-reporte">
    <h2>Ventas</h2>
    <p>Ventas confirmadas por día, mes, servicio, producto, cajero y medio de pago. Las anuladas se muestran aparte y no suman (RF-33).</p>
    <ul><li>Conciliación servicios + productos − descuentos = total</li><li>Gráfico de barras y totales por grupo</li></ul>
    <div class="acciones-exportar">
      <a class="btn btn-primario btn-chico" href="${pageContext.request.contextPath}/reportes/ventas?desde=${filtro.desde}&amp;hasta=${filtro.hasta}">Abrir reporte de ventas</a>
    </div>
  </section>
  <section class="tarjeta tarjeta-reporte">
    <h2>Inventario</h2>
    <p>Stock por lote con su condición, kardex de movimientos, vencimientos próximos y rotación de productos (RF-34).</p>
    <ul><li>Lee las vistas con el rol de solo lectura vetsalud_reportes</li><li>Fórmula de rotación documentada en pantalla</li></ul>
    <div class="acciones-exportar">
      <a class="btn btn-primario btn-chico" href="${pageContext.request.contextPath}/reportes/inventario?desde=${filtro.desde}&amp;hasta=${filtro.hasta}">Abrir reporte de inventario</a>
    </div>
  </section>
  <section class="tarjeta tarjeta-reporte">
    <h2>Citas y atención clínica</h2>
    <p>Citas, ausencias (canceladas y no asistidas), consultas y procedimientos agrupados por profesional, servicio o periodo (RF-35).</p>
    <ul><li>Porcentaje de ausentismo por grupo</li><li>Detalle de cada ausencia con su motivo</li></ul>
    <div class="acciones-exportar">
      <a class="btn btn-primario btn-chico" href="${pageContext.request.contextPath}/reportes/clinica?desde=${filtro.desde}&amp;hasta=${filtro.hasta}">Abrir reporte clínico</a>
    </div>
  </section>
  <section class="tarjeta tarjeta-reporte s10">
    <h2>Libro Excel de inventario y ventas</h2>
    <p>Libro del S10 generado con Apache POI: resumen con fórmulas, stock y vencimientos por lote con formato condicional y ventas por medio de pago.</p>
    <ul><li>Periodo de ventas: <c:out value="${vs:fecha(filtro.desde)}"/> al <c:out value="${vs:fecha(filtro.hasta)}"/></li><li>Alerta de vencimiento: <c:out value="${filtro.dias}"/> días (parámetro DIAS_POR_VENCER)</li></ul>
    <div class="acciones-exportar">
      <c:choose>
        <c:when test="${sesion.puede('REPORTE_EXPORTAR')}">
          <c:url var="urlS10" value="/reportes/exportar">
            <c:param name="tipo" value="s10"/><c:param name="desde" value="${filtro.desde}"/>
            <c:param name="hasta" value="${filtro.hasta}"/><c:param name="dias" value="${filtro.dias}"/>
          </c:url>
          <a class="btn btn-primario btn-chico" href="<c:out value='${urlS10}'/>">Descargar Excel (.xlsx)</a>
        </c:when>
        <c:otherwise><span class="ayuda">Requiere el permiso REPORTE_EXPORTAR.</span></c:otherwise>
      </c:choose>
    </div>
  </section>
</div>

<div class="rejilla rejilla-2 mt18">
  <section class="tarjeta">
    <h2>Ventas por medio de pago</h2>
    <c:set var="g" value="${ventasMedio.grafico}"/>
    <c:choose>
      <c:when test="${ventasMedio.conGrafico()}"><%@ include file="/WEB-INF/jsp/reportes/_grafico.jspf" %></c:when>
      <c:otherwise><p class="vacio">No hay ventas confirmadas en el periodo.</p></c:otherwise>
    </c:choose>
    <p class="ayuda">Solo ventas confirmadas del <c:out value="${vs:fecha(filtro.desde)}"/> al <c:out value="${vs:fecha(filtro.hasta)}"/>.</p>
  </section>
  <section class="tarjeta">
    <h2>Citas por profesional</h2>
    <c:set var="g" value="${citasProfesional.grafico}"/>
    <c:choose>
      <c:when test="${citasProfesional.conGrafico()}"><%@ include file="/WEB-INF/jsp/reportes/_grafico.jspf" %></c:when>
      <c:otherwise><p class="vacio">No hay citas en el periodo.</p></c:otherwise>
    </c:choose>
    <p class="ayuda">Incluye todos los estados; el detalle por estado está en el reporte clínico.</p>
  </section>
</div>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
