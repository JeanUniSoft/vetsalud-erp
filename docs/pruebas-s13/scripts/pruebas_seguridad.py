"""Batería de pruebas de seguridad de caja negra (OWASP Top 10:2021 / WSTG) sobre VetSalud ERP en localhost:8090.
Cada caso compara lo observado con lo esperado; nada se escribe a mano. Uso: pruebas_seguridad.py <salida.json>"""
import json, re, statistics, sys, time, uuid, struct, zlib
from cliente import *

USUARIOS = {"rparedes": "ADMINISTRADOR", "ltorres": "RECEPCION", "cmendoza": "VETERINARIO", "jhuaman": "TECNICO",
            "mccori": "FARMACIA", "dramos": "CAJERO", "mgonzales": "CLIENTE"}
P = {"ADMINISTRADOR","RECEPCION","VETERINARIO","TECNICO","FARMACIA","CAJERO"}
C = {"RECEPCION","VETERINARIO","TECNICO","CAJERO","ADMINISTRADOR"}
MATRIZ = {"/panel":P,"/alertas":P,"/clientes":C,"/mascotas":C,"/citas":{"RECEPCION","VETERINARIO","TECNICO","ADMINISTRADOR"},
  "/historia":{"VETERINARIO","TECNICO","ADMINISTRADOR"},"/consultas/ver?id=1":{"VETERINARIO","TECNICO","ADMINISTRADOR"},
  "/inventario":{"FARMACIA","ADMINISTRADOR"},"/ventas":{"CAJERO","ADMINISTRADOR"},"/caja":{"CAJERO","ADMINISTRADOR"},
  "/reportes":{"ADMINISTRADOR"},"/portal":{"CLIENTE"},"/admin/usuarios":{"ADMINISTRADOR"},"/admin/auditoria":{"ADMINISTRADOR"}}
SQLI = ["' OR '1'='1", "' OR 1=1--", "admin'--", "'; DROP TABLE vetsalud.usuario;--", "' UNION SELECT clave_hash FROM vetsalud.usuario--"]
XSS = '<script>alert(1)</script>'
res = []
def avisos(html):
    return [re.sub(r"\s+"," ",re.sub(r"<[^>]+>"," ",m)).strip() for m in re.findall(r'class="aviso aviso-[a-z]+"[^>]*>(.*?)</div>',html,re.S)]
def caso(id, cat, nombre, ok, obs, sev="—"):
    res.append(dict(id=id, cat=cat, nombre=nombre, ok=bool(ok), obs=str(obs)[:600], sev=sev))
    print(f"{'PASA ' if ok else 'FALLA'} {id} [{cat}] {nombre}\n      {str(obs)[:300]}", flush=True)
desbloquear = lambda u: sql(f"update vetsalud.usuario set bloqueado=false,intentos_fallidos=0 where usuario='{u}'")

ses = {u: entrar(u) for u in USUARIOS}

# ---------- A01 Control de acceso
dif = []; n = 0
for u, rol in USUARIOS.items():
    for ruta, roles in MATRIZ.items():
        r = ses[u].get(ruta); n += 1
        if (r.status == 200) != (rol in roles): dif.append(f"{u}{ruta}={r.status}")
caso("SEG-01","A01","Matriz rol × ruta (7 roles × 14 rutas) coincide con PoliticaAcceso",not dif,f"{n} combinaciones, {len(dif)} diferencias {dif[:6]}")
anon = Sesion(); mal = [r for r in MATRIZ if not (anon.get(r).status==302 and "/login" in anon.ultimo.cab.get("location",""))]
caso("SEG-02","A01","Sin sesión, toda ruta privada redirige al login",not mal,f"{len(MATRIZ)} rutas probadas, {len(mal)} sin redirigir {mal}")

TRUCOS = ["/static/..;/admin/usuarios","/static/%2e%2e/admin/usuarios","/static/../admin/usuarios","/login/..;/admin/usuarios",
          "/favicon.ico/..;/admin/usuarios","/recuperar/..;/admin/usuarios"]
ob=[]
for t in TRUCOS:
    r = Sesion().get(t); ob.append((t,r.status,"rparedes" in r.texto))
