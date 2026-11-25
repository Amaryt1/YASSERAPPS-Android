#!/usr/bin/env python3
"""Offline preflight for an explicitly authorized provider JSON export.
Validates metadata only. Does not fetch, verify or publish any binary.
"""
import argparse,json,re
from pathlib import Path
from generate_live_catalog import accept
SOURCES={"Android Oyun Club","APKVision"}
MAX_BYTES=64*1024*1024
def validate(doc):
    if not isinstance(doc,dict) or doc.get("schemaVersion")!=1 or doc.get("source") not in SOURCES:
        raise ValueError("Unsupported source or schema")
    if doc.get("reviewedByOwner") is not True and doc.get("reviewedPublicLicense") is not True:
        raise ValueError("Documented owner or license review required")
    permission=doc.get("permissionUrl")
    if not isinstance(permission,str) or not permission.startswith("https://"):
        raise ValueError("HTTPS permission evidence required")
    items=doc.get("apps")
    if not isinstance(items,list) or len(items)>82000:
        raise ValueError("Invalid apps array")
    ids=set();digests=set();metadata=0;binary_candidates=0
    for i,item in enumerate(items,1):
        row=accept(item,doc["source"],permission)
        if row is None:raise ValueError(f"Invalid release at record {i}")
        if row["id"] in ids:raise ValueError(f"Duplicate ID at record {i}")
        ids.add(row["id"])
        if row["discoveryOnly"]:
            metadata+=1
        else:
            if row["sha256"] in digests:raise ValueError(f"Duplicate binary at record {i}")
            digests.add(row["sha256"])
            binary_candidates+=1
    return {"source":doc["source"],"records":len(items),"metadataOnly":metadata,
            "binaryCandidates":binary_candidates,"verifiedBinaries":0,"published":0,
            "status":"metadata_preflight_only"}
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("export",type=Path)
    a=p.parse_args()
    if a.export.stat().st_size>MAX_BYTES:raise ValueError("Export too large")
    print(json.dumps(validate(json.loads(a.export.read_text(encoding="utf-8"))),ensure_ascii=False))
if __name__=="__main__":main()
