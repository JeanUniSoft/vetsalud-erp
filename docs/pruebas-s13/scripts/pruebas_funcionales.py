"""Regresión funcional automatizada por HTTP: comprueba que las correcciones de seguridad no rompieron el ERP. Uso: pruebas_funcionales.py <salida.json>"""
import json, re, struct, sys, uuid, zlib
from cliente import *
res=[]
def caso(id,mod,nombre,tecnica,ok,obs):
    res.append(dict(id=id,mod=mod,nombre=nombre,tecnica=tecnica,ok=bool(ok),obs=str(obs)[:400])); print(f"{'PASA ' if ok else 'FALLA'} {id} {nombre}\n      {str(obs)[:240]}",flush=True)
def avisos(h): return [re.sub(r"\s+"," ",re.sub(r"<[^>]+>"," ",m)).strip() for m in re.findall(r'class="aviso aviso-[a-z]+"[^>]*>(.*?)</div>',h,re.S)]
def tras(s,r):
    l=r.cab.get("location","").replace("http://localhost:8090",""); return avisos(s.get(l).texto) if l else avisos(r.texto)
INI={"rparedes":"/panel","ltorres":"/panel","cmendoza":"/panel","asalazar":"/panel","jhuaman":"/panel","mccori":"/panel","dramos":"/panel","mgonzales":"/portal"}
ob=[]
for u,d in INI.items():
    r=Sesion().login(u); ob.append((u,r.status,r.cab.get("location")==d))
caso("FUN-01","P01","Los 8 usuarios entran y llegan a su pantalla inicial","Partición de equivalencias",all(o[1]==302 and o[2] for o in ob),f"{sum(o[2] for o in ob)}/8 correctos")
adm=entrar("rparedes")
P=["/panel","/alertas","/clientes","/clientes/nuevo","/clientes/ver?id=1","/mascotas","/mascotas/ver?id=1","/citas","/historia","/consultas/ver?id=1","/inventario","/inventario/kardex","/inventario/alertas","/ventas","/caja","/caja/historial","/reportes","/reportes/ventas","/reportes/inventario","/reportes/clinica","/admin/usuarios","/admin/roles","/admin/auditoria","/admin/accesos","/admin/parametros","/admin/alertas","/admin/recordatorios","/cuenta"]
ob=[(p,adm.get(p).status) for p in P]; malas=[o for o in ob if o[1]!=200]
caso("FUN-02","P02–P12","Humo: las pantallas del administrador cargan","Prueba de humo",not malas,f"{len(P)} pantallas, HTTP distinto de 200: {malas}")
an=Sesion();home=an.get("/");css=an.get("/static/css/app.css");logo=an.get("/static/img/logo.svg")
caso("FUN-03","Filtro","Recursos públicos y raíz siguen funcionando con el filtro corregido","Regresión",home.status==302 and css.status==200 and logo.status==200,f"/ → {home.status} {home.cab.get('location')}; css {css.status}; logo {logo.status}")
d=adm.get("/");caso("FUN-04","Filtro","Con sesión, la raíz abre el panel","Regresión",d.status==200 and "Panel" in d.texto,f"HTTP {d.status}")
rec=entrar("ltorres");doc="4"+uuid.uuid4().int.__str__()[:7]
base={"tipoDocumento":"DNI","nombres":"Lucía","apellidos":"Prueba S13","telefono":"987654321","distrito":"Lima"}
r=rec.post("/clientes/nuevo",{**base,"numDocumento":doc},form="/clientes/nuevo");m=tras(rec,r)
caso("FUN-05","P03","Registrar un cliente con datos válidos","Partición de equivalencias",sql(f"select count(*) from vetsalud.cliente where num_documento='{doc}'")=="1",m[:1])
r=rec.post("/clientes/nuevo",{**base,"numDocumento":doc},form="/clientes/nuevo");m=tras(rec,r)
caso("FUN-06","P03","RN-01: el mismo documento se rechaza","Tabla de decisión",any("xiste" in x for x in m) and sql(f"select count(*) from vetsalud.cliente where num_documento='{doc}'")=="1",m[:1])
for i,(n,txt,esp) in enumerate([("7 dígitos","1234567",False),("9 dígitos","123456789",False),("con letras","1234567A",False)]):
    r=rec.post("/clientes/nuevo",{**base,"numDocumento":txt},form="/clientes/nuevo");m=tras(rec,r)
    caso(f"FUN-0{7+i}","P03",f"DNI inválido ({n}) se rechaza","Valores límite",sql(f"select count(*) from vetsalud.cliente where num_documento='{txt}'")=="0",m[:1])
