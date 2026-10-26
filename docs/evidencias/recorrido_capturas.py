"""Recorrido automatizado de las 12 pantallas (Playwright + Chrome) que genera las capturas de docs/capturas.
Requisitos: servidor en http://localhost:8080 con la BD recién instalada (db/instalar.sql) y `pip install playwright`.
Uso: python recorrido_capturas.py <carpeta_salida>"""
import sys, re, json
from playwright.sync_api import sync_playwright
URL = "http://localhost:8080"; CLAVE = "VetSalud2026!"
OUT = sys.argv[1]; SOLO = sys.argv[2] if len(sys.argv) > 2 else ""
REG = []

def foto(pg, nombre, alto=1100, selector=None):
    if SOLO and not nombre.startswith(SOLO):
        return
    pg.wait_for_load_state("networkidle")
    ruta = f"{OUT}/{nombre}.png"
    if selector:
        pg.locator(selector).first.screenshot(path=ruta)
    else:
        h = pg.evaluate("document.documentElement.scrollHeight")
        alto_final = min(h, alto)
        # la ventana toma el alto de la captura para que el menú lateral (sticky, 100vh) quede completo
        pg.set_viewport_size({"width": 1440, "height": max(900, alto_final)})
        pg.evaluate("window.scrollTo(0, 0)"); pg.wait_for_timeout(150)
        pg.screenshot(path=ruta, clip={"x": 0, "y": 0, "width": 1440, "height": alto_final})
        pg.set_viewport_size({"width": 1440, "height": 900})
    REG.append(nombre); print("  ✓", nombre, pg.url)

def elegir(pg, selector, texto):
    ok = pg.evaluate("""([sel, t]) => { const s = document.querySelector(sel); if (!s) return false;
        const o = [...s.options].find(o => o.text.includes(t)); if (!o) return false;
        s.value = o.value; s.dispatchEvent(new Event('change', {bubbles: true})); return true; }""", [selector, texto])
    if not ok: print("   ! opción no encontrada", selector, texto)

def sesion(b, usuario, clave=CLAVE):
    ctx = b.new_context(viewport={"width": 1440, "height": 900}, locale="es-PE", timezone_id="America/Lima")
    pg = ctx.new_page(); pg.on("dialog", lambda d: d.accept())
    pg.goto(URL + "/login"); pg.fill("input[name=usuario]", usuario); pg.fill("input[name=clave]", clave)
    pg.click("button[type=submit]"); pg.wait_for_load_state("networkidle")
    return ctx, pg

def pulsar(pg, nombre, dentro=None):
    base = pg.locator(dentro) if dentro else pg
    base.locator(f"button:has-text('{nombre}'), a:has-text('{nombre}'), input[type=submit][value='{nombre}']").first.click()
    pg.wait_for_load_state("networkidle")

def ir(pg, ruta):
    pg.goto(URL + ruta); pg.wait_for_load_state("networkidle")

def mensaje(pg):
    m = pg.locator(".alerta, .mensaje, [role=alert], [role=status]").all_inner_texts()
    return " | ".join(x.strip().replace("\n", " ")[:160] for x in m if x.strip())