caso("SEG-03","A01","Sin sesión, una ruta manipulada (..;, %2e%2e) no alcanza servlets privados",
     not any(o[2] for o in ob) and all(o[1] in (302,400,403,404) for o in ob), f"(ruta, HTTP, muestra usuarios reales)={ob}", "Crítica")
TR2 = ["/static/..;/admin/usuarios","/cuenta/..;/admin/usuarios","/cuenta/../admin/usuarios","/logout/%2e%2e/admin/usuarios",
       "/sin-permiso/..;/admin/auditoria","/cuenta/..;/reportes"]
ob=[]; dentro=[]
for t in TR2:
    r = ses["mgonzales"].get(t); ob.append((t,r.status))
    if r.status==200 and ("rparedes" in r.texto or "Auditor" in r.texto or "Reportes" in r.texto) : dentro.append(t)
caso("SEG-04","A01","Un CLIENTE no entra a pantallas de administrador manipulando la ruta",not dentro,
     f"{ob}; con datos de administrador: {dentro}", "Crítica")
idf=sql("select min(id_consulta) from vetsalud.consulta where estado='FIRMADA'")
antes=sql(f"select count(*) from vetsalud.adenda where id_consulta={idf}")
r=ses["mgonzales"].post("/cuenta/..;/consultas/adenda",{"id":idf,"texto":"Adenda falsificada por un cliente"},form="/cuenta")
desp=sql(f"select count(*) from vetsalud.adenda where id_consulta={idf}")
caso("SEG-05","A01","Un CLIENTE no agrega adendas a una historia clínica por ruta manipulada",antes==desp,f"HTTP {r.status}; adendas de la consulta {idf}: {antes} → {desp}","Crítica")
vet=sql("select u.usuario from vetsalud.cita c join vetsalud.usuario u on u.id_usuario=c.id_veterinario where c.id_cita=6")
sql("update vetsalud.cita set estado='EN_ATENCION' where id_cita=6")
sv=entrar(vet);rv=sv.post("/consultas/nueva",{"cita":6,"motivo":"Motivo original"},form="/consultas/nueva?cita=6")
ida=sql("select max(id_consulta) from vetsalud.consulta where estado<>'FIRMADA'")
if ida:
    rc=ses["mgonzales"].post("/cuenta/..;/consultas/datos",{"id":ida,"motivo":"Motivo alterado por cliente"},form="/cuenta")
    mo=sql(f"select motivo from vetsalud.consulta where id_consulta={ida}")
    caso("SEG-43","A01","Un CLIENTE no modifica una consulta abierta por ruta manipulada",mo=="Motivo original",f"consulta {ida}: HTTP {rc.status}; motivo en BD=«{mo}»","Crítica")
else:
    caso("SEG-43","A01","Un CLIENTE no modifica una consulta abierta por ruta manipulada",False,f"No se pudo abrir la consulta de prueba (HTTP {rv.status}, vet={vet})")
ob=[(p,ses['ltorres'].get(p).status) for p in ["/ADMIN/usuarios","/admin%2fusuarios","//admin/usuarios","/admin/usuarios/","/admin/usuarios;x=1"]]
caso("SEG-06","A01","Variantes de mayúsculas, barras y parámetros no evaden el rol",all(s!=200 for _,s in ob),ob)
ob=[(i,ses['mgonzales'].get(f"/portal/mascota?id={i}").status, ses['mgonzales'].get(f"/portal/foto?id={i}").status) for i in (1,3,5,6)]
ajena=[o for o in ob if o[1]==200 and ses['mgonzales'].get(f"/portal/mascota?id={o[0]}").texto.count('Toby')+ses['mgonzales'].ultimo.texto.count('Canela')+ses['mgonzales'].ultimo.texto.count('Luna')+ses['mgonzales'].ultimo.texto.count('Chispa')>0]
propia=ses['mgonzales'].get("/portal/mascota?id=2")
caso("SEG-07","A01","IDOR: un cliente no ve mascotas ni fotos de otro cliente",not ajena and 'Rocky' in propia.texto,f"mascotas ajenas (id, ficha, foto)={ob}; propia id=2 HTTP {propia.status}")
idc=sql("select max(id_consulta) from vetsalud.consulta")
r=ses['jhuaman'].post("/consultas/firmar",{"id":idc},form=f"/consultas/ver?id={idc}")
flash=avisos(ses['jhuaman'].get(r.cab.get('location','/panel').replace('http://localhost:8090','')).texto)
caso("SEG-08","A01","Permiso de acción: un TÉCNICO no puede firmar una consulta",any('CONSULTA_FIRMAR' in m for m in flash),f"HTTP {r.status} → {flash[:1]}")
ob=[(p,Sesion().get(p).status) for p in ["/WEB-INF/web.xml","/WEB-INF/classes/vetsalud.properties","/META-INF/MANIFEST.MF","/static/../WEB-INF/web.xml","/vetsalud.properties"]]
caso("SEG-09","A05","Archivos internos (WEB-INF, properties) no se descargan",all(s in(302,400,403,404) for _,s in ob),ob)

