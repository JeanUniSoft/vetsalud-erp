<%@ page isErrorPage="true" %>
<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><title>Error · VetSalud ERP</title><link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/app.css"></head>
<body><main class="pagina"><div class="tarjeta">
  <h2>No se pudo completar la solicitud</h2>
  <p><c:out value="${empty mensajeError ? 'Ocurrió un problema inesperado. El equipo técnico puede revisarlo con el código de solicitud.' : mensajeError}"/></p>
  <p class="suave">Código de seguimiento: <strong><c:out value="${empty idSolicitud ? 'sin código' : idSolicitud}"/></strong></p>
  <a class="btn btn-secundario" href="${pageContext.request.contextPath}/">Volver al inicio</a>
</div></main></body></html>
