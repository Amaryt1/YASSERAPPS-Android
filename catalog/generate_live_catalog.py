#!/usr/bin/env python3
"""Only explicitly approved modified Android releases. No ordinary listings."""
import argparse, json, re, time
from pathlib import Path
from urllib.parse import urlsplit
LIMIT=80000
ROOT=Path(__file__).resolve().parent
def https(s):
    try:
        p=urlsplit(s)
        return p.scheme=="https" and bool(p.hostname) and not p.username and not p.password
    except (TypeError,ValueError): return False
def accept(item,source,permission):
    if not isinstance(item,dict) or item.get("modified") is not True: return None
    if not all(isinstance(item.get(k),str) and item[k].strip() for k in ("id","name","packageName","version","modDescription","modEvidenceUrl","publisherUrl")): return None
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,180}",item["id"]) or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+",item["packageName"]): return None
    if not https(item["modEvidenceUrl"]) or not https(item["publisherUrl"]) or not https(permission): return None
    if item.get("category") not in ("app","game"): return None
    code=item.get("versionCode",0)
    if type(code)!=int or code<1: return None
    icon=item.get("iconUrl","")
    if icon and not https(icon): return None
    return {"id":source.lower()+"."+item["id"],"name":item["name"][:200],"packageName":item["packageName"],"category":item["category"],"version":item["version"][:80],"versionCode":code,"sizeBytes":0,"apkUrl":"","iconUrl":icon,"bannerUrl":"","screenshots":[],"summary":item["modDescription"][:500],"description":item["modDescription"][:4000],"sha256":"","signerSha256":"","sourceName":source,"publisherUrl":item["publisherUrl"],"modified":True,"modEvidenceUrl":item["modEvidenceUrl"],"permissionUrl":permission,"updatedAt":int(time.time()*1000),"discoveryOnly":True,"license":str(item.get("license",""))[:200]}
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--output",default=str(ROOT/"generated/full-index.json"))
    p.add_argument("--min-new",type=int,default=500)
    args=p.parse_args()
    out=Path(args.output)
    old=set()
    if out.exists():
        old={r["id"] for r in json.loads(out.read_text()).get("apps",[]) if r.get("modified") is True}
    rows={}
    for file in sorted((ROOT/"approved-mod-feeds").glob("*.json")):
        if file.stat().st_size>64*1024*1024: raise ValueError("Export exceeds limit")
        d=json.loads(file.read_text())
        source=d.get("source","")
        permission=d.get("permissionUrl","")
        if (d.get("reviewedByOwner") is not True and d.get("reviewedPublicLicense") is not True) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9 ._-]{0,79}",source) or not https(permission): raise ValueError("Unapproved source")
        for item in d.get("apps",[]):
            r=accept(item,source,permission)
            if r is not None: rows[r["id"]]=r
    if len(rows)>LIMIT: raise ValueError("Capacity exceeded; do not silently drop sources")
    counts={}
    for r in rows.values(): counts[r["sourceName"]]=counts.get(r["sourceName"],0)+1
    new=len(set(rows)-old)
    report={"total":len(rows),"new":new,"counts":counts,"targetNew":args.min_new,"targetMet":new>=args.min_new}
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps({"schemaVersion":1,"modOnly":True,"generatedAt":int(time.time()*1000),"report":report,"apps":list(rows.values())},ensure_ascii=False,separators=(",",":")))
    print("CATALOG_RESULT",json.dumps(report))
if __name__=="__main__": main()
