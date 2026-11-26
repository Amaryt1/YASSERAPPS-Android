#!/usr/bin/env python3
"""Owner-only announcement publishing via authenticated GitHub Actions."""
import argparse,json,re,time
from pathlib import Path
def edit(data,operation,id,title="",body="",kind="news",now=None):
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}",id):raise ValueError("Invalid notice ID")
    if data.get("schemaVersion")!=1:raise ValueError("Invalid schema")
    rows={r["id"]:r for r in data["notices"]}
    if operation=="publish":
        if not title.strip() or len(title)>100 or not body.strip() or len(body)>1500 or kind not in ("news","update","addition","maintenance"):raise ValueError("Invalid notice")
        rows[id]=dict(id=id,title=title.strip(),body=body.strip(),type=kind,publishedAt=now if now is not None else int(time.time()*1000),enabled=True)
    elif operation=="hide":
        if id not in rows:raise ValueError("Unknown notice")
        rows[id]["enabled"]=False
    elif operation=="delete":
        if id not in rows:raise ValueError("Unknown notice")
        del rows[id]
    else:raise ValueError("Invalid operation")
    if len(rows)>100:raise ValueError("Limit of 100 notices; delete old notices first")
    return dict(schemaVersion=1,notices=sorted(rows.values(),key=lambda r:(-r["publishedAt"],r["id"])))
def main():
    import os
    path=Path("catalog/notices.json")
    data=edit(json.loads(path.read_text()),os.environ["NOTICE_OPERATION"],os.environ["NOTICE_ID"],os.environ.get("NOTICE_TITLE",""),os.environ.get("NOTICE_BODY",""),os.environ.get("NOTICE_TYPE","news"))
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n")
if __name__=="__main__":main()
