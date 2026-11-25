#!/usr/bin/env python3
"""Server-side metadata catalogue. No APKs, credentials, or protected endpoint access."""
import argparse, json, re, time, urllib.request, urllib.parse, urllib.error, xml.etree.ElementTree as ET
from pathlib import Path
from html.parser import HTMLParser
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime

LIMIT=50000
HEADERS={"User-Agent":"YASSERAPPS-Catalog/1.0 (public metadata index)"}
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kw): return None
OPENER=urllib.request.build_opener(NoRedirect())
# Check advertised robots rules before public HTML/RSS/API access.
import urllib.robotparser, threading
_ROBOTS={}
_LAST={}
_STOP=set()
_LOCK=threading.Lock()
def public_allowed(url):
    p=urllib.parse.urlsplit(url)
    if p.scheme!="https" or not p.hostname or p.username or p.password or p.port is not None:
        raise ValueError("Only public HTTPS metadata endpoints")
    origin="https://"+p.hostname
    with _LOCK:
        if origin in _STOP:raise urllib.error.HTTPError(url,429,"Source suspended for this run",{},None)
        if origin not in _ROBOTS:
            try:
                with OPENER.open(urllib.request.Request(origin+"/robots.txt",headers=HEADERS),timeout=12) as r:
                    raw=r.read(512*1024+1)
                    if len(raw)>512*1024 or "<html" in raw[:300].decode("utf-8","replace").lower():raise ValueError("Invalid robots")
                rp=urllib.robotparser.RobotFileParser();rp.parse(raw.decode("utf-8","replace").splitlines())
                _ROBOTS[origin]=rp
            except urllib.error.HTTPError as e:
                if e.code==404:
                    rp=urllib.robotparser.RobotFileParser();rp.parse([]);_ROBOTS[origin]=rp
                else:
                    _STOP.add(origin);raise
            except Exception:
                _STOP.add(origin);raise
        rp=_ROBOTS[origin]
        agent="YASSERAPPS-Catalog"
        if not rp.can_fetch(agent,url):
            raise urllib.error.HTTPError(url,403,"Robots disallowed",{},None)
        delay=max(1,rp.crawl_delay(agent) or rp.crawl_delay("*") or 1)
        rate=rp.request_rate(agent) or rp.request_rate("*")
        if rate and rate.requests:delay=max(delay,rate.seconds/rate.requests)
        if delay>15:raise ValueError("Crawl delay exceeds per-run limit")
        wait=delay-(time.monotonic()-_LAST.get(origin,0))
        if wait>0:time.sleep(wait)
        _LAST[origin]=time.monotonic()
    return origin
def get(url):
    origin=public_allowed(url)
    try:
        with OPENER.open(urllib.request.Request(url,headers=HEADERS),timeout=18) as r:
            b=r.read(4*1024*1024+1)
            if len(b)>4*1024*1024: raise ValueError("response too large")
            return b
    except urllib.error.HTTPError as e:
        if e.code in (401,403,429):
            with _LOCK:_STOP.add(origin)
        raise
def uri(url):
    try:
        p=urllib.parse.urlsplit(url)
        if p.scheme=="https" and p.hostname and not p.username and p.port is None: return p
    except (ValueError,TypeError): pass
def image(source,url):
    p=uri(url)
    if not p: return ""
    h=p.hostname
    ok={"Aptoide":h=="pool.img.aptoide.com","Uptodown":h=="img.utdstc.com","TapTap":bool(re.fullmatch(r"img[0-9]*\.tapimg\.net",h)),"APKMirror":h=="apkmirror.com" or h.endswith(".apkmirror.com")}.get(source,False)
    return url if ok else ""
def link(source,url):
    p=uri(url)
    if not p or p.query or p.fragment: return ""
    h=p.hostname
    if source=="Uptodown" and re.fullmatch(r"[a-z0-9][a-z0-9-]{0,100}\.en\.uptodown\.com",h) and p.path=="/android": return url
    if source=="TapTap" and h=="www.taptap.io" and re.fullmatch(r"/app/[0-9]{1,12}",p.path): return url
    if source=="APKMirror" and h in ("www.apkmirror.com","apkmirror.com") and re.fullmatch(r"/apk/[\w-]+/[\w-]+(?:/[\w.-]+)?/?",p.path): return "https://www.apkmirror.com/"+"/".join(p.path.strip("/").split("/")[:3])+"/"
    return ""