with sync_playwright() as p:
    b = p.chromium.launch(channel="chrome")
    # ---------------- P01 acceso
    ctx = b.new_context(viewport={"width": 1440, "height": 900}, locale="es-PE", timezone_id="America/Lima"); pg = ctx.new_page()
    ir(pg, "/login"); foto(pg, "p01_login", 900)
    pg.fill("input[name=usuario]", "ltorres"); pg.fill("input[name=clave]", "ClaveIncorrecta1"); pg.click("button[type=submit]")
    foto(pg, "p01_login_error", 900); print("   msg:", mensaje(pg)); ctx.close()

    # ---------------- P02 / P03 / P04 recepción
    ctx, pg = sesion(b, "ltorres")
    ir(pg, "/cuenta"); foto(pg, "p01_cuenta", 900)
    ir(pg, "/panel"); foto(pg, "p02_panel_recepcion")
    ir(pg, "/clientes"); foto(pg, "p03_clientes")
    ir(pg, "/clientes/ver?id=1"); foto(pg, "p03_ficha_cliente")
    ir(pg, "/mascotas/ver?id=2"); foto(pg, "p03_ficha_mascota")
    ir(pg, "/citas"); foto(pg, "p04_agenda", 1250)
    ir(pg, "/citas?fecha=2026-10-26&vista=semana"); foto(pg, "p04_agenda_semana")
    # cita con cruce de horario (RN-03)
    ir(pg, "/citas/nueva?q=Canela")
    pulsar(pg, "Elegir")
    elegir(pg, "select[name=servicio]", "Consulta general"); elegir(pg, "select[name=vet]", "Mendoza")
    pg.fill("input[name=fecha]", "2026-10-26"); pg.fill("input[name=hora]", "09:45")
    pg.fill("textarea[name=motivo], input[name=motivo]", "Control de piel")
    pulsar(pg, "Programar cita")
    print("   msg:", mensaje(pg)); foto(pg, "p04_cita_cruce")
    pg.fill("input[name=hora]", "10:30"); pulsar(pg, "Programar cita")
    print("   msg:", mensaje(pg)); foto(pg, "p04_cita_registrada", 900)
    # llegada del paciente (cita 10, Bruno)
    ir(pg, "/citas/ver?id=10"); pulsar(pg, "Registrar llegada")
    print("   msg:", mensaje(pg)); foto(pg, "p04_detalle_cita")
    ir(pg, "/reportes"); foto(pg, "p00_acceso_denegado", 900)
    ctx.close()

    # ---------------- P05 / P06 atención clínica (Simba, consulta 4, Dra. Salazar)
    ctx, pg = sesion(b, "asalazar")
    ir(pg, "/panel"); foto(pg, "p02_panel_veterinario")
    ir(pg, "/consultas/ver?id=4"); foto(pg, "p05_consulta_abierta", 1250)
    pg.fill("[name=indicaciones]", "Observar el sitio de aplicación 48 h. Refuerzo de vacuna en 30 días.")
    pg.fill("input[name=fechaControl]", "2026-11-25")
    pulsar(pg, "Guardar datos clínicos"); print("   datos:", mensaje(pg))
    pg.fill("input[name=temperatura]", "38.6"); pg.fill("input[name=frecuenciaCardiaca]", "160")
    pg.fill("input[name=frecuenciaRespiratoria]", "28"); pg.fill("input[name=peso]", "4.2")
    pulsar(pg, "Registrar signos vitales"); print("   signos:", mensaje(pg))
    # firma sin diagnóstico principal → RN-04
    pulsar(pg, "Firmar y cerrar la consulta")
    print("   firma sin dx:", mensaje(pg)); foto(pg, "p05_firma_sin_diagnostico", 900)
    pg.fill("[name=descripcion]", "Paciente clínicamente sano, apto para vacunación")
    cb = pg.locator("input[name=principal]")
    if cb.count() and cb.first.get_attribute("type") == "checkbox": cb.first.check()
    pulsar(pg, "Agregar diagnóstico"); print("   dx:", mensaje(pg))
    elegir(pg, "select[name=servicio]", "Vacuna triple felina"); elegir(pg, "select[name=responsable]", "Salazar")
    pg.locator("form[action$='/consultas/procedimiento'] input[name=cantidad]").fill("1")
    pg.fill("[name=resultado]", "Vacuna aplicada en región interescapular, sin reacción inmediata")
    pulsar(pg, "Registrar procedimiento"); print("   proc:", mensaje(pg))
    foto(pg, "p06_procedimientos", 1500)
    pulsar(pg, "Firmar y cerrar la consulta")
    print("   firma:", mensaje(pg)); foto(pg, "p06_consulta_firmada")
    ctx.close()
    ctx, pg = sesion(b, "cmendoza")
    ir(pg, "/historia/mascota?id=2"); foto(pg, "p05_historia_clinica", 1250)
    ir(pg, "/consultas/receta?id=1"); foto(pg, "p06_receta", 1000)
    ctx.close()

    # ---------------- P07 farmacia
    ctx, pg = sesion(b, "mccori")
    ir(pg, "/inventario"); foto(pg, "p07_inventario")
    ir(pg, "/inventario/producto?id=2")
    q = pg.locator("form:has(button:has-text('Sugerir lote')) input[name=cantidad]")
    if q.count(): q.fill("5"); pulsar(pg, "Sugerir lote (FEFO)")
    print("   fefo:", pg.url, mensaje(pg)); foto(pg, "p07_producto_fefo", 1250)
    ir(pg, "/inventario/kardex?producto=2"); foto(pg, "p07_kardex")
    ir(pg, "/inventario/alertas"); foto(pg, "p07_alertas", 900)
    ir(pg, "/inventario/ingreso"); foto(pg, "p07_ingreso")
    ctx.close()

    # ---------------- P08 / P09 caja
    ctx, pg = sesion(b, "dramos")
    ir(pg, "/ventas/nueva"); foto(pg, "p08_nueva_venta", 900)
    fila = pg.locator("tr:has-text('Simba'), li:has-text('Simba'), .tarjeta:has-text('Simba')").locator("button:has-text('Cobrar')")
    (fila.first if fila.count() else pg.get_by_role("button", name="Cobrar").first).click(); pg.wait_for_load_state("networkidle")
    print("   venta:", pg.url, mensaje(pg))
    open(f"{OUT}/venta.html", "w").write(pg.content())
    elegir(pg, "select[name=producto]", "Pro Plan"); pulsar(pg, "Ver lotes", "form:has(button:has-text('Ver lotes'))")
    print("   lotes:", pg.url, pg.locator("input[name=lote]").count())
    pg.locator("form:has(button:has-text('Agregar a la venta')) input[name=cantidad]").fill("1")
    pulsar(pg, "Agregar a la venta"); print("   prod:", mensaje(pg))
    foto(pg, "p08_venta_lineas", 1300)
    pg.fill("input[name=descuento]", "20"); pulsar(pg, "Aplicar descuento"); print("   desc:", mensaje(pg))
    foto(pg, "p08_descuento_rechazado", 1000)
    elegir(pg, "select[name=medio]", "Efectivo"); pg.fill("input[name=monto]", "300"); pulsar(pg, "Registrar pago"); print("   pago:", mensaje(pg))
    foto(pg, "p08_venta_pagada", 1300)
    pulsar(pg, "Confirmar y emitir comprobante"); print("   conf:", pg.url, mensaje(pg))
    foto(pg, "p08_comprobante", 1100)
    ir(pg, "/ventas"); foto(pg, "p08_ventas_lista", 1000)
    ir(pg, "/caja"); foto(pg, "p09_caja", 1300)
    pg.fill("input[name=contado]", "120"); pulsar(pg, "Cerrar turno"); print("   cierre:", mensaje(pg))
    foto(pg, "p09_cierre_sin_observacion", 1300)
    ir(pg, "/caja/historial"); foto(pg, "p09_historial", 1000)
    ctx.close()

    # ---------------- P10 / P12 administración y P11 portal
    ctx, pg = sesion(b, "rparedes")
    ir(pg, "/panel"); foto(pg, "p02_panel_admin", 1250)
    ir(pg, "/reportes"); foto(pg, "p10_reportes", 1250)
    ir(pg, "/reportes/ventas?agrupar=medio"); foto(pg, "p10_reporte_ventas", 1250)
    ir(pg, "/reportes/inventario?vista=rotacion"); foto(pg, "p10_reporte_rotacion")
    ir(pg, "/reportes/clinica"); foto(pg, "p10_reporte_clinica")
    ir(pg, "/admin/usuarios"); foto(pg, "p12_usuarios")
    ir(pg, "/admin/roles"); foto(pg, "p12_roles", 1250)
    ir(pg, "/admin/auditoria"); foto(pg, "p12_auditoria", 1100)
    det = pg.get_by_role("link", name="Ver cambios")
    if det.count(): det.first.click(); pg.wait_for_load_state("networkidle"); foto(pg, "p12_auditoria_detalle", 1000)
    ir(pg, "/admin/accesos"); foto(pg, "p12_accesos")
    ir(pg, "/admin/parametros"); foto(pg, "p12_parametros", 900)
    ir(pg, "/admin/recordatorios"); foto(pg, "p11_recordatorios", 1000)
    ctx.close()
    ctx, pg = sesion(b, "mgonzales")
    ir(pg, "/portal"); foto(pg, "p11_portal", 1250)
    ir(pg, "/portal/mascota?id=2"); foto(pg, "p11_portal_mascota", 1250)
    ctx.close()
    b.close()
json.dump(REG, open(f"{OUT}/registro.json", "w"))
