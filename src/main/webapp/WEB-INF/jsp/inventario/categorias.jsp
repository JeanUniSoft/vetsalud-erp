<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Categorías de productos"/><c:set var="seccion" value="inventario"/><c:set var="cssExtra" value="modulo-inventario"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P07 · Mantenimiento de categorías del catálogo (RF-20)</p>
<c:set var="pestana" value="categorias"/>
<%@ include file="/WEB-INF/jsp/inventario/pestanas.jspf" %>
<div class="rejilla rejilla-2">
  <section class="tarjeta">
    <h2>Categorías registradas</h2>
    <c:choose>
      <c:when test="${empty categorias}"><p class="vacio">Aún no hay categorías.</p></c:when>
      <c:otherwise>
        <table class="tabla">
          <thead><tr><th>Categoría</th><th class="num">Productos</th><th>Renombrar</th></tr></thead>
          <tbody>
          <c:forEach var="cat" items="${categorias}">
            <tr>
              <td><a href="${ctx}/inventario?categoria=${cat.idCategoria}&amp;inactivos=1"><c:out value="${cat.nombre}"/></a></td>
              <td class="num"><c:out value="${cat.numProductos}"/></td>
              <td>
                <c:if test="${sesion.puede('INVENTARIO_INGRESAR')}">
                <form method="post" action="${ctx}/inventario/categorias" class="form-fila">
                  <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                  <input type="hidden" name="id" value="${cat.idCategoria}">
                  <div class="campo crece"><label for="nombre_${cat.idCategoria}" class="solo-lector">Nuevo nombre de <c:out value="${cat.nombre}"/></label>
                    <input id="nombre_${cat.idCategoria}" name="nombre" type="text" required maxlength="60" value="<c:out value='${cat.nombre}'/>"></div>
                  <button type="submit" class="btn btn-secundario btn-chico">Guardar</button>
                </form>
                </c:if>
              </td>
            </tr>
          </c:forEach>
          </tbody>
        </table>
        <p class="ayuda">Las categorías no se eliminan porque agrupan productos con historial.</p>
      </c:otherwise>
    </c:choose>
  </section>
  <section class="tarjeta">
    <h2>Nueva categoría</h2>
    <c:choose>
      <c:when test="${sesion.puede('INVENTARIO_INGRESAR')}">
        <form method="post" action="${ctx}/inventario/categorias" class="formulario">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <div class="campo"><label for="nombre">Nombre <span class="req">*</span></label>
            <input id="nombre" name="nombre" type="text" required maxlength="60" placeholder="Ej. Vacunas"></div>
          <div class="acciones-form"><button type="submit" class="btn btn-primario">Registrar categoría</button></div>
        </form>
      </c:when>
      <c:otherwise><p class="vacio">Su rol no puede modificar el catálogo.</p></c:otherwise>
    </c:choose>
  </section>
</div>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
