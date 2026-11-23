#!/usr/bin/env python3
"""Download official catalog APK files and verify actual hashes/package metadata.

Runs on a GitHub Action runner with Android SDK aapt installed. Does not publish
or rehost third-party binaries; tests only the owner's curated catalogue URLs.
"""
import hashlib
import json
import re
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

CATALOG = Path("catalog/apps.json")
AAPT = sys.argv[1] if len(sys.argv) > 1 else "aapt"

def verify(app, directory):
    url = app["apkUrl"]
    if not urlparse(url).path.lower().endswith(".apk"):
        print("SKIP split archive requiring mobile-device session:", app["name"])
        return
    print("Checking:", app["name"], url, flush=True)
    request = urllib.request.Request(url, headers={
        "User-Agent": "YASSERAPPS-Catalog-CI/1.0", "Accept": "application/octet-stream"})
    target = directory / (app["id"].replace(".", "_") + ".apk")
    digest = hashlib.sha256()
    size = 0
    with urllib.request.urlopen(request, timeout=80) as response, target.open("wb") as file:
        for block in iter(lambda: response.read(256 * 1024), b""):
            size += len(block)
            if size > 500_000_000:
                raise ValueError("APK exceeds test limit (500 MB)")
            file.write(block)
            digest.update(block)
    assert digest.hexdigest().lower() == app["sha256"].lower(), (
        f"SHA-256 mismatch: {app['name']} ({digest.hexdigest()})")
    output = subprocess.check_output([AAPT, "dump", "badging", str(target)], text=True)
    header = output.splitlines()[0]
    pkg = re.search(r"name='([^']+)'", header)
    code = re.search(r"versionCode='([^']+)'", header)
    assert pkg and code, f"aapt did not parse package info for {app['name']}"
    assert pkg.group(1) == app["packageName"], f"packageName mismatch for {app['name']}: {pkg.group(1)}"
    assert int(code.group(1)) == app["versionCode"], f"versionCode mismatch for {app['name']}"
    print(f"PASS: {app['name']} -- {size} bytes, SHA-256, packageName and versionCode verified", flush=True)
    target.unlink()

def main():
    doc = json.loads(CATALOG.read_text())
    live = [app for app in doc["apps"] if app.get("enabled", True)]
    if not live:
        raise RuntimeError("No enabled apps to verify")
    with tempfile.TemporaryDirectory() as tmp:
        directory = Path(tmp)
        for app in live[:12]:
            if not app.get("sha256"):
                raise RuntimeError("sha256 missing from " + app["name"])
            verify(app, directory)
    print("Catalog verification finished: no unchecked/downloaded binaries retained")

if __name__ == "__main__":
    main()