# ---------- A07 Autenticación
s1=Sesion();r=s1.login("ltorres","mala-clave");m1=re.findall(r'aviso-error[^>]*>([^<]+)',r.texto)
s2=Sesion();r2=s2.login("usuario_que_no_existe","mala-clave");m2=re.findall(r'aviso-error[^>]*>([^<]+)',r2.texto)
caso("SEG-10","A07","Mismo mensaje para clave errada y usuario inexistente",m1==m2 and m1,f"{m1} / {m2}")
desbloquear("ltorres")
te=[];ti=[]
for _ in range(4):
    a=Sesion();a.csrf();x=a.post("/login",{"usuario":"cmendoza","clave":"x"});te.append(x.seg);desbloquear("cmendoza")
    b=Sesion();b.csrf();y=b.post("/login",{"usuario":"nadie_"+uuid.uuid4().hex[:6],"clave":"x"});ti.append(y.seg)
me,mi=statistics.median(te)*1000,statistics.median(ti)*1000
caso("SEG-11","A07","El tiempo de respuesta no delata si la cuenta existe",abs(me-mi)<max(15,0.5*max(me,mi)),f"mediana cuenta existente {me:.0f} ms · inexistente {mi:.0f} ms","Media")
ob=[]
for i in range(5): ob.append(Sesion().login("asalazar","mala-clave").status)
b=sql("select bloqueado,intentos_fallidos from vetsalud.usuario where usuario='asalazar'")
r=Sesion().login("asalazar"); ent=r.status==302
msj=re.findall(r'aviso-error[^>]*>([^<]+)',Sesion().login("asalazar","otra-clave-errada").texto)
caso("SEG-12","A07","Bloqueo tras 5 intentos: ni la clave correcta entra",not ent and b.startswith("t"),f"BD (bloqueado|intentos)={b}; clave correcta → HTTP {r.status}")
caso("SEG-13","A07","Una cuenta bloqueada no se distingue de una inexistente",bool(msj) and msj==m2,f"Con clave errada: {msj}","Baja")
desbloquear("asalazar")
ob=[]
for p in SQLI:
    a=Sesion();r=a.login(p,p);ob.append(r.status)
caso("SEG-14","A03","Inyección SQL en el login no autentica",all(s==200 for s in ob),f"{len(SQLI)} cargas, HTTP {ob}")
s=Sesion();s.csrf();antes=s.cookies.get("JSESSIONID");s.login("dramos");desp=s.cookies.get("JSESSIONID")
caso("SEG-15","A07","Fijación de sesión: el id cambia al autenticarse",antes and desp and antes!=desp,f"{antes[:8]}… → {desp[:8]}…")
s=entrar("dramos");v=dict(s.cookies);t=s.csrf("/panel");s.pedir("POST","/logout",datos={"_csrf":t});x=Sesion();x.cookies=v;r=x.get("/panel")
caso("SEG-16","A07","Tras cerrar sesión, la cookie anterior ya no sirve",r.status==302 and "login" in r.cab.get("location",""),f"HTTP {r.status} → {r.cab.get('location')}")
sc=Sesion().get("/login").set_cookie
ck=sc[0] if sc else ""
caso("SEG-17","A05","Cookie de sesión con HttpOnly y SameSite=Strict","HttpOnly" in ck and "SameSite=Strict" in ck,ck.replace(ck.split(";")[0],"JSESSIONID=…"))
caso("SEG-18","A02","Cookie con Secure y cabecera HSTS (requiere HTTPS)","Secure" in ck and "strict-transport-security" in Sesion().get("/login").cab,"Servidor de laboratorio solo con HTTP: sin Secure ni HSTS","Media")
caso_hash=sql("select count(*) filter (where clave_hash like 'pbkdf2_sha256$210000$%'),count(*),count(distinct clave_hash) from vetsalud.usuario")
caso("SEG-19","A02","Claves con hash PBKDF2 y sal única (misma clave, hash distinto)",len(set(caso_hash.split('|')))==1,f"(PBKDF2|total|hash distintos)={caso_hash}")

