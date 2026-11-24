#!/usr/bin/env python3
"""Build the public, read-only YASSERAPPS catalogue API as static JSON."""
import json
import shutil
import tempfile
from collections import Counter
from pathlib import Path

SOURCES={"Aptoide":"aptoide","Uptodown":"uptodown","APKMirror":"apkmirror","TapTap":"taptap"}
PAGE_SIZE=250

def build(data, root):
    rows=data["apps"]
    assert rows and len({r["id"] for r in rows})==len(rows)
    assert all(r["sourceName"] in SOURCES and r["category"] in ("app","game") and r["discoveryOnly"] and not r["apkUrl"] for r in rows)
    stamp=data["generatedAt"]
    def write(path,value):
        target=root/path
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(json.dumps(value,ensure_ascii=False,separators=(",",":"))+"\n",encoding="utf-8")
    def pages(group,items):
        # Stable IDs give deterministic order; generation identifies mixed-cache responses.
        items=sorted(items,key=lambda r:r["id"])
        count=(len(items)+PAGE_SIZE-1)//PAGE_SIZE
        for i in range(count):
            name=f"{group}/{i+1}.json"
            write(name,{"schemaVersion":1,"generatedAt":stamp,"total":len(items),"page":i+1,"pageSize":PAGE_SIZE,"next":f"{i+2}.json" if i+1<count else None,"apps":items[i*PAGE_SIZE:(i+1)*PAGE_SIZE]})
        return {"total":len(items),"pages":count,"first":f"{group}/1.json" if count else None}
    groups={"all":pages("all",rows)}
    for cat in ("app","game"):
        groups[cat]=pages(cat,[r for r in rows if r["category"]==cat])
    for source,slug in SOURCES.items():
        selected=[r for r in rows if r["sourceName"]==source]
        groups[slug]=pages(slug,selected)
        for cat in ("app","game"):
            group=f"{slug}/{cat}"
            groups[group]=pages(group,[r for r in selected if r["category"]==cat])
    write("index.json",{"schemaVersion":1,"generatedAt":stamp,"pageSize":PAGE_SIZE,"total":len(rows),"groups":groups,"counts":dict(Counter(r["sourceName"] for r in rows)),"metadataOnly":True})
    for group,info in groups.items():
        seen=[]
        for i in range(1,info["pages"]+1):
            page=json.loads((root/group/f"{i}.json").read_text())
            assert page["page"]==i and page["generatedAt"]==stamp and len(page["apps"])<=PAGE_SIZE
            seen.extend(r["id"] for r in page["apps"])
        assert len(seen)==info["total"]==len(set(seen))
    assert groups["app"]["total"]+groups["game"]["total"]==len(rows)
    assert sum(groups[s]["total"] for s in SOURCES.values())==len(rows)

def self_test():
    rows=[{"id":str(i),"sourceName":"Aptoide" if i%2 else "TapTap","category":"game" if i%3 else "app","discoveryOnly":True,"apkUrl":""} for i in range(501)]
    with tempfile.TemporaryDirectory() as d:
        root=Path(d)
        build({"apps":rows,"generatedAt":123},root)
        manifest=json.loads((root/"index.json").read_text())
        assert manifest["total"]==501 and manifest["groups"]["all"]["pages"]==3
        assert json.loads((root/"all/3.json").read_text())["next"] is None
        assert manifest["groups"]["apkmirror"]["first"] is None
    print("API self-test passed")

if __name__=="__main__":
    self_test()
    data=json.loads(Path("catalog/generated/index.json").read_text())
    destination=Path("catalog/api/v1")
    destination.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=destination.parent) as d:
        temp=Path(d)/"v1"
        temp.mkdir()
        build(data,temp)
        if destination.exists(): shutil.rmtree(destination)
        temp.rename(destination)
    print("API published locally:",len(data["apps"]),"records")
