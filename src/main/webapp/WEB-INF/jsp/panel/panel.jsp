<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Panel principal"/><c:set var="seccion" value="panel"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P02 · Resumen del <c:out value="${vs:fecha(hoy)}"/></p>

<div class="rejilla rejilla-4">
  <div class="kpi destacado">
    <div class="etiqueta">Citas de hoy</div>
    <div class="valor"><c:out value="${resumen.citasHoy}"/></div>
    <div class="detalle"><c:out value="${resumen.citasPorAtender}"/> por atender · <c:out value="${resumen.citasEnSala}"/> en sala · <c:out value="${resumen.citasAtendidas}"/> atendidas</div>
  </div>
  <div class="kpi">
    <div class="etiqueta">Atenciones clínicas</div>
    <div class="valor"><c:out value="${resumen.consultasHoy}"/></div>
    <div class="detalle"><c:out value="${resumen.consultasAbiertas}"/> consultas abiertas sin firmar</div>
  </div>
  <div class="kpi">
    <div class="etiqueta">Ventas confirmadas hoy</div>
    <div class="valor"><c:out value="${vs:soles(resumen.montoVentasHoy)}"/></div>
    <div class="detalle"><c:out value="${resumen.ventasHoy}"/> comprobantes · <c:out value="${resumen.turnosAbiertos}"/> caja(s) abierta(s)</div>
  </div>
  <div class="kpi">
    <div class="etiqueta">Alertas de inventario</div>
    <div class="valor"><c:out value="${resumen.alertasInventario}"/></div>
    <div class="detalle">stock mínimo, por vencer y vencidos</div>
  </div>
</div>

<div class="rejilla rejilla-2 mt18">
  <section class="tarjeta">
    <div class="cabecera-tarjeta">
      <h2>${sesion.rol == 'VETERINARIO' ? 'Mi agenda de hoy' : 'Agenda de hoy'}</h2>
      <c:if test="${sesion.accede('/citas')}"><a class="btn btn-secundario btn-chico" href="${pageContext.request.contextPath}/citas">Abrir agenda</a></c:if>
    </div>
    <c:choose>
      <c:when test="${empty agenda}"><p class="vacio">No hay citas programadas para hoy.</p></c:when>
      <c:otherwise>
        <div class="tabla-scroll"><table class="tabla">
          <thead><tr><th>Hora</th><th>Mascota</th><th>Servicio</th><th>Profesional</th><th>Estado</th></tr></thead>
          <tbody>
          <c:forEach var="cita" items="${agenda}">
            <tr>
              <td><strong><c:out value="${vs:hora(cita.inicio)}"/></strong></td>
              <td><c:out value="${cita.mascota}"/> <small>(<c:out value="${cita.especie}"/>)</small><br><small><c:out value="${cita.cliente}"/></small></td>
              <td><c:out value="${cita.servicio}"/></td>
              <td><c:out value="${cita.veterinario}"/></td>
              <td><span class="${vs:claseEstado(cita.estado)}"><c:out value="${vs:etiqueta(cita.estado)}"/></span></td>
            </tr>
          </c:forEach>
          </tbody>
        </table></div>
      </c:otherwise>
    </c:choose>
  </section>

  <div>
    <section class="tarjeta">
      <h2>Ventas de los últimos 7 días</h2>
      <svg class="grafico" viewBox="0 0 700 200" role="img" aria-label="Ventas diarias de los últimos 7 días">
        <c:forEach var="p" items="${ventasSemana}" varStatus="i">
          <c:set var="alto" value="${p.altura * 1.3}"/>
          <rect x="${i.index * 100 + 22}" y="${155 - alto}" width="56" height="${alto}" rx="6" class="barra-svg"/>
          <text x="${i.index * 100 + 50}" y="${148 - alto}" text-anchor="middle" class="valor-svg"><c:out value="${p.total.intValue()}"/></text>
          <text x="${i.index * 100 + 50}" y="178" text-anchor="middle" class="eje-svg"><c:out value="${fn:substring(vs:fecha(p.dia), 0, 5)}"/></text>
        </c:forEach>
        <line x1="10" y1="156" x2="690" y2="156" class="linea-svg"/>
      </svg>
      <p class="ayuda">Montos en soles de comprobantes confirmados (las anulaciones no suman).</p>
    </section>

    <section class="tarjeta">
      <div class="cabecera-tarjeta"><h2>Alertas para <c:out value="${vs:etiqueta(sesion.rol)}"/></h2>
        <c:if test="${sesion.accede('/alertas')}"><a class="btn btn-secundario btn-chico" href="${pageContext.request.contextPath}/alertas">Ver todas</a></c:if></div>
      <c:choose>
        <c:when test="${empty alertas}"><p class="vacio">Sin alertas pendientes para su rol.</p></c:when>
        <c:otherwise>
          <ul class="lista-alertas">
          <c:forEach var="a" items="${alertas}">
            <li>
              <span class="${vs:claseEstado(a.tipo)}"><c:out value="${vs:etiqueta(a.tipo)}"/></span>
              <c:out value="${a.mensaje}"/> <small class="suave"><c:out value="${vs:fechaHora(a.creadaEn)}"/></small>
              <c:if test="${sesion.puede('ALERTA_ATENDER')}">
                <form method="post" action="${pageContext.request.contextPath}/alertas" class="en-linea">
                  <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                  <input type="hidden" name="id" value="${a.idAlerta}"><input type="hidden" name="volver" value="panel">
                  <button class="btn btn-secundario btn-chico" type="submit">Atender</button>
                </form>
              </c:if>
            </li>
          </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>

    <c:if test="${sesion.rol == 'FARMACIA' || sesion.rol == 'ADMINISTRADOR'}">
      <section class="tarjeta">
        <div class="cabecera-tarjeta"><h2>Inventario que requiere atención</h2>
          <a class="btn btn-secundario btn-chico" href="${pageContext.request.contextPath}/inventario">Ir a inventario</a></div>
        <table class="tabla">
          <thead><tr><th>Producto</th><th>Alerta</th><th class="num">Stock</th><th>Vence</th></tr></thead>
          <tbody>
          <c:forEach var="ai" items="${alertasInventario}">
            <tr><td><c:out value="${ai.producto}"/> <small>(<c:out value="${ai.codigo}"/>)</small></td>
              <td><span class="${vs:claseEstado(ai.alerta)}"><c:out value="${vs:etiqueta(ai.alerta)}"/></span></td>
              <td class="num"><c:out value="${vs:numero(ai.stock)}"/></td>
              <td><c:out value="${vs:fecha(ai.vence)}"/></td></tr>
          </c:forEach>
          </tbody>
        </table>
      </section>
    </c:if>
  </div>
</div>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
