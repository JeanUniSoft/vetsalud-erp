<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Alertas internas"/><c:set var="seccion" value="alertas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">RF-38 · Alertas pendientes del rol <c:out value="${vs:etiqueta(sesion.rol)}"/></p>
<section class="tarjeta">
  <c:choose>
    <c:when test="${empty alertas}"><p class="vacio">No hay alertas pendientes. ¡Todo al día!</p></c:when>
    <c:otherwise>
      <table class="tabla">
        <thead><tr><th>Tipo</th><th>Mensaje</th><th>Creada</th><th></th></tr></thead>
        <tbody>
        <c:forEach var="a" items="${alertas}">
          <tr>
            <td><span class="${vs:claseEstado(a.tipo)}"><c:out value="${vs:etiqueta(a.tipo)}"/></span></td>
            <td><c:out value="${a.mensaje}"/></td>
            <td><c:out value="${vs:fechaHora(a.creadaEn)}"/></td>
            <td class="acciones">
              <c:if test="${sesion.puede('ALERTA_ATENDER')}">
                <form method="post" action="${pageContext.request.contextPath}/alertas" class="en-linea">
                  <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                  <input type="hidden" name="id" value="${a.idAlerta}">
                  <button class="btn btn-primario btn-chico" type="submit">Marcar atendida</button>
                </form>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        </tbody>
      </table>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
