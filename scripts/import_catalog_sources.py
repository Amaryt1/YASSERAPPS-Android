#!/usr/bin/env python3
"""Read upstream indexes and stage candidates; never modify the live catalog."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
MAX_BYTES = 150 * 1024 * 1024
HEX = re.compile(r"[0-9a-fA-F]{64}")
LICENSE = re.compile(r"(?:GPL|AGPL|LGPL)-(?:2\.0|2\.1|3\.0)(?:-only|-or-later|\+)?|Apache-2\.0|MIT|BSD-[23]-Clause|MPL-2\.0|ISC|CC0-1\.0|Unlicense")

def fetch(url, limit=MAX_BYTES):
    if urlsplit(url).scheme != "https":
        raise ValueError("HTTPS required")
    with urlopen(Request(url, headers={"User-Agent": "YASSERAPPS-Catalog/1.0"}), timeout=90) as response:
        if urlsplit(response.geturl()).scheme != "https":
            raise ValueError("Insecure redirect")
        data = response.read(limit + 1)
    if len(data) > limit:
        raise ValueError("Response exceeds limit")
    return data

def repo_url(base, name):
    name = name.lstrip("/")
    if not name or any(p in ("", ".", "..") for p in name.split("/")) or any(c in name for c in "?#\\:"):
        raise ValueError("Unsafe repository path")
    return base.rstrip("/") + "/" + quote(name, safe="/-._")

def localized(value, default=""):
    if not isinstance(value, dict):
        return value or default
    return next((value[k] for k in ("ar", "ar-SA", "en-US", "en") if value.get(k)), next(iter(value.values()), default))

def media(base, value):
    obj = localized(value, {})
    return repo_url(base, obj["name"]) if isinstance(obj, dict) and obj.get("name") else ""

def index_for(source):
    base = source["baseUrl"]
    entry = json.loads(fetch(base + "/entry.json", 2 * 1024 * 1024))["index"]
    size = entry["size"]
    if not isinstance(size, int) or not 0 < size <= MAX_BYTES or not HEX.fullmatch(entry["sha256"]):
        raise ValueError("Invalid index metadata")
    raw = fetch(repo_url(base, entry["name"]), size)
    if len(raw) != size or hashlib.sha256(raw).hexdigest() != entry["sha256"].lower():
        raise ValueError("Index size/hash mismatch")
    # HTTPS and hash consistency are NOT signature authentication. Review only.
    return json.loads(raw)

def candidates(source, index, existing):
    base = source["baseUrl"]
    for package, item in sorted(index["packages"].items()):
        if package in existing or not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+", package):
            continue
        meta = item.get("metadata", {})
        license_id = meta.get("license") or ""
        origin = meta.get("sourceCode") or ""
        if not LICENSE.fullmatch(license_id) or urlsplit(origin).scheme != "https":
            continue
        versions = sorted(item.get("versions", {}).values(), key=lambda v: v.get("manifest", {}).get("versionCode", 0), reverse=True)
        for v in versions:
            m, f = v.get("manifest", {}), v.get("file", {})
            name, digest, size = f.get("name", ""), f.get("sha256", ""), f.get("size", 0)
            if v.get("releaseChannels") or v.get("antiFeatures") or m.get("nativecode"):
                continue
            if not name.endswith(".apk") or not HEX.fullmatch(digest) or not isinstance(size, int) or size <= 0 or not m.get("versionName") or not isinstance(m.get("versionCode"), int):
                continue
            try:
                icon = media(base, meta.get("icon"))
                banner = media(base, meta.get("featureGraphic"))
                shots = localized((meta.get("screenshots") or {}).get("phone"), [])
                apk = repo_url(base, name)
                screenshots = [repo_url(base, x["name"]) for x in shots if isinstance(x, dict) and x.get("name")][:6]
            except (ValueError, TypeError, KeyError):
                continue
            if not icon:
                continue
            yield {"id": source["id"] + "." + package, "name": localized(meta.get("name"), package),
                   "packageName": package, "category": "game" if "Games" in meta.get("categories", []) else "app",
                   "version": m["versionName"], "versionCode": m["versionCode"], "sizeBytes": size,
                   "apkUrl": apk, "iconUrl": icon, "bannerUrl": banner, "screenshots": screenshots,
                   "summary": localized(meta.get("summary")), "description": localized(meta.get("description")),
                   "changelog": localized(v.get("whatsNew")), "sha256": digest.lower(),
                   "featured": False, "enabled": False, "source": source["id"],
                   "sourceCode": origin, "license": license_id, "reviewStatus": "pending"}
            existing.add(package)
            break

def self_test():
    source = {"id": "test", "baseUrl": "https://example.org/repo"}
    version = {"manifest": {"versionName": "1", "versionCode": 1}, "file": {"name": "/x.apk", "sha256": "a"*64, "size": 42}}
    item = {"metadata": {"name": {"ar": "Test"}, "license": "MIT", "sourceCode": "https://example.org/code", "icon": {"en-US": {"name": "/x.png"}}}, "versions": {"v": version}}
    sample = {"packages": {"org.example.app": item}}
    seen = set()
    result = list(candidates(source, sample, seen))
    assert len(result) == 1 and result[0]["enabled"] is False
    assert result[0]["apkUrl"] == "https://example.org/repo/x.apk"
    assert not list(candidates(source, sample, seen))
    assert not list(candidates(source, {"packages": {"org.example.app": {**item, "metadata": {**item["metadata"], "license": "Proprietary"}}}}, set()))
    try:
        repo_url(source["baseUrl"], "/../x.apk")
    except ValueError:
        pass
    else:
        raise AssertionError("Unsafe path accepted")
    print("Self-test passed")

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--output", default="source-candidates.json")
    p.add_argument("--limit", type=int, default=100, help="Maximum candidates per source")
    args = p.parse_args()
    if args.self_test:
        self_test()
        return
    config = json.loads((ROOT / "catalog/sources.json").read_text())
    catalog = json.loads((ROOT / "catalog/apps.json").read_text())
    existing = {a["packageName"] for a in catalog["apps"]}
    results, report = [], []
    from itertools import islice
    for source in config["sources"]:
        if not source.get("enabled"):
            report.append({"id": source["id"], "status": "disabled", "reason": source.get("reason")})
            continue
        try:
            rows = list(islice(candidates(source, index_for(source), existing), max(1, min(500, args.limit))))
            results.extend(rows)
            report.append({"id": source["id"], "status": "ok", "candidates": len(rows)})
        except Exception as exc:
            report.append({"id": source["id"], "status": "error", "error": str(exc)})
    Path(args.output).write_text(json.dumps({"schemaVersion": 1, "sources": report, "apps": results}, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False))
    if any(x["status"] == "error" for x in report):
        raise SystemExit(1)

if __name__ == "__main__":
    main()
