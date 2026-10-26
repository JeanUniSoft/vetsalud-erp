<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Agenda de citas"/><c:set var="seccion" value="citas"/><c:set var="cssExtra" value="modulo-agenda"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="f" value="${agenda.filtro}"/>
<c:set var="gestiona" value="${sesion.puede('CITA_GESTIONAR')}"/>
<p class="etiqueta-pantalla">P04 · Agenda ${f.semanal() ? 'semanal' : 'diaria'} por profesional, servicio y estado (RF-11, RF-12, RF-19)</p>

<section class="tarjeta no-imprimir">
  <form method="get" action="${ctx}/citas" class="barra-herramientas" aria-label="Filtros de la agenda">
    <div class="campo"><label for="fecha">Fecha</label>
      <input id="fecha" name="fecha" type="date" value="<c:out value='${f.fecha}'/>"></div>
    <div class="campo"><label for="vista">Vista</label>
      <select id="vista" name="vista">
        <option value="dia" ${f.semanal() ? '' : 'selected'}>Día</option>
        <option value="semana" ${f.semanal() ? 'selected' : ''}>Semana</option>
      </select></div>
    <div class="campo"><label for="vet">Profesional</label>
      <select id="vet" name="vet">
        <option value="">Todos</option>
        <c:forEach var="v" items="${veterinarios}">
          <option value="${v.idUsuario}" ${f.idVeterinario == v.idUsuario ? 'selected' : ''}><c:out value="${v.etiqueta()}"/></option>
        </c:forEach>
      </select></div>
    <div class="campo"><label for="servicio">Servicio</label>
      <select id="servicio" name="servicio">
        <option value="">Todos</option>
        <c:forEach var="s" items="${servicios}">
          <option value="${s.idServicio}" ${f.idServicio == s.idServicio ? 'selected' : ''}><c:out value="${s.nombre}"/></option>
        </c:forEach>
      </select></div>
    <div class="campo"><label for="estado">Estado</label>
      <select id="estado" name="estado">
        <option value="">Todos</option>
        <c:forEach var="e" items="${estados}">
          <option value="${e}" ${f.estado == e ? 'selected' : ''}><c:out value="${vs:etiqueta(e)}"/></option>
        </c:forEach>
      </select></div>
    <button type="submit" class="btn btn-secundario">Aplicar filtros</button>
    <c:if test="${gestiona}"><a class="btn btn-primario" href="${ctx}/citas/nueva">+ Programar cita</a></c:if>
  </form>
</section>

