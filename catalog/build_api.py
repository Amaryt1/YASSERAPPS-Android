#!/usr/bin/env python3
import argparse,json,shutil
from pathlib import Path
from collections import Counter
p=argparse.ArgumentParser()
p.add_argument("--input",default="catalog/generated/full-index.json")
p.add_argument("--legacy-output")
args=p.parse_args()
d=json.loads(Path(args.input).read_text())
rows=d["apps"]
assert d["modOnly"] is True and len(rows)<=80000
assert all(r["modified"] is True and r["discoveryOnly"] and not r["apkUrl"] and r["modEvidenceUrl"] and r["permissionUrl"] for r in rows)
assert len({r["id"] for r in rows})==len(rows)==d["report"]["total"]
root=Path("catalog/api/v1")
if root.exists(): shutil.rmtree(root)
root.mkdir(parents=True)
def write(path,obj):
    f=root/path
    f.parent.mkdir(parents=True,exist_ok=True)
    f.write_text(json.dumps(obj,ensure_ascii=False,separators=(",",":")))
def pages(name,items):
    items=sorted(items,key=lambda r:r["id"])
    n=(len(items)+249)//250
    for i in range(n):
        write(f"{name}/{i+1}.json",{"schemaVersion":1,"modOnly":True,"generatedAt":d["generatedAt"],"total":len(items),"page":i+1,"pageSize":250,"next":f"{i+2}.json" if i+1<n else None,"apps":items[i*250:(i+1)*250]})
    return {"total":len(items),"pages":n,"first":f"{name}/1.json" if n else None}
groups={"all":pages("all",rows)}
for cat in ("app","game"): groups[cat]=pages(cat,[r for r in rows if r["category"]==cat])
write("index.json",{"schemaVersion":1,"modOnly":True,"metadataOnly":True,"generatedAt":d["generatedAt"],"pageSize":250,"total":len(rows),"groups":groups,"counts":dict(Counter(r["sourceName"] for r in rows))})
if args.legacy_output:
    # Retired clients cannot safely display the new format.
    Path(args.legacy_output).write_text(json.dumps({"schemaVersion":1,"modOnly":True,"generatedAt":d["generatedAt"],"report":{"total":0,"counts":{}},"apps":[]}))
print("MOD API published:",len(rows))
