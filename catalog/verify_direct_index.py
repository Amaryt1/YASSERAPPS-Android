#!/usr/bin/env python3
"""Bounded public metadata validation. No APK binaries downloaded or rehosted."""
import argparse,json,re,time,urllib.error
from pathlib import Path
from urllib.parse import urlsplit
from generate_market_catalog import get,aptoide,clean,image

def checked(row,detail):
    if detail.get("package")!=row["packageName"]:return None
    if any(k not in detail or detail[k] is not None for k in ("pay","obb","aab")) or detail.get("soft_locks")!=[]:return None
    f=detail.get("file") or {}; sig=f.get("signature") or {}
    if (f.get("malware") or {}).get("rank")!="TRUSTED":return None
    url=f.get("path",""); u=urlsplit(url)
    md5=f.get("md5sum",""); signer=str(sig.get("sha1","")).replace(":","")
    if u.scheme!="https" or u.netloc!="pool.apk.aptoide.com" or not u.path.endswith(".apk") or u.query or u.fragment:return None
    if not re.fullmatch("[a-fA-F0-9]{32}",md5) or not re.fullmatch("[a-fA-F0-9]{40}",signer):return None
    a=aptoide(detail,"games" if row["category"]=="game" else "applications")
    if not a or a["versionCode"]<row["versionCode"]:return None
    media=detail.get("media") or {}
    desc=clean(media.get("description",""),4500)
    a.update(apkUrl=url,legacyMd5=md5,legacySignerSha1=signer,discoveryOnly=False,description=desc,summary=desc[:180],firstSeenAt=row.get("firstSeenAt",0),verifiedAt=int(time.time()*1000))
    return a
# Prioritize recognizable apps that actually exist in the upstream catalogue.
PRIORITY=(
    "com.whatsapp","org.telegram.messenger","com.instagram.android",
    "com.facebook.katana","com.zhiliaoapp.musically","com.snapchat.android",
    "com.google.android.youtube","com.spotify.music","com.discord",
    "com.openai.chatgpt","com.microsoft.copilot","com.lemon.lvoverseas",
    "com.picsart.studio","com.adobe.lrmobile","com.niksoftware.snapseed",
    "com.kiloo.subwaysurf","com.imangi.templerun2","com.supercell.clashofclans",
    "com.supercell.clashroyale","com.king.candycrushsaga",
    "com.miniclip.eightballpool","com.roblox.client","com.tencent.ig",
    "com.dts.freefireth","com.ea.gp.fifamobile","jp.konami.pesam",
    "com.gameloft.android.ANMP.GloftA9HM","com.mojang.minecraftpe",
)
DAY=86400000
def queue(candidates,seen,old,now):
    by_pkg={r["packageName"]:r for r in candidates}
    selected=[];used=set()
    def add(row):
        pkg=row["packageName"]
        if pkg not in used:
            selected.append(row);used.add(pkg)
    def needs(row):
        pkg=row["packageName"]; stamp=seen.get(pkg,0)
        prior=old.get(pkg)
        return not stamp or now-stamp>=7*DAY or (prior is not None and prior["versionCode"]<row["versionCode"] and now-stamp>=DAY)
    for pkg in PRIORITY:
        row=by_pkg.get(pkg)
        if row and needs(row):add(row)
    # Reserve part of every run for stale accepted releases, not just new entries.
    stale=sorted((r for r in candidates if r["packageName"] in old and needs(r)),key=lambda r:seen.get(r["packageName"],0))
    for row in stale[:80]:add(row)
    buckets={kind:sorted((r for r in candidates if r["category"]==kind and needs(r)),key=lambda r:(bool(seen.get(r["packageName"])),seen.get(r["packageName"],0),-r.get("updatedAt",0),r["packageName"])) for kind in ("app","game")}
    from itertools import zip_longest
    for pair in zip_longest(buckets["app"],buckets["game"]):
        for row in pair:
            if row:add(row)
    return selected

def main():
    parser=argparse.ArgumentParser();parser.add_argument("--limit",type=int,default=480);parser.add_argument("--budget-seconds",type=int,default=680);parser.add_argument("--output",default="catalog/generated/direct-index.json");args=parser.parse_args()
    dest=Path(args.output);data=json.loads(dest.read_text()) if dest.exists() else {}
    rows=json.loads(Path("catalog/generated/full-index.json").read_text())["apps"]
    candidates=[a for a in rows if a.get("sourceName")=="Aptoide" and re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*(\.[A-Za-z_][A-Za-z_0-9]*)+",a.get("packageName",""))]
    now=int(time.time()*1000)
    old={a["packageName"]:a for a in data.get("apps",[]) if now-a.get("verifiedAt",0)<14*DAY}
    seen={str(k):int(v) for k,v in data.get("checked",{}).items()}
    for pkg,a in old.items():seen.setdefault(pkg,a.get("verifiedAt",0))
    # Preserve cooldown across CI jobs; do not rotate identity or bypass restrictions.
    retry_after=int(data.get("retryAfter",0))
    if now<retry_after:
        print("DIRECT_COOLDOWN",retry_after,"ELIGIBLE",len(old));return
    pending=queue(candidates,seen,old,now)
    started=time.monotonic();attempted=added=rejected=0
    for row in pending[:max(0,min(480,args.limit))]:
        if time.monotonic()-started>=args.budget_seconds:break
        pkg=row["packageName"]
        try:
            response=json.loads(get("https://ws75.aptoide.com/api/7/app/getMeta/package_name="+pkg))
            if response.get("info",{}).get("status")!="OK":break
            entry=checked(row,response.get("data") or {})
            if entry:
                # Keep the transport compact; full descriptions are fetched on opening details.
                entry["description"]=entry.get("description","")[:420]
                entry["summary"]=entry.get("summary","")[:160]
                if pkg not in old:added+=1
                old[pkg]=entry
            else:
                old.pop(pkg,None);rejected+=1
            seen[pkg]=int(time.time()*1000);attempted+=1
        except urllib.error.HTTPError as e:
            if e.code in (401,403,429):
                delay=3600 if e.code==429 else 86400
                try:delay=max(delay,int(e.headers.get("Retry-After","0")))
                except (TypeError,ValueError):pass
                retry_after=int(time.time()*1000)+delay*1000;break
            if e.code in (404,410):
                old.pop(pkg,None);seen[pkg]=int(time.time()*1000);attempted+=1;rejected+=1;continue
            break
        except Exception as e:
            print("DIRECT_TRANSIENT",type(e).__name__);break
    # The Android transport currently has a 32 MiB ceiling. Keep state bounded,
    # retain all eligible identities while shedding only optional preview text if needed.
    valid=set(r["packageName"] for r in candidates)
    seen={k:v for k,v in seen.items() if k in valid}
    output=dict(schemaVersion=1,checked=seen,retryAfter=retry_after,apps=list(old.values()))
    raw=json.dumps(output,ensure_ascii=False,separators=(",",":")).encode()
    if len(raw)>30*1024*1024:
        for a in output["apps"]:a["description"]="";a["summary"]=a.get("summary","")[:80]
        raw=json.dumps(output,ensure_ascii=False,separators=(",",":")).encode()
    if len(raw)>30*1024*1024:raise RuntimeError("Direct index requires paged transport; refusing partial publication")
    dest.parent.mkdir(parents=True,exist_ok=True)
    temp=dest.with_suffix(".tmp");temp.write_bytes(raw);temp.replace(dest)
    print("DIRECT_METADATA_CHECKED",attempted,"NEW",added,"REJECTED",rejected,"ELIGIBLE",len(old),"PENDING",len(pending)-attempted)
if __name__=="__main__":main()
