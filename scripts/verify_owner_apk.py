#!/usr/bin/env python3
"""Verify only the selected owner-authorized APK before committing its listing."""
import hashlib,json,os, pathlib,re,subprocess,sys,tempfile,urllib.request
from urllib.parse import urlparse
root=json.loads(pathlib.Path("catalog/apps.json").read_text())
identity=json.loads(os.environ["ADMIN_ENTRY_JSON"])["id"]
a=next(x for x in root["apps"] if x["id"]==identity)
tools=pathlib.Path(sys.argv[1])
assert 0<a["sizeBytes"]<=2*1024**3,"APK exceeds CI test limit"
with tempfile.TemporaryDirectory() as directory:
    path=pathlib.Path(directory)/"release.apk";digest=hashlib.sha256();size=0
    with urllib.request.urlopen(urllib.request.Request(a["apkUrl"],headers={"User-Agent":"YASSERAPPS-Owner-Verification/1.0"}),timeout=45) as response,path.open("wb") as f:
        assert urlparse(response.geturl()).scheme=="https","Insecure redirect"
        for block in iter(lambda:response.read(256*1024),b""):
            size+=len(block)
            assert size<=a["sizeBytes"],"File exceeds declared size"
            digest.update(block);f.write(block)
    assert size==a["sizeBytes"] and digest.hexdigest().lower()==a["sha256"].lower(),"Size or hash mismatch"
    info=subprocess.check_output([str(tools/"aapt"),"dump","badging",str(path)],text=True).splitlines()[0]
    assert re.search(r"name='([^']+)'",info).group(1)==a["packageName"]
    assert int(re.search(r"versionCode='([^']+)'",info).group(1))==a["versionCode"]
    cert=subprocess.check_output([str(tools/"apksigner"),"verify","--print-certs",str(path)],text=True)
    values=re.findall(r"certificate SHA-256 digest:\s*([a-fA-F0-9]{64})",cert)
    assert a["signerSha256"].lower() in [v.lower() for v in values],"Signer mismatch"
print("PASS: owner release verified, no binary retained")
