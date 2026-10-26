<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Programar cita"/><c:set var="seccion" value="citas"/><c:set var="cssExtra" value="modulo-agenda"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P04 · Programación con verificación de profesional, horario, duración y cruces (RF-09, RN-03)</p>

<c:if test="${not sesion.puede('CITA_GESTIONAR')}">
  <div class="aviso aviso-aviso" role="status">Su rol puede consultar la agenda, pero no programar citas (permiso CITA_GESTIONAR).</div>
</c:if>

<c:choose>
  <%-- ===================== Paso 1: elegir la mascota ===================== --%>
  <c:when test="${empty mascota}">
    <section class="tarjeta">
      <h2><span class="paso" aria-hidden="true">1</span>Busque la mascota</h2>
      <form method="get" action="${ctx}/citas/nueva" class="barra-herramientas">
        <div class="campo crece">
          <label for="q">Nombre de la mascota, documento o nombre del propietario</label>
          <input id="q" name="q" type="search" maxlength="80" value="<c:out value='${q}'/>" placeholder="Ej.: Max o 41256789" autofocus>
        </div>
        <button type="submit" class="btn btn-secundario">Buscar</button>
      </form>
      <h3>${empty q ? 'Pacientes atendidos recientemente' : 'Resultados de la búsqueda'}</h3>
      <c:choose>
        <c:when test="${empty resultados}">
          <p class="vacio">No se encontraron mascotas<c:if test="${not empty q}"> para «<c:out value="${q}"/>»</c:if>.
            <c:if test="${sesion.accede('/clientes')}">Si es un paciente nuevo, regístrelo primero en <a href="${ctx}/clientes">Clientes y mascotas</a>.</c:if></p>
        </c:when>
        <c:otherwise>
          <div class="tabla-scroll"><table class="tabla">
            <thead><tr><th>Mascota</th><th>Propietario</th><th>Documento</th><th>Alertas clínicas</th><th>Estado</th><th></th></tr></thead>
            <tbody>
            <c:forEach var="m" items="${resultados}">
              <tr>
                <td><strong><c:out value="${m.nombre}"/></strong><br><small><c:out value="${m.especie}"/> · <c:out value="${m.raza}"/></small></td>
                <td><c:out value="${m.cliente}"/><br><small><c:out value="${m.telefono}"/></small></td>
                <td><c:out value="${m.tipoDocumento}"/> <c:out value="${vs:enmascarar(m.numDocumento)}"/></td>
                <td><c:if test="${not empty m.alertas}"><span class="alerta-mini">⚠ <c:out value="${m.alertas}"/></span></c:if></td>
                <td><span class="${vs:claseEstado(m.estado)}"><c:out value="${vs:etiqueta(m.estado)}"/></span></td>
                <td class="acciones">
                  <c:if test="${m.activa()}"><a class="btn btn-primario btn-chico" href="${ctx}/citas/nueva?mascota=${m.idMascota}">Elegir</a></c:if>
                </td>
              </tr>
            </c:forEach>
            </tbody>
          </table></div>
        </c:otherwise>
      </c:choose>
    </section>
  </c:when>

  <%-- ===================== Paso 2: servicio, profesional, fecha y hora ===================== --%>
  <c:otherwise>
    <div class="rejilla-nueva">
      <section class="tarjeta">
        <div class="cabecera-tarjeta">
          <h2><span class="paso" aria-hidden="true">2</span>Datos de la cita</h2>
          <a class="btn btn-secundario btn-chico" href="${ctx}/citas/nueva">Cambiar mascota</a>
        </div>
        <dl class="ficha">
          <dt>Mascota</dt><dd><strong><c:out value="${mascota.nombre}"/></strong> · <c:out value="${mascota.especie}"/>, <c:out value="${mascota.raza}"/> · <c:out value="${mascota.sexoTexto()}"/> · <c:out value="${vs:edad(mascota.fechaNacimiento)}"/></dd>
          <dt>Propietario</dt><dd><c:out value="${mascota.cliente}"/> · <c:out value="${mascota.telefono}"/></dd>
        </dl>
        <c:if test="${not empty mascota.alertas}">
          <p class="nota-clinica mt18" role="note"><strong>Alerta clínica:</strong> <c:out value="${mascota.alertas}"/></p>
        </c:if>
        <c:if test="${not mascota.activa()}">
          <div class="aviso aviso-error mt18" role="alert">La mascota está «<c:out value="${vs:etiqueta(mascota.estado)}"/>»: no se le pueden programar citas.</div>
        </c:if>

        <form method="post" action="${ctx}/citas/nueva" class="formulario mt18">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <input type="hidden" name="mascota" value="${mascota.idMascota}">
          <div class="campos">
            <div class="campo"><label for="servicio">Servicio <span class="req">*</span></label>
              <select id="servicio" name="servicio" required>
                <option value="">Seleccione…</option>
                <c:forEach var="s" items="${servicios}">
                  <option value="${s.idServicio}" ${servicioSel == s.idServicio ? 'selected' : ''}><c:out value="${s.nombre}"/> · ${s.duracionMin} min · <c:out value="${vs:soles(s.tarifa)}"/></option>
                </c:forEach>
              </select>
              <span class="ayuda">La duración define la hora de fin.</span></div>
            <div class="campo"><label for="vet">Profesional <span class="req">*</span></label>
              <select id="vet" name="vet" required>
                <option value="">Seleccione…</option>
                <c:forEach var="v" items="${veterinarios}">
                  <option value="${v.idUsuario}" ${vetSel == v.idUsuario ? 'selected' : ''}><c:out value="${v.etiqueta()}"/></option>
                </c:forEach>
              </select>
              <span class="ayuda">Solo médicos veterinarios activos.</span></div>
            <div class="campo"><label for="fecha">Fecha <span class="req">*</span></label>
              <input id="fecha" name="fecha" type="date" required min="${hoy}" value="<c:out value='${fechaSel}'/>"></div>
            <div class="campo"><label for="hora">Hora de inicio <span class="req">*</span></label>
              <input id="hora" name="hora" type="time" required min="08:00" max="19:55" step="300" value="<c:out value='${vs:hora(horaSel)}'/>">
              <span class="ayuda">Atención de <c:out value="${vs:hora(apertura)}"/> a <c:out value="${vs:hora(cierre)}"/>.</span></div>
            <div class="campo ancho"><label for="motivo">Motivo de la cita</label>
              <input id="motivo" name="motivo" type="text" maxlength="200" value="<c:out value='${motivoSel}'/>" placeholder="Ej.: control respiratorio, vacuna anual…"></div>
          </div>
          <p class="ayuda">Antes de guardar se verifica que la cita no esté en el pasado, que termine dentro del horario y que no se cruce con otra cita del profesional ni de la mascota (RN-03).</p>
          <div class="acciones-form">
            <button type="submit" class="btn btn-primario" ${mascota.activa() and sesion.puede('CITA_GESTIONAR') ? '' : 'disabled'}>Programar cita</button>
            <a class="btn btn-secundario" href="${ctx}/citas">Cancelar</a>
          </div>
        </form>
      </section>

      <aside class="tarjeta" aria-labelledby="titulo-ocupacion">
        <h2 id="titulo-ocupacion">Horarios ocupados</h2>
        <form method="get" action="${ctx}/citas/nueva" class="barra-herramientas">
          <input type="hidden" name="mascota" value="${mascota.idMascota}">
          <c:if test="${not empty servicioSel}"><input type="hidden" name="servicio" value="${servicioSel}"></c:if>
          <c:if test="${not empty vetSel}"><input type="hidden" name="vet" value="${vetSel}"></c:if>
          <div class="campo crece"><label for="diaOcupacion">Día</label>
            <input id="diaOcupacion" name="fecha" type="date" value="${diaOcupacion}"></div>
          <button type="submit" class="btn btn-secundario btn-chico">Ver</button>
        </form>
        <p class="ayuda">Citas vigentes del <c:out value="${vs:fecha(diaOcupacion)}"/> (las canceladas liberan el horario).</p>
        <c:choose>
          <c:when test="${empty ocupacion}"><p class="vacio">Sin citas ese día: todo el horario está libre.</p></c:when>
          <c:otherwise>
            <c:set var="vetAnterior" value=""/>
            <c:forEach var="o" items="${ocupacion}">
              <c:if test="${o.veterinario != vetAnterior}">
                <c:if test="${not empty vetAnterior}"></ul></c:if>
                <h3 class="grupo-vet">M.V. <c:out value="${o.veterinario}"/></h3>
                <ul class="ocupacion">
                <c:set var="vetAnterior" value="${o.veterinario}"/>
              </c:if>
              <li><span class="hora-cita"><c:out value="${vs:hora(o.inicio)}"/>–<c:out value="${vs:hora(o.fin)}"/></span>
                <span><c:out value="${o.mascota}"/> <small>· <c:out value="${o.servicio}"/></small></span></li>
            </c:forEach>
            </ul>
          </c:otherwise>
        </c:choose>
      </aside>
    </div>
  </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
