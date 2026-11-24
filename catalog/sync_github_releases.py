"""Sync reviewed open-source Android derivatives via GitHub's official Releases API."""
import base64,hashlib,json,os,re,subprocess,tempfile,urllib.request,urllib.parse
from pathlib import Path
ROOT=Path(__file__).resolve().parent
API="https://api.github.com/"
MAX_APK=128*1024*1024
def api(path):
    headers={"Accept":"application/vnd.github+json","X-GitHub-Api-Version":"2022-11-28","User-Agent":"YASSERAPPS-Catalog"}
    token=os.environ.get("GITHUB_TOKEN")
    if token:headers["Authorization"]="Bearer "+token
    with urllib.request.urlopen(urllib.request.Request(API+path,headers=headers),timeout=30) as response:
        return json.loads(response.read(16*1024*1024))
def select_asset(release,entry):
    if release.get("draft") or release.get("prerelease"):return None
    matches=[]
    for a in release.get("assets",[]):
        url=a.get("browser_download_url","")
        if not url.startswith("https://github.com/"+entry["repository"]+"/releases/download/"):continue
        if not re.fullmatch(entry["assetPattern"],a.get("name","")):continue
        if not re.fullmatch(r"sha256:[a-fA-F0-9]{64}",a.get("digest") or ""):continue
        if not isinstance(a.get("size"),int) or not 0<a["size"]<=MAX_APK:continue
        matches.append(a)
    return matches[0] if len(matches)==1 else None
class ReleaseRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        p=urllib.parse.urlsplit(newurl)
        if p.scheme!="https" or p.hostname not in {"github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","github-releases.githubusercontent.com"} or p.username or p.password or p.port not in (None,443):
            raise ValueError("Unexpected release redirect")
        return super().redirect_request(req,fp,code,msg,headers,newurl)
def binary(asset,entry,sdk):
    with tempfile.TemporaryDirectory() as tmp:
        path=Path(tmp)/"release.apk";n=0;digest=hashlib.sha256()
        req=urllib.request.Request(asset["browser_download_url"],headers={"User-Agent":"YASSERAPPS-Catalog"})
        with urllib.request.build_opener(ReleaseRedirect()).open(req,timeout=60) as res,path.open("wb") as out:
            while chunk:=res.read(65536):
                n+=len(chunk)
                if n>asset["size"] or n>MAX_APK:raise ValueError("Oversized release")
                digest.update(chunk);out.write(chunk)
        sha=digest.hexdigest()
        if n!=asset["size"] or sha!=asset["digest"].split(":")[1].lower():raise ValueError("Release digest mismatch")
        sig=subprocess.check_output([str(sdk/"apksigner"),"verify","--min-sdk-version","26","--verbose","--print-certs",str(path)],text=True,stderr=subprocess.DEVNULL,timeout=90)
        cert=re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})",sig)
        if len(cert)!=1 or not re.search(r"Verified using v[23] scheme.*: true",sig):raise ValueError("Signature not verified")
        info=subprocess.check_output([str(sdk/"aapt"),"dump","badging",str(path)],text=True,stderr=subprocess.DEVNULL,timeout=60)
        m=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",info)
        if not m or m[1]!=entry["packageName"]:raise ValueError("Package mismatch")
        return dict(packageName=m[1],versionCode=int(m[2]),version=m[3],apkUrl=asset["browser_download_url"],sizeBytes=n,sha256=sha,signerSha256=cert[0].lower())
def metadata(entry,repo):
    name=entry["repository"];branch=repo["default_branch"]
    tree=api("repos/"+name+"/git/trees/"+urllib.parse.quote(branch,safe="")+"?recursive=1")
    paths={x["path"] for x in tree.get("tree",[]) if x.get("type")=="blob"}
    prefix="fastlane/metadata/android/";ar=prefix+"ar/";en=prefix+"en-US/"
    ref=tree["sha"]
    def raw(p):return "https://raw.githubusercontent.com/"+name+"/"+ref+"/"+urllib.parse.quote(p,safe="/")
    result={"descriptionAr":entry["descriptionAr"],"translationOrigin":"editorial_arabic","screenshots":[]}
    for locale in (ar,en):
        p=locale+"images/icon.png"
        if p in paths:result["iconUrl"]=raw(p);break
    for locale in (ar,en):
        shots=sorted(x for x in paths if x.startswith(locale+"images/phoneScreenshots/") and x.lower().endswith((".png",".jpg",".jpeg",".webp")))
        if shots:result["screenshots"]=[raw(x) for x in shots[:12]];break
    p=ar+"full_description.txt"
    if p in paths:
        doc=api("repos/"+name+"/contents/"+urllib.parse.quote(p,safe="/")+"?ref="+urllib.parse.quote(ref,safe=""))
        if doc.get("encoding")=="base64":
            value=base64.b64decode(doc["content"]).decode("utf-8","replace").strip()
            if re.search(r"[\u0600-\u06ff]",value):
                result["descriptionAr"]=value[:3500];result["translationOrigin"]="upstream_arabic"
    return result
def main():
    sdk=Path(os.environ["ANDROID_HOME"])/"build-tools"/"35.0.0"
    entries=json.loads((ROOT/"github-release-projects.json").read_text())
    for e in entries:
        try:
            repo=api("repos/"+e["repository"])
            if repo.get("full_name","").casefold()!=e["repository"].casefold() or (repo.get("license") or {}).get("spdx_id")!=e["license"]:raise ValueError("License or project changed")
            release=api("repos/"+e["repository"]+"/releases/latest")
            asset=select_asset(release,e)
            if not asset:raise ValueError("No unambiguous official APK")
            target=ROOT/"approved-mod-feeds"/("github-releases-"+e["id"]+".json")
            old=json.loads(target.read_text())["apps"][0] if target.exists() else {}
            if old.get("sha256")==asset["digest"].split(":")[1] and old.get("apkUrl")==asset["browser_download_url"] and old.get("signerSha256"):
                binary_fields={k:old[k] for k in ("packageName","versionCode","version","apkUrl","sizeBytes","sha256","signerSha256")}
            else:binary_fields=binary(asset,e,sdk)
            if old.get("signerSha256") and old["signerSha256"]!=binary_fields["signerSha256"]:raise ValueError("Signer changed; review required")
            meta=metadata(e,repo)
            item=dict(id=e["id"],name=e["name"],category=e["category"],modified=True,modDescription=meta["descriptionAr"],modEvidenceUrl=repo["html_url"],publisherUrl=release["html_url"],license=e["license"],translationOrigin=meta["translationOrigin"],iconUrl=meta.get("iconUrl",""),screenshots=meta["screenshots"],**binary_fields)
            data=dict(source="GitHub Releases",permissionUrl=repo["html_url"]+"/blob/"+repo["default_branch"]+"/LICENSE",reviewedPublicLicense=True,apps=[item])
            # Repository API established the actual license; use its official license endpoint as evidence.
            data["permissionUrl"]=repo["html_url"]
            target.write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n")
            print("GITHUB_RELEASE_OK",e["id"],item["version"],meta["translationOrigin"],len(item["screenshots"]))
        except Exception as exc:
            print("GITHUB_RELEASE_UNAVAILABLE",e["id"],type(exc).__name__,str(exc)[:120])
if __name__=="__main__":main()
