#!/usr/bin/env python3
"""Build the public, read-only YASSERAPPS catalogue API as static JSON."""
import json
import argparse
from itertools import zip_longest
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

def legacy_view(data):
    # Preserve the old Android limit. Keep original ordering when it fits.
    rows=data["apps"]
    if len(rows)>20000:
        buckets=[[r for r in rows if r["sourceName"]==s] for s in ("APKMirror","Uptodown","TapTap","Aptoide")]
        rows=[r for batch in zip_longest(*buckets) for r in batch if r is not None][:20000]
    report=dict(data["report"])
    report["total"]=len(rows)
    report["counts"]={s:sum(r["sourceName"]==s for r in rows) for s in SOURCES}
    report["fullTotal"]=len(data["apps"])
    view={"schemaVersion":1,"generatedAt":data["generatedAt"],"report":report,"apps":rows}
    # The old client enforces both a count and a byte limit.
    def encode():
        return json.dumps(view,ensure_ascii=False,separators=(",",":"))
    encoded=encode()
    while len(encoded.encode("utf-8"))>=25*1024*1024:
        if len(rows)<=1: raise ValueError("Legacy catalog cannot fit")
        rows=rows[:max(1,int(len(rows)*0.9))]
        view["apps"]=rows
        report["total"]=len(rows)
        report["counts"]={s:sum(r["sourceName"]==s for r in rows) for s in SOURCES}
        encoded=encode()
    return encoded

def legacy_test():
    rows=[{"id":str(i),"sourceName":"Aptoide" if i<21000 else "TapTap","category":"game","discoveryOnly":True,"apkUrl":""} for i in range(21005)]
    data={"schemaVersion":1,"generatedAt":123,"report":{"total":len(rows)},"apps":rows}
    v=json.loads(legacy_view(data))
    assert len(v["apps"])==20000 and v["report"]["fullTotal"]==21005
    assert v["report"]["counts"]["TapTap"]==5 and len(data["apps"])==21005
    print("Legacy compatibility self-test passed")

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--input",default="catalog/generated/index.json")
    parser.add_argument("--legacy-output")
    args=parser.parse_args()
    self_test()
    legacy_test()
    data=json.loads(Path(args.input).read_text())
    destination=Path("catalog/api/v1")
    destination.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=destination.parent) as d:
        temp=Path(d)/"v1"
        temp.mkdir()
        build(data,temp)
        if destination.exists(): shutil.rmtree(destination)
        temp.rename(destination)
    if args.legacy_output:
        Path(args.legacy_output).write_text(legacy_view(data),encoding="utf-8")
    print("API published locally:",len(data["apps"]),"records")