# ---------- CSRF
s=entrar("ltorres");d={"tipoDocumento":"DNI","numDocumento":"40000001","nombres":"Csrf","apellidos":"Prueba","telefono":"999111222"}
r=s.pedir("POST","/clientes/nuevo",datos=d)
caso("SEG-20","A01","POST sin token CSRF se rechaza",r.status==403,f"HTTP {r.status}")
otro=entrar("dramos").csrf("/panel");r=s.pedir("POST","/clientes/nuevo",datos={**d,"_csrf":otro})
caso("SEG-21","A01","Token CSRF de otra sesión se rechaza",r.status==403,f"HTTP {r.status}")
c=sql("select count(*) from vetsalud.cliente where num_documento='40000001'")
caso("SEG-22","A01","Las peticiones rechazadas no dejaron datos",c=="0",f"filas creadas={c}")

# ---------- A03 Inyección y XSS
s=ses["ltorres"]; ob=[]
base=s.get("/clientes?q=zzzzzz_no_existe").texto
for p in SQLI:
    from urllib.parse import quote
    r=s.get("/clientes?q="+quote(p)); ob.append((r.status,"PSQLException" in r.texto or "syntax error" in r.texto, r.texto.count('/clientes/ver?id=')))
caso("SEG-23","A03","Inyección SQL en la búsqueda de clientes no devuelve datos ni errores",all(o[0]==200 and not o[1] and o[2]==0 for o in ob),f"(HTTP, error SQL visible, filas)={ob}")
tb=sql("select count(*) from information_schema.tables where table_schema='vetsalud' and table_name='usuario'")
caso("SEG-24","A03","La tabla usuario sigue existiendo tras la carga DROP TABLE",tb=="1",f"existe={tb}")
s=ses["rparedes"];ob=[]
for ruta in ["/admin/auditoria?usuario=","/admin/auditoria?operacion=","/reportes/ventas?desde=","/citas?fecha=","/inventario?q=","/historia?q=","/ventas?q="]:
    r=s.get(ruta+quote("' OR 1=1--"));ob.append((ruta.split('?')[0],r.status,"PSQLException" in r.texto or "org.postgresql" in r.texto))
caso("SEG-25","A03","Otros filtros (auditoría, reportes, citas, inventario, historia, ventas) resisten SQLi",all(not o[2] and o[1]<500 for o in ob),ob)
s=ses["ltorres"];doc=str(40000000+int(time.time())%999999)
r=s.post("/clientes/nuevo",{"tipoDocumento":"DNI","numDocumento":doc,"nombres":"Xss"+XSS[:0]+"<script>alert(1)</script>","apellidos":"<img src=x onerror=alert(2)>","telefono":"999111222","direccion":"\"><svg onload=alert(3)>"},form="/clientes/nuevo")
idc=sql(f"select id_cliente from vetsalud.cliente where num_documento='{doc}'")
guardado=bool(idc)
v=[s.get(f"/clientes/ver?id={idc}"),s.get("/clientes?q="+doc),s.get(f"/clientes/editar?id={idc}")] if guardado else []
crudo=[x.status for x in v if "<script>alert(1)" in x.texto or "<img src=x" in x.texto or "<svg onload" in x.texto]
caso("SEG-26","A03","XSS almacenado: el nombre malicioso se muestra escapado en ficha, lista y edición",guardado and not crudo,f"cliente {idc}; vistas={[x.status for x in v]}; con HTML sin escapar={len(crudo)}; ejemplo: {re.findall(r'&lt;script&gt;[^ ]{0,30}',v[0].texto)[:1] if v else ''}")
ob=[]
for ruta in ["/clientes?q=","/inventario?q=","/historia?q=","/ventas?q=","/citas?fecha=","/mascotas?q=","/panel?x="]:
    r=s.get(ruta+quote('"><script>alert(9)</script>'));ob.append((ruta,r.status,'<script>alert(9)' in r.texto))
