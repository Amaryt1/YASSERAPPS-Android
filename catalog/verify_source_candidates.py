"""Validate explicitly advertised MOD APK links from public, allowed source pages.
No generated download URLs, access bypass, or inferred APK identity."""
import hashlib,json,re,subprocess,tempfile,time,urllib.request,urllib.robotparser,urllib.parse
from pathlib import Path
from probe_sources import inspect, get, host, NoRedirect, AGENT
from review_metadata import advertised_links
ROOT=Path(__file__).resolve().parent
REPO="https://github.com/Amaryt1/YASSERAPPS-Android/blob/main/catalog/source-permission-notice.md"
MAX_APK=64*1024*1024
def verify(row,sdk):
    if not row.get("modClaim") or row.get("category") not in ("app","game") or not row.get("explicitApkUrl"):return None,"no_explicit_eligible_apk"
    url=row["explicitApkUrl"];domain=host(url)
    status,robots=get("https://"+domain+"/robots.txt",domain)
    if status!="200" or not robots:return None,"robots_unavailable"
    rp=urllib.robotparser.RobotFileParser();rp.parse(robots.splitlines())
    if not rp.can_fetch(AGENT,url):return None,"robots_disallowed"
    delay=max(1,rp.crawl_delay(AGENT) or rp.crawl_delay("*") or 1)
    if delay>10:return None,"crawl_delay"
    time.sleep(delay)
    try:
        with tempfile.TemporaryDirectory() as tmp:
            apk=Path(tmp)/"release.apk"
            req=urllib.request.Request(url,headers={"User-Agent":AGENT+"/2.1","Accept":"application/vnd.android.package-archive, application/octet-stream"})
            digest=hashlib.sha256();n=0
            with urllib.request.build_opener(NoRedirect()).open(req,timeout=25) as resp,apk.open("wb") as f:
                if int(resp.headers.get("Content-Length","0"))>MAX_APK:return None,"apk_too_large"
                while True:
                    buf=resp.read(65536)
                    if not buf:break
                    n+=len(buf)
                    if n>MAX_APK:return None,"apk_too_large"
                    digest.update(buf);f.write(buf)
            with apk.open("rb") as f:
                if f.read(4)!=b"PK\x03\x04":return None,"not_apk_archive_or_landing_page"
            sig=subprocess.check_output([str(sdk/"apksigner"),"verify","--min-sdk-version","26","--verbose","--print-certs",str(apk)],text=True,stderr=subprocess.DEVNULL,timeout=60)
            cert=re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})",sig)
            if len(cert)!=1 or not re.search(r"Verified using v[23] scheme.*: true",sig):return None,"signature_not_verified"
            info=subprocess.check_output([str(sdk/"aapt"),"dump","badging",str(apk)],text=True,stderr=subprocess.DEVNULL,timeout=30)
            m=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",info)
            if not m or not row.get("softwareVersion") or m[3]!=row["softwareVersion"]:return None,"version_mismatch"
            if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+",m[1]):return None,"invalid_package"
            return dict(id=digest.hexdigest()[:32],name=row["name"],packageName=m[1],version=m[3],versionCode=int(m[2]),category=row["category"],modified=True,modDescription="نسخة يصفها المصدر بأنها معدلة. مميزات التعديل التفصيلية غير متحققة.",modEvidenceUrl=row["pageUrl"],publisherUrl=row["pageUrl"],iconUrl=row.get("imageUrl",""),apkUrl=url,sizeBytes=n,sha256=digest.hexdigest(),signerSha256=cert[0].lower()),"verified_binary_identity"
    except Exception as e:return None,type(e).__name__
def main():
    import os
    sdk=Path(os.environ["ANDROID_HOME"])/"build-tools"/"35.0.0"
    registry=json.loads((ROOT/"mod-source-candidates.json").read_text())
    report=[]
    for cfg in registry["sources"]:
        if not cfg.get("ownerReportedPermission"):continue
        # Keep written-consent-only source paused until the documented consent is available.
        result=inspect(cfg["name"],cfg["probeHome"],review_pages=12)
        rows=result.get("reviewRows",[])
        eligible=[x for x in rows if x.get("modClaim") and x.get("explicitApkUrl") and x.get("category") in ("app","game")]
        verified=[];outcomes={}
        for row in eligible[:2]:
            item,status=verify(row,sdk)
            outcomes[status]=outcomes.get(status,0)+1
            if item:verified.append(item)
        # Keep verified candidates isolated until individual rights/mod claims can be reviewed.
        report.append(dict(source=cfg["name"],status=result.get("status"),pages=result.get("reviewPagesChecked",0),metadataRows=len(rows),modClaims=sum(x.get("modClaim",False) for x in rows),explicitApks=len(eligible),verifiedCandidates=verified,outcomes=outcomes))
    out=ROOT/"source-import-review.json"
    out.write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps([{k:v for k,v in r.items() if k!="verifiedCandidates"}|{"verified":len(r["verifiedCandidates"])} for r in report],ensure_ascii=False,indent=2))
if __name__=="__main__":main()
