"""Read only explicitly configured, authorized HTTPS provider JSON feeds."""
import json, os, re, time, urllib.request, urllib.error, urllib.parse
from provider_formats import decode
from pathlib import Path

MAX_PAGE_BYTES=8*1024*1024
MAX_ROWS=82000
MAX_PAGES=400
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None

def origin(url):
    p=urllib.parse.urlsplit(url)
    if p.scheme!="https" or not p.hostname or p.username or p.password or p.fragment or p.port not in (None,443):
        raise ValueError("Invalid HTTPS feed URL")
    return p.hostname.lower(),443

def lookup(obj,path):
    if not isinstance(path,str) or not path or len(path)>120: raise ValueError("Invalid field mapping")
    for part in path.split("."):
        if not isinstance(obj,dict): return None
        obj=obj.get(part)
    return obj

def fetch_json(url,token,kind="json"):
    headers={"Accept":{"json":"application/json","xml":"application/xml","rss":"application/rss+xml","atom":"application/atom+xml","html-jsonld":"text/html"}[kind],"User-Agent":"YASSERAPPS-Catalog/2.0"}
    if token: headers["Authorization"]="Bearer "+token
    req=urllib.request.Request(url,headers=headers)
    with urllib.request.build_opener(NoRedirect()).open(req,timeout=25) as resp:
        raw=resp.read(MAX_PAGE_BYTES+1)
        if len(raw)>MAX_PAGE_BYTES: raise ValueError("Provider page too large")
    return decode(raw.decode("utf-8-sig"),kind)

def read_source(cfg,fetch=fetch_json,sleep=time.sleep):
    name=cfg["name"]
    if cfg.get("enabled") is not True: return []
    if cfg.get("authorizationConfirmed") is not True or not cfg.get("permissionUrl"):
        raise ValueError("Authorization required")
    origin(cfg["permissionUrl"])
    first=cfg["apiUrl"]
    host=origin(first)
    token_env=cfg.get("tokenEnv","")
    if token_env and not re.fullmatch(r"YASSER_FEED_[A-Z0-9_]+_TOKEN",token_env):
        raise ValueError("Invalid secret environment name")
    token=os.environ.get(token_env,"") if token_env else ""
    if token_env and not token: raise ValueError("Required provider token missing")
    fields=cfg.get("fields",{})
    if not isinstance(fields,dict): raise ValueError("Invalid mapping")
    items_path=cfg.get("itemsPath","apps")
    next_path=cfg.get("nextPath","next")
    delay=max(1.0,float(cfg.get("delaySeconds",1)))
    if delay>60: raise ValueError("Invalid provider delay")
    kind=cfg.get("format","json")
    if kind not in ("json","xml","rss","atom","html-jsonld"):raise ValueError("Unsupported feed format")
    if kind!="json" and not fields:raise ValueError("Explicit mapping required for non-JSON feeds")
    current=first
    seen=set()
    rows=[]
    for page in range(MAX_PAGES):
        if origin(current)!=host or current in seen: raise ValueError("Invalid or repeated pagination link")
        seen.add(current)
        obj=fetch(current,token,kind) if kind!="json" else fetch(current,token)
        items=lookup(obj,items_path)
        if not isinstance(items,list): raise ValueError("Invalid provider JSON records")
        for row in items:
            if not isinstance(row,dict): continue
            if fields:
                row={key:lookup(row,path) for key,path in fields.items()}
                # XML text fields have no native integer/boolean types.
                # Convert exact explicit values only; never infer version or MOD status.
                for key in ("versionCode","sizeBytes"):
                    value=row.get(key)
                    if isinstance(value,str) and re.fullmatch(r"[0-9]{1,12}",value):row[key]=int(value)
                if isinstance(row.get("modified"),str):
                    value=row["modified"].strip().lower()
                    if value in ("true","false"):row["modified"]=value=="true"
            rows.append({key:value for key,value in row.items() if value is not None})
            if len(rows)>MAX_ROWS: raise ValueError("Provider exceeds catalog capacity")
        next_url=lookup(obj,next_path) if next_path else None
        if next_url in (None,""): return rows
        if not isinstance(next_url,str): raise ValueError("Provider next link must be URL or null")
        current=urllib.parse.urljoin(current,next_url)
        if origin(current)!=host: raise ValueError("Cross-origin pagination refused")
        sleep(delay)
    raise ValueError("Provider pagination limit exceeded")

def load_remote(registry):
    data=json.loads(Path(registry).read_text(encoding="utf-8"))
    for cfg in data["sources"]:
        if cfg.get("enabled") is not True:
            print("FEED_DISABLED",cfg["name"])
            continue
        # Fail closed: preserve last published snapshot if any enabled feed fails.
        # Never print URLs, query strings, tokens or response bodies.
        try:
            rows=read_source(cfg)
        except Exception as e:
            print("FEED_FAILED",cfg["name"],type(e).__name__,getattr(e,"code",""))
            raise RuntimeError("Authorized provider refresh failed: "+cfg["name"]) from None
        print("FEED_FETCHED",cfg["name"],len(rows))
        yield cfg["name"],cfg["permissionUrl"],rows
