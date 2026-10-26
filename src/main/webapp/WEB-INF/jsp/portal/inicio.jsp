<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Mis mascotas"/><c:set var="seccion" value="portal"/><c:set var="cssExtra" value="modulo-mascotas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P11 · Portal del cliente: mascotas, citas, controles y recordatorios (RF-09, RF-19, RF-37)</p>

<section class="tarjeta saludo">
  <h2>Hola, <c:out value="${sesion.nombreCompleto}"/></h2>
  <p>Aquí puede consultar a sus mascotas, sus próximas citas, los controles indicados por el veterinario y los recordatorios que le enviamos por correo.</p>
  <p class="privacidad">Por su privacidad, la historia clínica completa (diagnósticos y recetas) se entrega directamente en la clínica.</p>
</section>

<h2 class="titulo-seccion">Mis mascotas</h2>
<c:choose>
  <c:when test="${empty mascotas}">
    <section class="tarjeta"><p class="vacio">Aún no hay mascotas registradas a su nombre. Acérquese a recepción para registrarlas.</p></section>
  </c:when>
  <c:otherwise>
    <div class="tarjetas-mascotas">
      <c:forEach var="m" items="${mascotas}">
        <article class="tarjeta tarjeta-mascota">
          <c:choose>
            <c:when test="${m.tieneFoto()}">
              <img class="foto-mascota" src="${ctx}/portal/foto?id=${m.idMascota}" alt="Fotografía de <c:out value='${m.nombre}'/>" width="230" height="170">
            </c:when>
            <c:otherwise>
              <div class="foto-vacia" role="img" aria-label="Sin fotografía"><span class="inicial-mascota"><c:out value="${m.inicial()}"/></span></div>
            </c:otherwise>
          </c:choose>
          <div class="cuerpo">
            <h3><c:out value="${m.nombre}"/> <span class="${vs:claseEstado(m.estado)}"><c:out value="${vs:etiqueta(m.estado)}"/></span></h3>
            <p><c:out value="${m.especie}"/> · <c:out value="${m.raza}"/></p>
            <p><c:out value="${m.sexoTexto()}"/> · <c:out value="${vs:edad(m.fechaNacimiento)}"/></p>
            <a class="btn btn-secundario btn-chico" href="${ctx}/portal/mascota?id=${m.idMascota}">Ver ficha de <c:out value="${m.nombre}"/></a>
          </div>
        </article>
      </c:forEach>
    </div>
  </c:otherwise>
</c:choose>

<section class="tarjeta mt18">
  <h2>Próximas citas</h2>
  <c:choose>
    <c:when test="${empty citas}"><p class="vacio">No tiene citas programadas desde hoy.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Fecha</th><th>Hora</th><th>Mascota</th><th>Servicio</th><th>Profesional</th><th>Estado</th></tr></thead>
        <tbody>
        <c:forEach var="ci" items="${citas}">
          <tr>
            <td><c:out value="${vs:fecha(ci.inicio)}"/></td>
            <td><strong><c:out value="${vs:hora(ci.inicio)}"/></strong></td>
            <td><c:out value="${ci.mascota}"/></td>
            <td><c:out value="${ci.servicio}"/></td>
            <td><c:out value="${ci.profesional}"/></td>
            <td><span class="${vs:claseEstado(ci.estado)}"><c:out value="${vs:etiqueta(ci.estado)}"/></span></td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
    </c:otherwise>
  </c:choose>
  <p class="ayuda">Se muestran las citas de hoy en adelante. Para reprogramar o cancelar una cita, comuníquese con recepción.</p>
</section>

<div class="rejilla rejilla-2">
  <section class="tarjeta">
    <h2>Controles programados</h2>
    <c:choose>
      <c:when test="${empty controles}"><p class="vacio">No hay controles pendientes.</p></c:when>
      <c:otherwise>
        <ul class="linea-tiempo">
          <c:forEach var="co" items="${controles}">
            <li><strong><c:out value="${vs:fecha(co.fechaControl)}"/></strong> · <c:out value="${co.mascota}"/><br>
              <small>Indicado por <c:out value="${co.profesional}"/></small></li>
          </c:forEach>
        </ul>
      </c:otherwise>
    </c:choose>
    <p class="ayuda">Los controles los indica el veterinario al cerrar la consulta (RF-19).</p>
  </section>

  <section class="tarjeta">
    <h2>Mis recordatorios por correo</h2>
    <c:choose>
      <c:when test="${empty recordatorios}"><p class="vacio">Todavía no hay recordatorios a su nombre.</p></c:when>
      <c:otherwise>
        <ul class="lista-recordatorios">
          <c:forEach var="r" items="${recordatorios}">
            <li>
              <span class="${vs:claseEstado(r.estado)}"><c:out value="${vs:etiqueta(r.estado)}"/></span>
              <div>
                <strong><c:out value="${r.origenTexto()}"/> de <c:out value="${r.mascota}"/></strong>
                del <c:out value="${r.esCita() ? vs:fechaHora(r.inicioCita) : vs:fecha(r.fechaControl)}"/><br>
                <small><c:choose>
                  <c:when test="${empty r.enviadoEn}">Programado para el <c:out value="${vs:fechaHora(r.programadoPara)}"/></c:when>
                  <c:otherwise>Procesado el <c:out value="${vs:fechaHora(r.enviadoEn)}"/></c:otherwise>
                </c:choose> · <c:out value="${empty r.correo ? 'sin correo registrado' : r.destinatarioEnmascarado()}"/></small>
              </div>
            </li>
          </c:forEach>
        </ul>
      </c:otherwise>
    </c:choose>
    <p class="ayuda">Solo se envían si usted autorizó recibirlos y tiene un correo válido; si alguno figura como «Fallido», pida en recepción actualizar su correo o su autorización.</p>
  </section>
</div>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
