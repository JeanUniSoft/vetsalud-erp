<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="${empty cita ? 'Cita' : 'Cita N.° '.concat(cita.idCita)}"/><c:set var="seccion" value="citas"/><c:set var="cssExtra" value="modulo-agenda"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="gestiona" value="${sesion.puede('CITA_GESTIONAR')}"/>
<p class="etiqueta-pantalla">P04 · Detalle, llegada, reprogramación y cancelación con historial de estados (RF-10, RF-12)</p>

<c:choose>
  <c:when test="${empty cita}">
    <section class="tarjeta">
      <h2>No se encontró la cita</h2>
      <p>La cita solicitada no existe o el enlace está incompleto.</p>
      <a class="btn btn-secundario" href="${ctx}/citas">Volver a la agenda</a>
    </section>
  </c:when>
  <c:otherwise>
    <c:set var="c" value="${cita}"/>
    <c:url var="urlDia" value="/citas"><c:param name="fecha" value="${c.inicio.toLocalDate()}"/><c:param name="vet" value=""/></c:url>
    <div class="barra-herramientas no-imprimir">
      <a class="btn btn-secundario btn-chico" href="<c:out value='${urlDia}'/>">‹ Agenda del <c:out value="${vs:fecha(c.inicio)}"/></a>
    </div>

    <div class="rejilla rejilla-2">
      <section class="tarjeta">
        <div class="cabecera-tarjeta">
          <h2><c:out value="${c.mascota}"/> · <c:out value="${c.servicio}"/></h2>
          <span class="${vs:claseEstado(c.estado)}"><c:out value="${vs:etiqueta(c.estado)}"/></span>
        </div>
        <dl class="ficha">
          <dt>Fecha</dt><dd><strong><c:out value="${vs:fecha(c.inicio)}"/></strong></dd>
          <dt>Horario</dt><dd><c:out value="${vs:hora(c.inicio)}"/> a <c:out value="${vs:hora(c.fin)}"/> (${c.duracionMin()} min)</dd>
          <dt>Profesional</dt><dd>M.V. <c:out value="${c.veterinario}"/></dd>
          <dt>Mascota</dt><dd><c:out value="${c.mascota}"/> (<c:out value="${c.especie}"/>)
            <c:if test="${sesion.accede('/historia')}"> · <a href="${ctx}/historia/mascota?id=${c.idMascota}">Ver historia clínica</a></c:if></dd>
          <dt>Propietario</dt><dd><c:out value="${c.cliente}"/> · <c:out value="${c.telefonoCliente}"/></dd>
          <dt>Motivo</dt><dd><c:out value="${empty c.motivo ? '—' : c.motivo}"/></dd>
          <dt>Registrada por</dt><dd><c:out value="${c.registradoPor}"/> · <c:out value="${vs:fechaHora(c.creadoEn)}"/></dd>
          <dt>Consulta</dt><dd>
            <c:choose>
              <c:when test="${empty c.idConsulta}">Sin consulta registrada</c:when>
              <c:otherwise>N.° ${c.idConsulta} <span class="${vs:claseEstado(c.estadoConsulta)}"><c:out value="${vs:etiqueta(c.estadoConsulta)}"/></span></c:otherwise>
            </c:choose></dd>
        </dl>
        <c:if test="${not empty c.alertasMascota}">
          <p class="nota-clinica mt18" role="note"><strong>Alerta clínica:</strong> <c:out value="${c.alertasMascota}"/></p>
        </c:if>

        <div class="acciones-form mt18">
          <c:if test="${puedeAbrirConsulta and c.estado == 'EN_ATENCION' and empty c.idConsulta}">
            <a class="btn btn-primario" href="${ctx}/consultas/nueva?cita=${c.idCita}">Iniciar consulta</a>
          </c:if>
          <c:if test="${not empty c.idConsulta and sesion.accede('/consultas')}">
            <a class="btn btn-secundario" href="${ctx}/consultas/ver?id=${c.idConsulta}">Ver consulta N.° ${c.idConsulta}</a>
          </c:if>
        </div>

        <div class="separador"></div>
        <h3 id="estado">Estado de la cita (RF-12)</h3>
        <c:choose>
          <c:when test="${empty c.transiciones}">
            <p class="suave">La cita está «<c:out value="${vs:etiqueta(c.estado)}"/>»: es un estado final y no admite más cambios.</p>
          </c:when>
          <c:when test="${not gestiona}">
            <p class="suave">Su rol puede consultar la cita, pero no cambiar su estado (permiso CITA_GESTIONAR).</p>
          </c:when>
          <c:otherwise>
            <div class="botonera-estado">
              <c:forEach var="t" items="${c.transiciones}">
                <c:if test="${not vs:contiene(conMotivo, t)}">
                  <form method="post" action="${ctx}/citas/estado">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                    <input type="hidden" name="id" value="${c.idCita}">
                    <input type="hidden" name="estado" value="${t}">
                    <input type="hidden" name="volver" value="<c:out value='${urlActual}'/>">
                    <button type="submit" class="btn btn-primario"><c:out value="${accionesEstado[t]}"/></button>
                  </form>
                </c:if>
              </c:forEach>
            </div>
            <c:set var="hayConMotivo" value="false"/>
            <c:forEach var="t" items="${c.transiciones}"><c:if test="${vs:contiene(conMotivo, t)}"><c:set var="hayConMotivo" value="true"/></c:if></c:forEach>
            <c:if test="${hayConMotivo}">
              <form method="post" action="${ctx}/citas/estado" class="form-motivo" data-confirmar="¿Confirma el cambio? La cita quedará cerrada y el horario se liberará.">
                <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                <input type="hidden" name="id" value="${c.idCita}">
                <input type="hidden" name="volver" value="<c:out value='${urlActual}'/>">
                <div class="campos">
                  <div class="campo"><label for="estadoFinal">Cerrar la cita como</label>
                    <select id="estadoFinal" name="estado" required>
                      <c:forEach var="t" items="${c.transiciones}">
                        <c:if test="${vs:contiene(conMotivo, t)}"><option value="${t}"><c:out value="${vs:etiqueta(t)}"/></option></c:if>
                      </c:forEach>
                    </select></div>
                  <div class="campo ancho"><label for="motivoCierre">Motivo <span class="req">*</span></label>
                    <input id="motivoCierre" name="motivo" type="text" required maxlength="100" placeholder="Ej.: el propietario avisó que viaja"></div>
                </div>
                <div><button type="submit" class="btn btn-peligro">Registrar</button></div>
              </form>
            </c:if>
          </c:otherwise>
        </c:choose>
      </section>

      <div>
        <section class="tarjeta" id="reprogramar">
          <h2>Reprogramar (RF-10)</h2>
          <c:choose>
            <c:when test="${not c.reprogramable()}">
              <p class="suave">Solo se reprograma una cita pendiente o confirmada.</p>
            </c:when>
            <c:when test="${not gestiona}">
              <p class="suave">Su rol no puede reprogramar citas.</p>
            </c:when>
            <c:otherwise>
              <form method="post" action="${ctx}/citas/reprogramar" class="formulario">
                <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                <input type="hidden" name="id" value="${c.idCita}">
                <div class="campos">
                  <div class="campo"><label for="rFecha">Nueva fecha <span class="req">*</span></label>
                    <input id="rFecha" name="fecha" type="date" required value="${c.inicio.toLocalDate()}"></div>
                  <div class="campo"><label for="rHora">Nueva hora <span class="req">*</span></label>
                    <input id="rHora" name="hora" type="time" required min="08:00" max="19:55" step="300" value="<c:out value='${vs:hora(c.inicio)}'/>"></div>
                  <div class="campo"><label for="rVet">Profesional</label>
                    <select id="rVet" name="vet">
                      <c:forEach var="v" items="${veterinarios}">
                        <option value="${v.idUsuario}" ${c.idVeterinario == v.idUsuario ? 'selected' : ''}><c:out value="${v.etiqueta()}"/></option>
                      </c:forEach>
                    </select></div>
                  <div class="campo ancho"><label for="rMotivo">Motivo de la reprogramación <span class="req">*</span></label>
                    <input id="rMotivo" name="motivo" type="text" required maxlength="100" placeholder="Ej.: el cliente pidió la tarde"></div>
                </div>
                <p class="ayuda">La duración se recalcula con el servicio (<c:out value="${c.servicio}"/>) y el horario anterior queda libre. El cambio queda en el historial con su responsable.</p>
                <div class="acciones-form"><button type="submit" class="btn btn-primario">Reprogramar cita</button></div>
              </form>
            </c:otherwise>
          </c:choose>
        </section>

        <section class="tarjeta">
          <h2>Historial de la cita</h2>
          <c:choose>
            <c:when test="${empty historial}"><p class="vacio">Sin movimientos registrados.</p></c:when>
            <c:otherwise>
              <div class="tabla-scroll"><table class="tabla">
                <thead><tr><th>Fecha y hora</th><th>Cambio</th><th>Responsable</th></tr></thead>
                <tbody>
                <c:forEach var="h" items="${historial}">
                  <tr>
                    <td><c:out value="${vs:fechaHora(h.fechaHora)}"/></td>
                    <td>
                      <c:choose>
                        <c:when test="${h.esReprogramacion()}"><span class="badge badge-neutro">Reprogramación</span></c:when>
                        <c:when test="${empty h.estadoAnterior}">Registrada como <span class="${vs:claseEstado(h.estadoNuevo)}"><c:out value="${vs:etiqueta(h.estadoNuevo)}"/></span></c:when>
                        <c:otherwise><span class="cambio-estado"><span class="${vs:claseEstado(h.estadoAnterior)}"><c:out value="${vs:etiqueta(h.estadoAnterior)}"/></span> → <span class="${vs:claseEstado(h.estadoNuevo)}"><c:out value="${vs:etiqueta(h.estadoNuevo)}"/></span></span></c:otherwise>
                      </c:choose>
                      <c:if test="${not empty h.motivo}"><br><small><c:out value="${h.motivo}"/></small></c:if>
                    </td>
                    <td><c:out value="${empty h.nombreUsuario ? 'Sistema' : h.nombreUsuario}"/><c:if test="${not empty h.usuario}"><br><small><c:out value="${h.usuario}"/></small></c:if></td>
                  </tr>
                </c:forEach>
                </tbody>
              </table></div>
            </c:otherwise>
          </c:choose>
        </section>
      </div>
    </div>
  </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
