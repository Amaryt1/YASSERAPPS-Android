"""Review factual metadata from public structured application records.
No APK downloads, no publication, no assumed MOD status or permission.
"""
import re
from urllib.parse import urlsplit,urljoin
from provider_formats import decode
from html.parser import HTMLParser

def candidates(urls):
    def rank(url):
        path=urlsplit(url).path.lower()
        # Prefer plausible release pages, exclude obvious taxonomy and asset paths.
        if re.search(r"/(category|categories|tag|tags|author|search|page|wp-content|wp-json)(/|$)",path):return None
        if re.search(r"\.(apk|apks|xapk|zip|xml|gz|png|jpg|webp|js|css)$",path):return None
        if not path.strip("/"):return None
        return (0 if re.search(r"(^|[/_-])(mod|apk|games|apps|game|app)([/_-]|$)",path) else 1,url)
    ranked=[r for u in urls if (r:=rank(u)) is not None]
    return [u for _,u in sorted(ranked)]
def public_link(page,raw):
    if not isinstance(raw,str) or not raw or len(raw)>4096:return None
    try:
        link=urljoin(page,raw); p=urlsplit(link)
        if p.scheme!="https" or p.hostname!=urlsplit(page).hostname or p.username or p.password or p.fragment or p.port not in (None,443):return None
        if p.path.lower().endswith((".xapk",".apkm",".apks")) or link==page:return None
        return link
    except ValueError:return None
class DownloadLinks(HTMLParser):
    def __init__(self):
        super().__init__();self.active=None;self.links=[]
    def handle_starttag(self,tag,attrs):
        if tag=="a":
            a=dict(attrs);self.active=[a,[]]
    def handle_data(self,data):
        if self.active:self.active[1].append(data)
    def handle_endtag(self,tag):
        if tag=="a" and self.active:
            a,parts=self.active;self.active=None
            label=" ".join(parts+[a.get("aria-label") or "",a.get("title") or ""])
            # Public, explicitly labeled APK anchors only; no scripts or inferred endpoints.
            if "download" in a or re.search(r"\b(download|تحميل|تنزيل)\b",label,re.I):
                if re.search(r"\b(original|unmodified|official|obb|xapk|apkm|apks)\b",label,re.I):return
                self.links.append((a.get("href"),bool(re.search(r"\bmod(?:ded)?\b",label,re.I))))
def advertised_links(html,page):
    parser=DownloadLinks();parser.feed(html)
    options={}
    for raw,mod in parser.links:
        link=public_link(page,raw)
        if link:options[link]=options.get(link,False) or mod
    return list(options)
def factual_rows(html,source,url):
    rows=[]
    for item in decode(html,"html-jsonld")["apps"]:
        name=item.get("name")
        if not isinstance(name,str) or not name.strip():continue
        row={"name":name.strip()[:200],"source":source,"pageUrl":url,"status":"review_required","installable":False}
        for key in ("softwareVersion","operatingSystem","applicationCategory","fileSize"):
            val=item.get(key)
            if isinstance(val,str) and val.strip():row[key]=val.strip()[:160]
        # Image URLs are evidence only; they are not fetched or published in store.
        image=item.get("image")
        if isinstance(image,dict):image=image.get("url")
        if isinstance(image,str) and urlsplit(image).scheme=="https":row["imageUrl"]=image[:2048]
        # Only an explicit modified-release title is a MOD candidate.
        if re.search(r"\bmod(?:ded)?\s+apk\b|\bmodded\b",name,re.I):
            row["modClaim"]=True
            raw=item.get("downloadUrl")
            if isinstance(raw,str):
                link=urljoin(url,raw)
                parsed=urlsplit(link)
                if parsed.scheme=="https" and parsed.hostname==urlsplit(url).hostname and not parsed.username and not parsed.password and not parsed.fragment and parsed.port in (None,443) and not parsed.path.lower().endswith((".xapk",".apkm",".apks")):
                    row["explicitApkUrl"]=link
            if not row.get("explicitApkUrl"):
                links=advertised_links(html,url)
                # A page with several download variants cannot be paired safely by guessing.
                if len(links)==1:row["explicitApkUrl"]=links[0]
            cats=str(item.get("applicationCategory","")).lower()
            types=item.get("@type",[])
            if "game" in cats or "VideoGame" in types:row["category"]="game"
            elif "application" in cats or "app" in cats:row["category"]="app"
        rows.append(row)
    return rows