ci=sql("select id_mascota,id_servicio,id_veterinario from vetsalud.cita order by id_cita limit 1").split("|")
masc,serv,vet=ci;f="2026-11-30";antes=int(sql("select count(*) from vetsalud.cita"))
r=rec.post("/citas/nueva",{"mascota":masc,"servicio":serv,"vet":vet,"fecha":f,"hora":"10:00","motivo":"Prueba sin cruce"},form="/citas/nueva");m=tras(rec,r)
caso("FUN-10","P04","Una cita en un horario libre se registra","Tabla de decisión",int(sql("select count(*) from vetsalud.cita"))==antes+1,m[:1])
r=rec.post("/citas/nueva",{"mascota":masc,"servicio":serv,"vet":vet,"fecha":f,"hora":"10:05","motivo":"Prueba de cruce"},form="/citas/nueva");m=tras(rec,r)
caso("FUN-11","P04","RN-03: otra cita que cruza al mismo veterinario se rechaza","Tabla de decisión",int(sql("select count(*) from vetsalud.cita"))==antes+1,m[:1])
x=adm.get("/reportes/inventario.xlsx");caso("FUN-12","P10","Reporte Excel de inventario (Apache POI) se descarga","Regresión",x.status==200 and x.bytes[:2]==b"PK",f"HTTP {x.status}, {len(x.bytes)} bytes, firma {x.bytes[:2]}, tipo {x.cab.get('content-type','')[:40]}")
por=entrar("mgonzales");p=por.get("/portal");caso("FUN-13","P11","El cliente ve sus mascotas en el portal","Regresión",p.status==200 and "Rocky" in p.texto and "Misha" in p.texto,f"HTTP {p.status}")
def png():
    def ch(t,dd): return struct.pack(">I",len(dd))+t+dd+struct.pack(">I",zlib.crc32(t+dd))
    return b"\x89PNG\r\n\x1a\n"+ch(b"IHDR",struct.pack(">IIBBBBB",1,1,8,2,0,0,0))+ch(b"IDAT",zlib.compress(b"\0\xff\0\0"))+ch(b"IEND",b"")
t=rec.csrf("/mascotas/ver?id=1");b="----b"+uuid.uuid4().hex;cu=(f'--{b}\r\nContent-Disposition: form-data; name="foto"; filename="ok.png"\r\nContent-Type: image/png\r\n\r\n').encode()+png()+f"\r\n--{b}--\r\n".encode()
r=rec.pedir("POST",f"/mascotas/foto?id=1&_csrf={t}",cuerpo=cu,cabeceras={"Content-Type":f"multipart/form-data; boundary={b}"});m=tras(rec,r);f=rec.get("/mascotas/foto?id=1")
caso("FUN-14","P03","RF-08: subir y volver a ver la foto de una mascota","Regresión",f.status==200 and f.cab.get("content-type","").startswith("image/png"),f"{m[:1]}; GET foto HTTP {f.status} {f.cab.get('content-type')}")
for _ in range(5): Sesion().login("jhuaman","mala-clave")
m1=avisos(Sesion().login("jhuaman","mala-clave").texto);m2=avisos(Sesion().login("jhuaman").texto)
caso("FUN-15","P01","Cuenta bloqueada: con la clave correcta se informa el bloqueo; con otra, mensaje genérico","Transición de estados",m1==["Usuario o contraseña incorrectos."] and "bloqueada" in (m2 or [""])[0],f"errada={m1}; correcta={m2}")
sql("update vetsalud.usuario set bloqueado=false,intentos_fallidos=0 where usuario='jhuaman'");caso("FUN-16","P12","Tras el desbloqueo, la cuenta vuelve a entrar","Transición de estados",Sesion().login("jhuaman").status==302,"Desbloqueo aplicado en la tabla usuario")
s=entrar("dramos");t=s.csrf("/panel");r=s.pedir("POST","/logout",datos={"_csrf":t});caso("FUN-17","P01","Cerrar sesión lleva al login con aviso","Regresión",r.status==302 and "salida=1" in r.cab.get("location",""),r.cab.get("location"))
json.dump(res,open(sys.argv[1],"w"),ensure_ascii=False,indent=1);ok=sum(r["ok"] for r in res);print(f"\nTOTAL {len(res)} casos · {ok} pasan · {len(res)-ok} fallan")