<c:if test="${not empty agenda}">
  <%-- Enlaces de navegación que conservan los filtros (c:url codifica cada parámetro) --%>
  <c:url var="urlAnterior" value="/citas"><c:param name="fecha" value="${agenda.anterior}"/><c:param name="vista" value="${f.vista}"/><c:param name="vet" value="${f.idVeterinario}"/><c:param name="servicio" value="${f.idServicio}"/><c:param name="estado" value="${f.estado}"/></c:url>
  <c:url var="urlSiguiente" value="/citas"><c:param name="fecha" value="${agenda.siguiente}"/><c:param name="vista" value="${f.vista}"/><c:param name="vet" value="${f.idVeterinario}"/><c:param name="servicio" value="${f.idServicio}"/><c:param name="estado" value="${f.estado}"/></c:url>
  <c:url var="urlHoy" value="/citas"><c:param name="fecha" value="${hoy}"/><c:param name="vista" value="${f.vista}"/><c:param name="vet" value="${f.idVeterinario}"/><c:param name="servicio" value="${f.idServicio}"/><c:param name="estado" value="${f.estado}"/></c:url>
  <c:url var="urlOtraVista" value="/citas"><c:param name="fecha" value="${f.fecha}"/><c:param name="vista" value="${f.semanal() ? 'dia' : 'semana'}"/><c:param name="vet" value="${f.idVeterinario}"/><c:param name="servicio" value="${f.idServicio}"/><c:param name="estado" value="${f.estado}"/></c:url>

  <section class="tarjeta">
    <div class="agenda-cabecera">
      <h2>
        <c:choose>
          <c:when test="${f.semanal()}">Semana del <c:out value="${vs:fecha(agenda.desde)}"/> al <c:out value="${vs:fecha(agenda.hasta)}"/></c:when>
          <c:otherwise><c:out value="${agenda.dias[0].nombre}"/> · <c:out value="${vs:fecha(agenda.desde)}"/></c:otherwise>
        </c:choose>
      </h2>
      <nav class="agenda-nav" aria-label="Cambiar de ${f.semanal() ? 'semana' : 'día'}">
        <a class="btn btn-secundario btn-chico" href="<c:out value='${urlAnterior}'/>">‹ Anterior</a>
        <a class="btn btn-secundario btn-chico" href="<c:out value='${urlHoy}'/>">Hoy</a>
        <a class="btn btn-secundario btn-chico" href="<c:out value='${urlSiguiente}'/>">Siguiente ›</a>
        <a class="btn btn-secundario btn-chico" href="<c:out value='${urlOtraVista}'/>">${f.semanal() ? 'Ver día' : 'Ver semana'}</a>
      </nav>
    </div>

    <ul class="resumen-estados" aria-label="Citas por estado">
      <li><span class="badge badge-neutro">Total: ${fn:length(agenda.citas)}</span></li>
      <c:forEach var="e" items="${agenda.conteoPorEstado}">
        <li><span class="${vs:claseEstado(e.key)}"><c:out value="${vs:etiqueta(e.key)}"/>: ${e.value}</span></li>
      </c:forEach>
    </ul>

    <c:choose>
      <%-- ===================== Vista semanal: rejilla de 7 columnas ===================== --%>
      <c:when test="${f.semanal()}">
        <div class="semana">
          <c:forEach var="d" items="${agenda.dias}">
            <c:url var="urlDia" value="/citas"><c:param name="fecha" value="${d.fecha}"/><c:param name="vista" value="dia"/><c:param name="vet" value="${f.idVeterinario}"/><c:param name="servicio" value="${f.idServicio}"/><c:param name="estado" value="${f.estado}"/></c:url>
            <section class="dia ${d.hoy ? 'hoy' : ''}" aria-label="${d.nombre}">
              <header class="dia-cabecera">
                <a href="<c:out value='${urlDia}'/>" title="Ver la agenda de este día"><c:out value="${d.nombre}"/></a>
                <span class="badge badge-neutro">${fn:length(d.citas)}</span>
              </header>
              <c:choose>
                <c:when test="${empty d.citas}"><p class="dia-vacio">Sin citas</p></c:when>
                <c:otherwise>
                  <ul class="dia-citas">
                    <c:forEach var="c" items="${d.citas}">
                      <li>
                        <a class="cita-mini estado-${c.estado}" href="${ctx}/citas/ver?id=${c.idCita}">
                          <span class="hora-cita"><c:out value="${vs:hora(c.inicio)}"/>–<c:out value="${vs:hora(c.fin)}"/></span>
                          <strong><c:out value="${c.mascota}"/></strong>
                          <small><c:out value="${c.servicio}"/></small>
                          <small>M.V. <c:out value="${c.veterinario}"/></small>
                          <span class="${vs:claseEstado(c.estado)}"><c:out value="${vs:etiqueta(c.estado)}"/></span>
                        </a>
                      </li>
                    </c:forEach>
                  </ul>
                </c:otherwise>
              </c:choose>
            </section>
          </c:forEach>
        </div>
      </c:when>

      <%-- ===================== Vista diaria: tabla por hora con acciones de estado ===================== --%>
      <c:when test="${empty agenda.citas}">
        <p class="vacio">No hay citas para este día con los filtros elegidos.
          <c:if test="${gestiona}"><a href="${ctx}/citas/nueva">Programar una cita</a></c:if></p>
      </c:when>
      <c:otherwise>
        <div class="tabla-scroll"><table class="tabla">
          <thead><tr><th>Hora</th><th>Mascota y propietario</th><th>Servicio</th><th>Profesional</th><th>Estado</th><th class="derecha">Acciones</th></tr></thead>
          <tbody>
          <c:forEach var="c" items="${agenda.citas}">
            <tr class="${c.liberada() ? 'cita-liberada' : ''} ${resaltar == c.idCita ? 'cita-resaltada' : ''}">
              <td class="hora-cita"><c:out value="${vs:hora(c.inicio)}"/><small>a <c:out value="${vs:hora(c.fin)}"/></small></td>
              <td>
                <strong><c:out value="${c.mascota}"/></strong> <small>(<c:out value="${c.especie}"/>)</small>
                <c:if test="${not empty c.alertasMascota}"><br><span class="alerta-mini">⚠ <c:out value="${c.alertasMascota}"/></span></c:if>
                <br><small><c:out value="${c.cliente}"/> · <c:out value="${c.telefonoCliente}"/></small>
              </td>
              <td><c:out value="${c.servicio}"/></td>
              <td>M.V. <c:out value="${c.veterinario}"/></td>
              <td><span class="${vs:claseEstado(c.estado)}"><c:out value="${vs:etiqueta(c.estado)}"/></span></td>
              <td class="acciones">
                <div class="acciones-estado">
                  <c:if test="${gestiona}">
                    <c:forEach var="t" items="${c.transiciones}">
                      <c:choose>
                        <c:when test="${vs:contiene(conMotivo, t)}">
                          <a class="btn btn-peligro btn-chico" href="${ctx}/citas/ver?id=${c.idCita}#estado"><c:out value="${accionesEstado[t]}"/>…</a>
                        </c:when>
                        <c:otherwise>
                          <form method="post" action="${ctx}/citas/estado">
                            <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                            <input type="hidden" name="id" value="${c.idCita}">
                            <input type="hidden" name="estado" value="${t}">
                            <input type="hidden" name="volver" value="<c:out value='${urlActual}'/>">
                            <button type="submit" class="btn btn-secundario btn-chico"><c:out value="${accionesEstado[t]}"/></button>
                          </form>
                        </c:otherwise>
                      </c:choose>
                    </c:forEach>
                  </c:if>
                  <c:if test="${puedeAbrirConsulta and c.estado == 'EN_ATENCION' and empty c.idConsulta}">
                    <a class="btn btn-primario btn-chico" href="${ctx}/consultas/nueva?cita=${c.idCita}">Iniciar consulta</a>
                  </c:if>
                  <c:if test="${not empty c.idConsulta and sesion.accede('/consultas')}">
                    <a class="btn btn-secundario btn-chico" href="${ctx}/consultas/ver?id=${c.idConsulta}">Consulta</a>
                  </c:if>
                  <a class="btn btn-secundario btn-chico" href="${ctx}/citas/ver?id=${c.idCita}" aria-label="Ver la cita de ${fn:escapeXml(c.mascota)}">Ver</a>
                </div>
              </td>
            </tr>
          </c:forEach>
          </tbody>
        </table></div>
        <p class="ayuda">Solo se muestran las acciones de las transiciones válidas (RF-12). Cancelar y «No asistió» piden el motivo en el detalle de la cita (RF-10).</p>
      </c:otherwise>
    </c:choose>
  </section>

  <%-- ===================== RF-19: controles programados en el rango visible ===================== --%>
  <section class="tarjeta">
    <div class="cabecera-tarjeta">
      <h2>Controles programados</h2>
      <small>Consultas con fecha de control ${f.semanal() ? 'en esta semana' : 'para este día'} (RF-19)</small>
    </div>
    <c:choose>
      <c:when test="${empty agenda.controles}"><p class="vacio">No hay controles programados en este rango.</p></c:when>
      <c:otherwise>
        <div class="tabla-scroll"><table class="tabla">
          <thead><tr><th>Fecha</th><th>Mascota</th><th>Propietario</th><th>Diagnóstico</th><th>Profesional</th><th>Cita</th><th></th></tr></thead>
          <tbody>
          <c:forEach var="k" items="${agenda.controles}">
            <tr>
              <td><strong><c:out value="${vs:fecha(k.fechaControl)}"/></strong></td>
              <td><c:out value="${k.mascota}"/> <small>(<c:out value="${k.especie}"/>)</small></td>
              <td><c:out value="${k.cliente}"/><br><small><c:out value="${k.telefonoCliente}"/></small></td>
              <td><c:out value="${k.diagnostico}"/></td>
              <td>M.V. <c:out value="${k.veterinario}"/></td>
              <td><span class="${k.conCita ? 'badge badge-ok' : 'badge badge-aviso'}">${k.conCita ? 'Con cita' : 'Sin cita'}</span></td>
              <td class="acciones">
                <c:if test="${gestiona and not k.conCita}">
                  <c:url var="urlControl" value="/citas/nueva"><c:param name="mascota" value="${k.idMascota}"/><c:param name="fecha" value="${k.fechaControl}"/><c:param name="vet" value="${k.idVeterinario}"/><c:param name="motivo" value="Control de la consulta N.° ${k.idConsulta}"/></c:url>
                  <a class="btn btn-secundario btn-chico" href="<c:out value='${urlControl}'/>">Programar cita</a>
                </c:if>
                <c:if test="${sesion.accede('/historia')}">
                  <a class="btn btn-secundario btn-chico" href="${ctx}/historia/mascota?id=${k.idMascota}">Historia</a>
                </c:if>
              </td>
            </tr>
          </c:forEach>
          </tbody>
        </table></div>
      </c:otherwise>
    </c:choose>
  </section>
</c:if>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
