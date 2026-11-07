"""Prueba de carga (RNF-01): N usuarios simultáneos, cada uno inicia sesión y hace M operaciones habituales. Uso: prueba_carga.py <salida.json> [N] [M]"""
import json, random, statistics, sys, threading, time
from cliente import *
N=int(sys.argv[2]) if len(sys.argv)>2 else 50; M=int(sys.argv[3]) if len(sys.argv)>3 else 20
CUENTAS=["rparedes"]*20+["ltorres"]*10+["cmendoza"]*5+["mccori"]*5+["dramos"]*10
RUTAS={"rparedes":["/panel","/clientes?q=gon","/citas","/inventario","/ventas","/reportes/ventas","/admin/auditoria"],"ltorres":["/panel","/clientes?q=gon","/citas","/mascotas"],"cmendoza":["/panel","/citas","/historia"],"mccori":["/panel","/inventario","/inventario/alertas"],"dramos":["/panel","/ventas","/caja"]}
med=[];err=[];lock=threading.Lock();bar=threading.Barrier(N)
def usuario(i):
    u=CUENTAS[i%len(CUENTAS)];random.seed(i);s=Sesion();bar.wait()
    try:
        r=s.login(u)
        with lock: med.append(("login",r.seg,r.status))
        for _ in range(M):
            ruta=random.choice(RUTAS[u]);r=s.get(ruta)
            with lock:
                med.append((ruta.split("?")[0],r.seg,r.status))
                if r.status!=200: err.append((ruta,r.status))
            time.sleep(random.uniform(.05,.25))
    except Exception as e:
        with lock: err.append((u,repr(e)[:80]))
t0=time.time();hs=[threading.Thread(target=usuario,args=(i,)) for i in range(N)]
[h.start() for h in hs];[h.join() for h in hs];dur=time.time()-t0
def pct(v,p): v=sorted(v);return v[min(len(v)-1,int(len(v)*p/100))]
t=[m[1] for m in med if m[0]!="login"];l=[m[1] for m in med if m[0]=="login"]
por={}
for ruta,seg,_ in med: por.setdefault(ruta,[]).append(seg)
sal=dict(usuarios=N,operaciones=len(med),duracion=round(dur,1),rps=round(len(med)/dur,1),errores=len(err),det_err=err[:6],p50=round(pct(t,50)*1000),p95=round(pct(t,95)*1000),max=round(max(t)*1000),
 login_p50=round(pct(l,50)*1000),login_p95=round(pct(l,95)*1000),por_ruta={k:dict(n=len(v),p50=round(pct(v,50)*1000),p95=round(pct(v,95)*1000)) for k,v in sorted(por.items())},bajo3s=round(100*sum(x<=3 for x in t)/len(t),2))
json.dump(sal,open(sys.argv[1],"w"),ensure_ascii=False,indent=1);print(json.dumps(sal,ensure_ascii=False)[:900])
