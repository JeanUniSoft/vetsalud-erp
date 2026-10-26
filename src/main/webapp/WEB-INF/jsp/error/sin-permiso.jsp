<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Acceso restringido"/>
<c:choose>
  <c:when test="${not empty sesion}">
    <%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
    <section class="tarjeta">
      <h2>Esta pantalla no corresponde a su rol</h2>
      <p>Su rol (<c:out value="${vs:etiqueta(sesion.rol)}"/>) no tiene acceso a esta opción según la matriz de permisos (RF-02). El intento quedó registrado.</p>
      <a class="btn btn-secundario" href="${pageContext.request.contextPath}/">Volver al inicio</a>
    </section>
    <%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
  </c:when>
  <c:otherwise><!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><title>Acceso restringido</title><link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/app.css"></head><body><main class="pagina"><div class="tarjeta"><h2>Acceso restringido</h2><p>La solicitud fue rechazada por seguridad. Recargue la página o vuelva a <a href="${pageContext.request.contextPath}/login">iniciar sesión</a>.</p></div></main></body></html></c:otherwise>
</c:choose>
