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
def main():
    parser=argparse.ArgumentParser();parser.add_argument("--limit",type=int,default=100);parser.add_argument("--output",default="catalog/generated/direct-index.json");args=parser.parse_args()
    dest=Path(args.output);data=json.loads(dest.read_text()) if dest.exists() else {}
    rows=json.loads(Path("catalog/generated/full-index.json").read_text())["apps"]
    candidates=sorted([a for a in rows if a.get("sourceName")=="Aptoide" and re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*(\.[A-Za-z_][A-Za-z_0-9]*)+",a.get("packageName",""))],key=lambda a:a["id"])
    old={a["packageName"]:a for a in data.get("apps",[]) if int(time.time()*1000)-a.get("verifiedAt",0)<7*86400000}
    # Recheck old versions before indexing new records; round-robin through stable package IDs.
    cursor=data.get("cursor",0)%max(1,len(candidates))
    attempted=0
    for i in range(min(args.limit,len(candidates))):
        row=candidates[(cursor+i)%len(candidates)]
        try:
            response=json.loads(get("https://ws75.aptoide.com/api/7/app/getMeta/package_name="+row["packageName"]))
            if response.get("info",{}).get("status")!="OK":break
            entry=checked(row,response.get("data") or {})
            if entry:old[row["packageName"]]=entry
            else:old.pop(row["packageName"],None)
            attempted+=1
        except urllib.error.HTTPError as e:
            if e.code in (401,403,429):break
            if e.code in (404,410):old.pop(row["packageName"],None);attempted+=1;continue
            break
        except Exception:break
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(dict(schemaVersion=1,cursor=(cursor+attempted)%max(1,len(candidates)),apps=list(old.values())),ensure_ascii=False,separators=(",",":")))
    print("DIRECT_METADATA_CHECKED",attempted,"ELIGIBLE",len(old))
if __name__=="__main__":main()
