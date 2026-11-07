"""Consulta OSV.dev con coordenadas Maven públicas del proyecto (nombre y versión). Uso: auditoria_dependencias.py <salida.json> deps.txt..."""
import json,re,sys,urllib.request
deps=set()
for f in sys.argv[2:]:
    for l in open(f):
        m=re.match(r'\s*([\w.\-]+):([\w.\-]+):jar:([\w.\-]+):(\w+)',l)
        if m: deps.add((m[1],m[2],m[3],m[4]))
deps=sorted(deps)
q={"queries":[{"package":{"ecosystem":"Maven","name":f"{g}:{a}"},"version":v} for g,a,v,_ in deps]}
def post(u,d): return json.load(urllib.request.urlopen(urllib.request.Request(u,json.dumps(d).encode(),{"Content-Type":"application/json"}),timeout=60))
r=post("https://api.osv.dev/v1/querybatch",q)
sal=[]
for (g,a,v,s),x in zip(deps,r["results"]):
    for vul in x.get("vulns",[]):
        d=json.load(urllib.request.urlopen(f"https://api.osv.dev/v1/vulns/{vul['id']}",timeout=60))
        cve=[c for c in d.get("aliases",[]) if c.startswith("CVE")]
        fix=sorted({e["fixed"] for af in d.get("affected",[]) if af["package"]["name"]==f"{g}:{a}" for rg in af.get("ranges",[]) for e in rg.get("events",[]) if "fixed" in e and e["fixed"].split('.')[0]==v.split('.')[0]})
        sal.append(dict(dep=f"{g}:{a}",version=v,alcance=s,id=vul["id"],cve=cve[:1],sev=d.get("database_specific",{}).get("severity","?"),resumen=d.get("summary","")[:110],corregido=fix[:2]))
json.dump(dict(total=len(deps),hallazgos=sal),open(sys.argv[1],"w"),ensure_ascii=False,indent=1)
print(len(deps),"dependencias;",len(sal),"avisos")
for s in sal: print(s["dep"],s["version"],s["alcance"],s["id"],s["cve"],s["sev"],s["corregido"],s["resumen"][:70])