class HTML(HTMLParser):
    def __init__(self): super().__init__(); self.links=[]; self.meta={}; self.h1=False; self.title=[]; self.in_json=False; self.block=[]; self.schemas=[]; self.text=[]
    def handle_starttag(self,t,attrs):
        a=dict(attrs)
        if t=="a" and a.get("href"): self.links.append(a["href"])
        if t=="meta" and a.get("property"): self.meta[a["property"]]=a.get("content","")
        if t=="h1": self.h1=True
        if t=="script" and a.get("type")=="application/ld+json": self.in_json=True; self.block=[]
    def handle_data(self,d):
        self.text.append(d)
        if self.h1: self.title.append(d)
        if self.in_json: self.block.append(d)
    def handle_endtag(self,t):
        if t=="h1": self.h1=False
        if t=="script" and self.in_json:
            self.in_json=False
            try: self.schemas.append(json.loads("".join(self.block)))
            except ValueError: pass
def clean(v,n=200):
    if not isinstance(v,str): return ""
    p=HTML(); p.feed(v); return " ".join(" ".join(p.text).split())[:n]
def software(v):
    if isinstance(v,dict):
        if any(x in str(v.get("@type","")) for x in ("SoftwareApplication","MobileApplication","VideoGame")): return v
        return software(v.get("@graph"))
    if isinstance(v,list):
        for item in v:
            r=software(item)
            if r: return r
    return {}
def entry(source,key,name,url,**kw):
    # Match Catalog.fromJson schema; installation eligibility is resolved on device.
    row=dict(id=key,name=clean(name),packageName="",category="app",version="",versionCode=0,apkUrl="",iconUrl="",bannerUrl="",summary="",description="",changelog="",sha256="",sizeBytes=0,sourceName=source,signerSha256="",license="",updatedAt=0,screenshots=[],discoveryOnly=True,publisherUrl=url,attribution=source)
    row.update(kw)
    return row if row["name"] else None
def update_time(s):
    try: return int(datetime.fromisoformat(s.replace(" ","T")).replace(tzinfo=timezone.utc).timestamp()*1000)
    except (ValueError,AttributeError): return 0
def aptoide(r,group):
    f=r.get("file") or {}; v=f.get("malware")
    if v and v.get("rank")!="TRUSTED": return None
    pkg=r.get("package",""); slug=r.get("uname",""); code=f.get("vercode",0); size=f.get("filesize",r.get("size",0))
    if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*(\.[A-Za-z_][A-Za-z_0-9]*)+",pkg) or not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,62}",slug) or not isinstance(code,int) or code<1 or not isinstance(size,int) or size<1: return None
    return entry("Aptoide","aptoide."+pkg,r.get("name",""),"https://"+slug+".en.aptoide.com/app",packageName=pkg,category="game" if group=="games" else "app",version=clean(f.get("vername",""),80),versionCode=code,sizeBytes=size,iconUrl=image("Aptoide",r.get("icon","")),bannerUrl=image("Aptoide",r.get("graphic","")),updatedAt=update_time(r.get("updated","")))
def chart(source,url,discovered=None):
    p=HTML(); p.feed(get(url).decode("utf-8","replace"))
    if discovered is not None:
        for href in p.links:
            candidate=link(source,urllib.parse.urljoin(url,href))
            if candidate and candidate!=url: discovered.add(candidate)
    data=software(p.schemas)
    name=data.get("name") or " ".join(p.title)
    desc=clean(data.get("description") or p.meta.get("og:description",""),180)
    icon=image(source,data.get("image","")) or image(source,p.meta.get("og:image",""))
    if not name or not desc or not icon: return None
    key="apkmirror:"+urllib.parse.urlsplit(url).path if source=="APKMirror" else source.lower()+":"+url
    return entry(source,key,name,url,category="game" if source=="TapTap" or "game" in str(data.get("applicationCategory","")).lower() else "app",version=clean(data.get("softwareVersion",""),80),iconUrl=icon,summary=desc,description=desc)
