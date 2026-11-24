"""Non-invasive public endpoint capability checks. No APK requests or catalog publishing."""
import concurrent.futures,json,re,time,urllib.request,urllib.error,urllib.parse,urllib.robotparser,xml.etree.ElementTree as ET
from html.parser import HTMLParser
from pathlib import Path
AGENT="YASSERAPPS-Catalog"
MAX_BYTES=2*1024*1024
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None
def host(url):
    p=urllib.parse.urlsplit(url)
    if p.scheme!="https" or not p.hostname or p.username or p.password or p.port not in (None,443):
        raise ValueError("Invalid public URL")
    return p.hostname.lower()
def get(url,allowed_host):
    if host(url)!=allowed_host: raise ValueError("Cross-origin request refused")
    req=urllib.request.Request(url,headers={"User-Agent":AGENT+"/2.1","Accept":"text/html, application/xml, application/json, text/plain;q=0.9"})
    try:
        with urllib.request.build_opener(NoRedirect()).open(req,timeout=12) as res:
            raw=res.read(MAX_BYTES+1)
            if len(raw)>MAX_BYTES: return "too_large",None
            return str(res.status),raw.decode("utf-8","replace")
    except urllib.error.HTTPError as e: return str(e.code),None
    except (OSError,ValueError) as e: return type(e).__name__,None
class Page(HTMLParser):
    def __init__(self):
        super().__init__();self.links=[];self.feeds=[];self.schemas=[];self.og=set();self.buf=None
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=="a" and a.get("href"):self.links.append(a["href"])
        if tag=="link" and a.get("href"):
            typ=a.get("type","").lower()
            if typ in ("application/rss+xml","application/atom+xml","application/json"):self.feeds.append((typ,a["href"]))
        if tag=="meta" and a.get("property","").startswith("og:"):self.og.add(a["property"])
        if tag=="script" and a.get("type")=="application/ld+json":self.buf=[]
    def handle_data(self,data):
        if self.buf is not None:self.buf.append(data)
    def handle_endtag(self,tag):
        if tag=="script" and self.buf is not None:
            try:self.schemas.append(json.loads("".join(self.buf)))
            except (ValueError,TypeError):pass
            self.buf=None
def parse_xml(text):
    if "<!DOCTYPE" in text.upper() or "<!ENTITY" in text.upper():raise ValueError("Unsafe XML")
    root=ET.fromstring(text)
    kind=root.tag.rsplit("}",1)[-1]
    if kind not in ("sitemapindex","urlset","rss","feed"):return "unknown",0
    child={"sitemapindex":"sitemap","urlset":"url","rss":"item","feed":"entry"}[kind]
    return kind,sum(x.tag.rsplit("}",1)[-1]==child for x in root.iter())
def inspect(name,home,fetch=get):
    domain=host(home);origin="https://"+domain
    result={"source":name,"home":home,"robots":"unchecked","html":"unchecked","discovered":{"sitemaps":0,"rss":0,"atom":0,"json":0,"htmlLinks":0,"jsonLd":0,"openGraph":0},"documents":[],"published":0}
    status,txt=fetch(origin+"/robots.txt",domain);result["robots"]=status
    # Stop on any denial, rate limit, redirect or unknown robots policy.
    if status!="200" or txt is None or "<html" in txt[:300].lower():
        result["status"]="robots_unavailable";return result
    rp=urllib.robotparser.RobotFileParser();rp.parse(txt.splitlines())
    delay=max(1,rp.crawl_delay(AGENT) or rp.crawl_delay("*") or 1)
    if delay>10:result["status"]="crawl_delay_too_high_for_probe";return result
    if not rp.can_fetch(AGENT,home):result["status"]="robots_disallowed";return result
    time.sleep(delay)
    status,body=fetch(home,domain);result["html"]=status
    if status!="200" or body is None:result["status"]="homepage_unavailable";return result
    p=Page();p.feed(body);counts=result["discovered"]
    def same(url):
        try:return host(url)==domain
        except ValueError:return False
    counts["htmlLinks"]=len({urllib.parse.urljoin(home,x) for x in p.links if same(urllib.parse.urljoin(home,x))})
    counts["jsonLd"]=len(p.schemas);counts["openGraph"]=len(p.og)
    candidates=[("sitemap",line.split(":",1)[1].strip()) for line in txt.splitlines() if line.lower().startswith("sitemap:")]
    candidates += [(typ,urllib.parse.urljoin(home,url)) for typ,url in p.feeds]
    # Only publicly advertised document URLs; no guessed private endpoints.
    seen=set()
    for typ,url in candidates[:5]:
        if url in seen or not same(url) or not rp.can_fetch(AGENT,url):continue
        seen.add(url)
        time.sleep(delay)
        status,content=fetch(url,domain)
        info={"type":typ,"status":status,"records":0}
        if status in ("401","403","429"):result["documents"].append(info);break
        if status=="200" and content is not None:
            try:
                if typ=="application/json":
                    val=json.loads(content)
                    info["records"]=len(val) if isinstance(val,list) else 0
                    counts["json"]+=1
                else:
                    kind,num=parse_xml(content);info["type"]=kind;info["records"]=num
                    key={"sitemapindex":"sitemaps","urlset":"sitemaps","rss":"rss","feed":"atom"}.get(kind)
                    if key:counts[key]+=1
            except (ValueError,ET.ParseError):info["status"]="invalid_document"
        result["documents"].append(info)
    result["status"]="checked_metadata_only"
    return result
def main():
    cfg=json.loads(Path("catalog/mod-source-candidates.json").read_text())
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        rows=list(pool.map(lambda s:inspect(s["name"],s["probeHome"]),cfg["sources"]))
    out={"generatedAt":int(time.time()*1000),"mode":"capability_probe_only","sources":rows}
    path=Path("catalog/source-probe-report.json");path.write_text(json.dumps(out,indent=2))
    print(json.dumps(out,indent=2))
if __name__=="__main__":main()
