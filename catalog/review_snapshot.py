"""Create a separate factual review snapshot; no APK endpoints or store publication."""
import argparse, html, json, re
import xml.etree.ElementTree as ET
from pathlib import Path
SLUGS={"Android Oyun Club":"androidoyun-club","APKVision":"apkvision"}
def clean(value,limit=200):
    if not isinstance(value,str):return ""
    value=" ".join(html.unescape(value).split())
    if re.search(r"https?://|<[^>]+>|\\.apk\\b",value,re.I):return ""
    return value[:limit]
def feed_titles(text,source):
    if "<!DOCTYPE" in text.upper() or "<!ENTITY" in text.upper():raise ValueError("Unsafe XML")
    root=ET.fromstring(text)
    local=lambda x:x.tag.rsplit("}",1)[-1]
    result=[]
    for node in root.iter():
        if local(node) not in ("item","entry"):continue
        title=next((c.text for c in node if local(c)=="title"),None)
        name=clean(title)
        if name:result.append({"name":name,"source":source,"installable":False,"recordType":"feed_title"})
        if len(result)>=50:break
    return result
def snapshot(report,run_id):
    if report.get("mode")!="capability_probe_only":raise ValueError("Unexpected report")
    sources=report["sources"]
    if len(sources)!=2 or {s["source"] for s in sources}!=set(SLUGS):raise ValueError("Unexpected sources")
    if any(s.get("status")!="checked_metadata_only" or s.get("html")!="200" or s.get("robots")!="200" for s in sources):
        raise ValueError("Incomplete probe; preserve existing snapshot")
    result={}; index={"schemaVersion":1,"generatedAt":report["generatedAt"],"mode":"metadata_review_only","workflowRun":run_id,"sources":[],"installable":0}
    for s in sources:
        rows=[];seen=set()
        for raw in s.get("reviewRows",[])+s.get("feedTitleRows",[]):
            name=clean(raw.get("name"))
            if not name or name.casefold() in seen:continue
            seen.add(name.casefold())
            row={"name":name,"installable":False,"recordType":"feed_title" if raw.get("recordType")=="feed_title" else "structured_application"}
            for k in ("softwareVersion","operatingSystem","applicationCategory","fileSize"):
                value=clean(raw.get(k),160)
                if value:row[k]=value
            rows.append(row)
        path=SLUGS[s["source"]]+".json"
        count=s["discovered"]["sitemapPageUrls"]
        result[path]={"schemaVersion":1,"source":s["source"],"generatedAt":report["generatedAt"],"status":"metadata_review_only","discoveredPageUrls":count,"reviewPagesChecked":s.get("reviewPagesChecked",0),"total":len(rows),"installable":0,"records":rows}
        index["sources"].append({"name":s["source"],"path":path,"discoveredPageUrls":count,"reviewedRecords":len(rows),"installable":0})
    result["index.json"]=index
    return result
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("report",type=Path)
    p.add_argument("--run-id",type=int,required=True)
    p.add_argument("--output",type=Path,default=Path("catalog/api/review/v1"))
    a=p.parse_args()
    docs=snapshot(json.loads(a.report.read_text()),a.run_id)
    a.output.mkdir(parents=True,exist_ok=True)
    for path,doc in docs.items():
        (a.output/path).write_text(json.dumps(doc,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(docs["index.json"],ensure_ascii=False))
if __name__=="__main__":main()