def approved_rows(source):
    """Import an explicitly authorized provider export, not a guessed endpoint."""
    file=Path(__file__).resolve().parent/"approved-feeds"/(source.lower()+".json")
    if not file.exists(): return [], "no_authorized_export"
    if file.stat().st_size>32*1024*1024: raise ValueError("Provider export exceeds byte limit")
    data=json.loads(file.read_text(encoding="utf-8"))
    if data.get("source")!=source or not isinstance(data.get("authorization"),str) or not data["authorization"].strip() or not isinstance(data.get("apps"),list):
        raise ValueError("Missing source or authorization record")
    out={}
    for item in data["apps"]:
        if not isinstance(item,dict): continue
        url=link(source,item.get("publisherUrl",""))
        if not url: continue
        name=clean(item.get("name",""),200)
        desc=clean(item.get("description",""),180)
        icon=image(source,item.get("iconUrl",""))
        if not name or not desc or not icon: continue
        key="apkmirror:"+urllib.parse.urlsplit(url).path if source=="APKMirror" else source.lower()+":"+url
        row=entry(source,key,name,url,category="game" if source=="TapTap" or item.get("category")=="game" else "app",version=clean(item.get("version",""),80),iconUrl=icon,summary=desc,description=desc)
        if row: out[key]=row
    return list(out.values()),"authorized_export"

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--output",default="catalog/generated/index.json"); ap.add_argument("--min-new",type=int,default=500); ap.add_argument("--max-pages",type=int,default=120); args=ap.parse_args()
    path=Path(args.output); prior=json.loads(path.read_text()) if path.exists() else {}
    from concurrent.futures import ThreadPoolExecutor, as_completed
    from itertools import zip_longest
    sources=("Aptoide","APKMirror","Uptodown","TapTap")
    rows={a["id"]:a for a in prior.get("apps",[]) if a.get("sourceName") in sources}
    initial=set(rows)
    cursors=dict(prior.get("cursors",{}))
    stats={}
    # Each source owns its rows and cursor snapshot; the main thread merges once.
    def refresh_source(source):
        rows={k:v for k,v in prior_rows.items() if v.get("sourceName")==source}
        cursors=dict(prior_cursors)
        stats={}
        start=time.monotonic()
        cooldown_key=source+":retryAfter"
        def pause_source(code):
            # Respect access restrictions and rate limiting across scheduled runs.
            cursors[cooldown_key]=int(time.time())+(3600 if code==403 else 900)
        def save(row):
            if not row: return
            old=rows.get(row["id"])
            if old is None and len(rows)>=LIMIT: return
            if old is None or row["versionCode"]>old["versionCode"] or (row["versionCode"]==old["versionCode"] and row["updatedAt"]>=old["updatedAt"]): rows[row["id"]]=row
        if source in ("Uptodown","TapTap","APKMirror"):
            imported,status=approved_rows(source)
            if imported:
                for row in imported: save(row)
                return rows,cursors,{source:{"status":status,"accepted":len(imported)}}
            if source=="Uptodown":
                # Terms restrict bulk data mining without authorization.
                return rows,cursors,{source:{"status":"awaiting_authorized_export","retained":len(rows)}}
        if int(cursors.get(cooldown_key,0))>int(time.time()):
            return rows,cursors,{source:{"status":"cooldown","retryAfter":cursors[cooldown_key]}}
        if source=="Aptoide":
            groups=("games","applications"); counts={g:0 for g in groups}; paused=set()
            for n in range(args.max_pages):
                if time.monotonic()-start>=210: break
                group=groups[n%2]; offset=int(cursors.get(group,0))
                if group in paused: continue
                try:
                    r=json.loads(get("https://ws75.aptoide.com/api/7/apps/get/group_name="+group+"/limit=50/offset="+str(offset)+"/mature=false/sort=downloads"))
                    if r.get("info",{}).get("status")!="OK": raise ValueError("API status")
                    d=r["datalist"]
                    for x in d["list"]:
                        f=x.get("file") or {}; verdict=f.get("malware")
                        if verdict and verdict.get("rank")!="TRUSTED": rows.pop("aptoide."+x.get("package",""),None); continue
                        row=aptoide(x,group)
                        if row: save(row); counts[group]+=1
                    nxt=int(d.get("next",-1)); cursors[group]=nxt if offset<nxt<int(d.get("total",2147483647)) and d["list"] else 0
                except urllib.error.HTTPError as e:
                    print("APTOIDE_HTTP",group,e.code,flush=True)
                    if e.code in (403,429): pause_source(e.code); break
                    paused.add(group)
                except Exception as e:
                    print("APTOIDE_FAILED",group,type(e).__name__,flush=True); paused.add(group)
                if len(paused)==len(groups): break
                if len(rows)>=LIMIT or time.monotonic()-start>=210: break
                time.sleep(0.8)
            stats["Aptoide"]=counts
        if source=="APKMirror":
            try:
                root=ET.fromstring(get("https://www.apkmirror.com/feed/"))
                for item in root.findall(".//item"):
                    u=link("APKMirror",item.findtext("link","")); title=item.findtext("title","")
                    if not u: continue
                    try: stamp=int(parsedate_to_datetime(item.findtext("pubDate","")).timestamp()*1000)
                    except Exception: stamp=0
                    match=re.match(r"^(.*?)\s+(\d[\w.+-]*)\s+by\s+.+$",title,re.I)
                    if match:
                        title,version=match.group(1),match.group(2)
                    else:
                        version=""
                    save(entry("APKMirror","apkmirror:"+urllib.parse.urlsplit(u).path,title,u,version=version,updatedAt=stamp))
            except urllib.error.HTTPError as e:
                print("RSS_HTTP",e.code,flush=True)
                if e.code in (403,429):
                    pause_source(e.code)
                    return rows,cursors,{source:{"status":"cooldown","http":e.code}}
            except Exception as e: print("RSS_FAILED",type(e).__name__,flush=True)
        charts={"Uptodown":["https://en.uptodown.com/android/apps/top","https://en.uptodown.com/android/games/top","https://en.uptodown.com/android/latest-updates","https://en.uptodown.com/android/games","https://en.uptodown.com/android/games/newreleases","https://en.uptodown.com/android/newreleases"],"TapTap":["https://www.taptap.io/","https://www.taptap.io/top/","https://www.taptap.io/top/?page=2"]+["https://www.taptap.io/tag/"+tag for tag in ("editor_choice","arcade","strategy","casual","action","sports","racing")],"APKMirror":["https://www.apkmirror.com/uploads/","https://www.apkmirror.com/categories/"]}
        if source in charts:
            pages=charts[source]
            urls={}
            # Only follow actual navigation links on the same public source.
            def navigation(url):
                p=uri(url)
                if not p or p.fragment: return ""
                if source=="APKMirror" and p.hostname=="www.apkmirror.com" and re.fullmatch(r"/uploads/page/[1-9][0-9]{0,5}/?",p.path) and not p.query: return url
                if source=="Uptodown" and p.hostname=="en.uptodown.com" and re.fullmatch(r"/android/(?:apps|games|latest-updates|newreleases)(?:/[a-z0-9-]{1,70}){0,3}/?",p.path) and (not p.query or re.fullmatch(r"page=[1-9][0-9]{0,5}",p.query)): return url
                if source=="TapTap" and p.hostname=="www.taptap.io" and re.fullmatch(r"/(?:(?:top(?:/[a-zA-Z0-9_-]{1,70}){0,2}|tag/[a-zA-Z0-9_-]{1,70})/?)?",p.path) and (not p.query or re.fullmatch(r"page=[1-9][0-9]{0,5}",p.query)): return url
                return ""
            known=list(dict.fromkeys(pages+[u for u in cursors.get(source+":pages",[]) if navigation(u)]))[:2000]
            navpos=int(cursors.get(source+":navpos",0))%max(1,len(known))
            # Refresh seeds and rotate through saved category/pagination links.
            batch=list(dict.fromkeys([known[(navpos+i)%len(known)] for i in range(min(8,len(known)))]))
            blocked=False
            for url in batch:
                if time.monotonic()-start>=210: break
                try:
                    p=HTML(); p.feed(get(url).decode("utf-8","replace"))
                    for h in p.links:
                        valid=link(source,urllib.parse.urljoin(url,h))
                        if valid: urls[valid]=None
                        nav=navigation(urllib.parse.urljoin(url,h))
                        if nav and nav not in known and len(known)<2000: known.append(nav)
                except urllib.error.HTTPError as e:
                    print("CHART_HTTP",source,e.code,flush=True)
                    if e.code in (403,429): pause_source(e.code); blocked=True; break
                except Exception as e: print("CHART_FAILED",source,type(e).__name__,flush=True)
                time.sleep(1)
            cursors[source+":pages"]=known
            cursors[source+":navpos"]=(navpos+8)%max(1,len(known))
            candidates=list(dict.fromkeys([u for u in cursors.get(source+":listings",[]) if link(source,u)]+list(urls)))[:10000]
            cursors[source+":listings"]=candidates
            pos=int(cursors.get(source,0))%max(1,len(candidates)); accepted=0; checked=0
            if blocked: candidates=[]
            # Spend most requests on new listings, with a small rotating refresh
            # budget for older entries. Keep the persistent candidate queue.
            rotated=candidates[pos:]+candidates[:pos]
            indexed={row.get("publisherUrl") for row in rows.values()}
            unseen=[u for u in rotated if u not in indexed]
            existing=[u for u in rotated if u in indexed]
            selected=unseen[:90]+existing[:max(0,100-min(90,len(unseen)))]
            if len(selected)<100: selected+=unseen[90:90+100-len(selected)]
            discovered=set()
            for u in selected:
                if time.monotonic()-start>=210: break
                checked+=1
                try:
                    row=chart(source,u,discovered)
                    if row: save(row); accepted+=1
                except urllib.error.HTTPError as e:
                    print("CHART_HTTP",source,e.code,flush=True)
                    if e.code in (403,429): pause_source(e.code); break
                except Exception as e: print("CHART_DETAIL_FAILED",source,type(e).__name__,flush=True)
                time.sleep(1.2)
            if candidates: cursors[source]=(pos+checked)%len(candidates)
            # Reuse links already returned in fetched detail pages; no extra requests.
            queue=list(dict.fromkeys(cursors[source+":listings"]+sorted(discovered)))[:10000]
            cursors[source+":listings"]=queue
            stats[source]={"links":len(queue),"checked":checked,"accepted":accepted,"relatedDiscovered":len(discovered)}
        return rows,cursors,stats

    prior_rows=dict(rows)
    prior_cursors=dict(cursors)
    outcomes={}
    with ThreadPoolExecutor(max_workers=len(sources)) as pool:
        futures={pool.submit(refresh_source,source):source for source in sources}
        for future in as_completed(futures):
            source=futures[future]
            try:
                outcomes[source]=future.result()
            except Exception as exc:
                stats[source]={"error":type(exc).__name__}
                print("SOURCE_FAILED",source,type(exc).__name__,flush=True)
    additions=[]
    for source in sources:
        if source not in outcomes:
            continue
        updated,source_cursors,source_stats=outcomes[source]
        for key in list(rows):
            if rows[key].get("sourceName")==source and key not in updated:
                del rows[key]
        additions.append([row for key,row in updated.items() if key not in rows])
        for key,row in updated.items():
            if key in rows:
                rows[key]=row
        for key in (("games","applications") if source=="Aptoide" else (source,source+":pages",source+":navpos",source+":listings")):
            if key in source_cursors:
                cursors[key]=source_cursors[key]
        if source+":retryAfter" in source_cursors:
            cursors[source+":retryAfter"]=source_cursors[source+":retryAfter"]
        stats.update(source_stats)
    # Preserve prior entries and distribute remaining capacity across sources.
    for batch in zip_longest(*additions):
        for row in batch:
            if row is not None and len(rows)<LIMIT:
                rows[row["id"]]=row
    new=len(set(rows)-initial); count={s:sum(a["sourceName"]==s for a in rows.values()) for s in ("Aptoide","APKMirror","Uptodown","TapTap","APKPure","LiteAPKs","Aurora")}
    report={"new":new,"total":len(rows),"targetNew":args.min_new,"targetMet":new>=args.min_new,"counts":count,"sources":stats,"unavailable":["APKPure","LiteAPKs","Aurora"]}
    print("CATALOG_RESULT",json.dumps(report),flush=True)
    if not rows: raise SystemExit("No catalogue records; refusing empty publication")
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_suffix(".tmp"); temp.write_text(json.dumps({"schemaVersion":1,"generatedAt":int(time.time()*1000),"report":report,"cursors":cursors,"apps":list(rows.values())},ensure_ascii=False,separators=(",",":")))
    temp.replace(path)
    # Preserve any useful progress even when supply is below the requested goal; report shortfall explicitly.
    if new<args.min_new: print("TARGET_SHORTFALL",new,args.min_new,flush=True)
if __name__=="__main__": main()