r=Sesion().login('"><script>alert(9)</script>','x');ob.append(("/login",r.status,'<script>alert(9)' in r.texto))
r=Sesion().get("/login?salida="+quote('<script>alert(9)</script>'));ob.append(("/login?salida",r.status,'<script>alert(9)' in r.texto))
caso("SEG-27","A03","XSS reflejado: los parámetros no se devuelven sin escapar",not any(o[2] for o in ob),f"{len(ob)} puntos; reflejados crudos={[o[0] for o in ob if o[2]]}")
nom="a\r\n2026-01-01|rparedes|operacion=FORJA"
assert len(nom)<=40
r_f=Sesion().login(nom,"x")
import pathlib
logs="".join(p.read_text(errors="replace") for p in pathlib.Path("/tmp/vetsalud_s13_logs").glob("*.log"))
forj=[l for l in logs.splitlines() if l.startswith("2026-01-01|rparedes|operacion=FORJA")]
caso("SEG-28","A09","Inyección en logs: un usuario con salto de línea no falsifica eventos de auditoría",not forj,f"líneas falsificadas en logs={len(forj)}; ejemplo={forj[:1]}","Media")
largo=Sesion().login("x"*300,"y");caso("SEG-42","A09","Un usuario de 300 caracteres recibe el mensaje genérico, sin error de BD",avisos(largo.texto)==["Usuario o contraseña incorrectos."],f"HTTP {largo.status}; {avisos(largo.texto)}","Baja")
caso("SEG-29","A09","Las contraseñas de prueba no aparecen en los logs",CLAVE not in logs and "mala-clave" not in logs,f"{len(logs.splitlines())} líneas revisadas")
ev=sql("select count(*) from vetsalud.acceso_log"); ev2=sql("select count(*) filter (where exitoso=false) from vetsalud.acceso_log")
caso("SEG-30","A09","Los intentos de acceso quedan registrados en acceso_log",int(ev2 or 0)>0,f"registros={ev}, fallidos={ev2}")

# ---------- A05 Configuración / divulgación
r=Sesion().get("/login");req=["x-content-type-options","x-frame-options","content-security-policy","referrer-policy"]
caso("SEG-31","A05","Cabeceras de seguridad presentes",all(h in r.cab for h in req),{h:r.cab.get(h,'FALTA')[:60] for h in req})
csp=r.cab.get("content-security-policy","");caso("SEG-32","A05","CSP sin 'unsafe-inline' ni 'unsafe-eval'","unsafe" not in csp and "script-src 'self'" in csp,csp)
s=ses["rparedes"];r=s.get("/panel");caso("SEG-33","A04","Páginas autenticadas no se guardan en caché",r.cab.get("cache-control")=="no-store",f"Cache-Control={r.cab.get('cache-control')}")
er=[Sesion().get("/no-existe"),Sesion().get("/login/%00"),s.get("/clientes/ver?id=abc"),s.get("/clientes/ver?id=999999999999999"),Sesion().get("/static/..;/admin/usuarios")]
fuga=[x.status for x in er if re.search(r"Exception|\.java:|org\.apache|Apache Tomcat/|postgresql",x.texto)]
caso("SEG-34","A05","Los errores no muestran trazas, versiones ni rutas internas",not fuga,f"HTTP {[x.status for x in er]}; con fuga={fuga}")
caso("SEG-35","A05","La cabecera Server no revela la versión","server" not in er[0].cab or "Tomcat" not in er[0].cab.get("server",""),f"Server={er[0].cab.get('server','(ausente)')}")
ob=[(m,Sesion().pedir(m,"/login").status) for m in ("TRACE","PUT","DELETE","OPTIONS")]
caso("SEG-36","A05","TRACE, PUT y DELETE no están habilitados",all(st in(403,405,501) for m,st in ob if m!="OPTIONS"),ob)

