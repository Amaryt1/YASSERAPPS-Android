"""Incremental LiteAPKs public metadata importer. No APK distribution or guessed data."""
import argparse,json,time,urllib.robotparser
from pathlib import Path
from probe_sources import inspect,get,host,AGENT
from review_metadata import candidates,factual_rows
ROOT=Path(__file__).resolve().parent
STATE=ROOT/"liteapks-import-state.json"
PREVIEW=ROOT/"liteapks-review-index.json"
def write(path,obj):
    tmp=path.with_suffix(".tmp")
    tmp.write_text(json.dumps(obj,ensure_ascii=False,separators=(",",":"))+"\n",encoding="utf-8")
    tmp.replace(path)
def preview_row(r):
    if not r.get("modClaim") or r.get("category") not in ("app","game") or not r.get("softwareVersion"):return None
    return dict(name=r["name"],version=r["softwareVersion"],pageUrl=r["pageUrl"],category=r["category"],iconUrl=r.get("imageUrl",""),modClaim=True,status="pending_verified_release",installable=False)
def batch(urls,records,done,rp,delay,limit,budget,fetch=get,sleep=time.sleep,clock=time.monotonic):
    start=clock();attempted=0;status="batch_complete"
    for url in urls:
        if url in done:continue
        if attempted>=limit or clock()-start>=budget:break
        if not rp.can_fetch(AGENT,url):
            done.add(url);continue
        if host(url)!="liteapks.com":continue
        sleep(delay);attempted+=1
        result,html=fetch(url,"liteapks.com")
        if result in ("401","403","429"):
            status="source_denied_or_rate_limited";break
        if result in ("404","410"):
            done.add(url);continue
        if result!="200" or not html:
            status="source_temporarily_unavailable";break
        try:
            for row in factual_rows(html,"LiteAPKs",url):
                rec=preview_row(row)
                if rec:records[url]=rec;break
        except (ValueError,TypeError):
            pass
        done.add(url)
    return attempted,status
def main():
    p=argparse.ArgumentParser();p.add_argument("--limit",type=int,default=150);p.add_argument("--seconds",type=int,default=180);args=p.parse_args()
    cfg=json.loads((ROOT/"mod-source-candidates.json").read_text())["sources"]
    if len(cfg)!=1 or cfg[0]["name"]!="LiteAPKs" or not cfg[0].get("ownerReportedPermission"):raise SystemExit("LiteAPKs permission not recorded")
    s=cfg[0]
    old=json.loads(PREVIEW.read_text()) if PREVIEW.exists() else {"records":[]}
    records={r["pageUrl"]:r for r in old["records"]}
    state=json.loads(STATE.read_text()) if STATE.exists() else {"urls":[],"done":[],"discoveredAt":0}
    now=int(time.time())
    if not state["urls"] or now-state.get("discoveredAt",0)>7*86400:
        result=inspect("LiteAPKs",s["probeHome"],review_pages=0)
        if result.get("status")!="checked_metadata_only" or not result.get("pageIndex"):
            print("LITE_IMPORT_INDEX_UNAVAILABLE",result.get("status"));return
        state["urls"]=candidates(result["pageIndex"])
        state["discoveredPageUrls"]=result["discovered"]["sitemapPageUrls"]
        state["discoveredAt"]=now
    status,robots=get("https://liteapks.com/robots.txt","liteapks.com")
    if status!="200" or not robots or "<html" in robots[:300].lower():
        print("LITE_IMPORT_ROBOTS_UNAVAILABLE");return
    rp=urllib.robotparser.RobotFileParser();rp.parse(robots.splitlines())
    delay=max(1,rp.crawl_delay(AGENT) or rp.crawl_delay("*") or 1)
    if delay>60:print("LITE_IMPORT_CRAWL_DELAY");return
    done=set(state["done"])|set(records)
    attempted,status=batch(state["urls"],records,done,rp,delay,args.limit,args.seconds)
    state["done"]=sorted(done)
    state["lastStatus"]=status
    state["lastAttempted"]=attempted
    state["remaining"]=len(set(state["urls"])-done)
    write(STATE,state)
    write(PREVIEW,dict(source="LiteAPKs",discoveredPageUrls=state["discoveredPageUrls"],reviewedPages=len(done),remainingPages=state["remaining"],lastStatus=status,ownerReportedPermission=True,authorizationConfirmed=False,publishedInstallable=0,records=sorted(records.values(),key=lambda x:x["pageUrl"])))
    print("LITE_IMPORT",json.dumps(dict(attempted=attempted,reviewed=len(done),previews=len(records),remaining=state["remaining"],status=status)))
if __name__=="__main__":main()
