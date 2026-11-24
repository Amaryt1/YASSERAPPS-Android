#!/usr/bin/env python3
import argparse,json,shutil,re
import xml.etree.ElementTree as ET
from pathlib import Path
from collections import Counter
p=argparse.ArgumentParser()
p.add_argument("--input",default="catalog/generated/full-index.json")
p.add_argument("--legacy-output")
args=p.parse_args()
d=json.loads(Path(args.input).read_text())
rows=d["apps"]
assert d["modOnly"] is True and len(rows)<=82000
assert all(r["modified"] is True and r["modEvidenceUrl"] and r["permissionUrl"] and ((r["discoveryOnly"] and not r["apkUrl"]) or (not r["discoveryOnly"] and r["apkUrl"] and re.fullmatch(r"[0-9a-f]{64}",r["sha256"]) and re.fullmatch(r"[0-9a-f]{64}",r["signerSha256"]))) for r in rows)
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
def xml_catalog(path,source,items,status):
    doc=ET.Element("catalog",{"schemaVersion":"1","modOnly":"true","source":source,"status":status,"generatedAt":str(d["generatedAt"]),"total":str(len(items))})
    for row in sorted(items,key=lambda r:r["id"]):
        entry=ET.SubElement(doc,"app",{"id":row["id"],"category":row["category"],"modified":"true","discoveryOnly":str(row["discoveryOnly"]).lower()})
        for key in ("name","packageName","version","versionCode","iconUrl","bannerUrl","summary","description","publisherUrl","apkUrl","sha256","signerSha256","modEvidenceUrl","permissionUrl","sizeBytes"):
            ET.SubElement(entry,key).text=str(row.get(key,""))
        images=ET.SubElement(entry,"screenshots")
        for url in row.get("screenshots",[]): ET.SubElement(images,"url").text=url
    target=root/path
    target.parent.mkdir(parents=True,exist_ok=True)
    ET.ElementTree(doc).write(target,encoding="utf-8",xml_declaration=True)
    parsed=ET.parse(target).getroot()
    assert int(parsed.get("total"))==len(parsed.findall("app"))==len(items)
    assert {x.get("id") for x in parsed.findall("app")}=={r["id"] for r in items}
groups={"all":pages("all",rows)}
xml_catalog("catalog.xml","YASSERAPPS",rows,"published")
for cat in ("app","game"): groups[cat]=pages(cat,[r for r in rows if r["category"]==cat])
registry=json.loads(Path("catalog/mod-source-candidates.json").read_text())
source_apis={}
for cfg in registry["sources"]:
    slug=cfg["slug"]
    assert re.fullmatch(r"[a-z0-9-]+",slug)
    selected=[row for row in rows if row["sourceName"].casefold()==cfg["name"].casefold()]
    prefix="sources/"+slug
    sg={"all":pages(prefix+"/all",selected)}
    for cat in ("app","game"):
        sg[cat]=pages(prefix+"/"+cat,[row for row in selected if row["category"]==cat])
    status="published" if selected else ("empty" if cfg.get("enabled") else "awaiting_authorized_source")
    entry={"schemaVersion":1,"modOnly":True,"source":cfg["name"],"status":status,"generatedAt":d["generatedAt"],"total":len(selected),"installableMetadata":sum(not row["discoveryOnly"] for row in selected),"groups":sg}
    entry["xml"]="catalog.xml"
    write(prefix+"/index.json",entry)
    xml_catalog(prefix+"/catalog.xml",cfg["name"],selected,status)
    assert sg["app"]["total"]+sg["game"]["total"]==len(selected)
    source_apis[cfg["name"]]={"path":prefix+"/index.json","xml":prefix+"/catalog.xml","total":len(selected),"status":status}
# Factual listing previews are kept separate from the installable release API.
preview_path=Path("catalog/liteapks-review-index.json")
if preview_path.exists() and "LiteAPKs" in source_apis:
    preview=json.loads(preview_path.read_text())
    records=preview["records"]
    assert all(r["installable"] is False and r["status"]=="pending_verified_release" for r in records)
    write("sources/liteapks/preview.json",{"schemaVersion":1,"source":"LiteAPKs","total":len(records),"discoveredPageUrls":preview["discoveredPageUrls"],"reviewedPages":preview.get("reviewedPages",0),"remainingPages":preview.get("remainingPages"),"lastStatus":preview.get("lastStatus","unknown"),"installable":False,"records":records})
    source_apis["LiteAPKs"]["preview"]="sources/liteapks/preview.json"
    source_apis["LiteAPKs"]["previewTotal"]=len(records)
write("index.json",{"schemaVersion":1,"modOnly":True,"metadataOnly":all(r["discoveryOnly"] for r in rows),"installableMetadata":sum(not r["discoveryOnly"] for r in rows),"generatedAt":d["generatedAt"],"pageSize":250,"total":len(rows),"groups":groups,"xml":"catalog.xml","sourceApis":source_apis,"counts":dict(Counter(r["sourceName"] for r in rows))})
if args.legacy_output:
    # Retired clients cannot safely display the new format.
    Path(args.legacy_output).write_text(json.dumps({"schemaVersion":1,"modOnly":True,"generatedAt":d["generatedAt"],"report":{"total":0,"counts":{}},"apps":[]}))
print("MOD API published:",len(rows))