# ---------- Carga de archivos
s=ses["ltorres"];t=s.csrf("/mascotas/ver?id=1")
def subir(nombre,contenido,tipo):
    b="----b"+uuid.uuid4().hex;cu=(f'--{b}\r\nContent-Disposition: form-data; name="foto"; filename="{nombre}"\r\nContent-Type: {tipo}\r\n\r\n').encode()+contenido+f"\r\n--{b}--\r\n".encode()
    r=s.pedir("POST",f"/mascotas/foto?id=1&_csrf={t}",cuerpo=cu,cabeceras={"Content-Type":f"multipart/form-data; boundary={b}"})
    loc=r.cab.get("location","").replace("http://localhost:8090","")
    if loc: return avisos(s.get(loc).texto)
    return [f"HTTP {r.status}"]
png=b"\x89PNG\r\n\x1a\n"+b"\0\0\0\rIHDR"+struct.pack(">II",1,1)+b"\x08\x06\0\0\0"+b"\0"*30
ob={"shell.jsp":subir("shell.jsp",b'<% out.println("x"); %>',"image/png"),"falso.png (HTML)":subir("falso.png",b"<html><script>alert(1)</script>","image/png"),
    "foto.svg":subir("foto.svg",b"<svg onload=alert(1)>","image/svg+xml"),"png.jsp (firma PNG)":subir("x.jsp",png,"image/png"),
    "3 MB":subir("g.png",png+b"0"*3*1024*1024,"image/png"),"../../evil.png":subir("../../evil.png",png,"image/png")}
ok_png=subir("buena.png",png,"image/png")
malo=[k for k,v in ob.items() if k!="../../evil.png" and any("actualizada" in m for m in v)]
caso("SEG-37","A04","Carga de archivos: solo JPG/PNG reales de hasta 2 MB (control: un PNG válido sí entra)",not malo and any("actualizada" in m for m in ok_png),f"rechazos={ {k:(v[0][:70] if v else '') for k,v in ob.items()} }; PNG válido={ok_png[:1]}")
ruta=sql("select foto_ruta from vetsalud.mascota where id_mascota=1")
caso("SEG-38","A01","Nombre de archivo con ../ no escapa de la carpeta de fotos",".." not in ruta,f"foto_ruta guardada={ruta}")

# ---------- Base de datos: privilegio mínimo
APP=("vetsalud_app","App_CambiarEsto_2026")
ob={}
for nombre,q in {"DROP TABLE":"drop table vetsalud.venta","UPDATE auditoría":"update vetsalud.auditoria set tabla='x'","DELETE kardex":"delete from vetsalud.movimiento_inventario","DELETE usuario":"delete from vetsalud.usuario","CREATE ROLE":"create role hacker","TRUNCATE":"truncate vetsalud.acceso_log"}.items():
    ob[nombre]=sql(q,usuario=APP[0],clave=APP[1]).split("\n")[0][:60]
caso("SEG-39","A01","El rol vetsalud_app no puede borrar tablas ni alterar auditoría/kardex",all("ERROR" in v for v in ob.values()),ob)
# ---------- Lógica de negocio
s=ses["dramos"]
s=entrar("mgonzales");t_=s.csrf("/cuenta")
r=s.pedir("POST","/cuenta",datos={"_csrf":t_,"actual":CLAVE,"nueva":"123","confirmacion":"123"})
loc=r.cab.get("location","").replace("http://localhost:8090","");mm=avisos(s.get(loc).texto if loc else r.texto)
sigue=Sesion().login("mgonzales").status==302
caso("SEG-40","A07","Una clave débil (\"123\") se rechaza al cambiarla",sigue and mm,f"{mm[:1]}; la clave original sigue vigente={sigue}")

json.dump(res,open(sys.argv[1],"w"),ensure_ascii=False,indent=1)
ok=sum(r["ok"] for r in res);print(f"\nTOTAL {len(res)} casos · {ok} pasan · {len(res)-ok} fallan")
