#!/usr/bin/env python3
"""Import only licensed entries from authenticated F-Droid-style indexes."""
import argparse
import copy
import html
import json
import re
from pathlib import Path
from urllib.parse import quote, urlsplit

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "catalog/apps.json"
MAX_CATALOG_BYTES = 1_800_000
MAX_APPS = 2000
HEX = re.compile(r"[0-9a-fA-F]{64}")
PACKAGE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+")
LICENSE = re.compile(r"(?:GPL|AGPL|LGPL)-(?:2\.0|2\.1|3\.0)(?:-only|-or-later|\+)?|Apache-2\.0|MIT|BSD-[23]-Clause|MPL-2\.0|ISC|CC0-1\.0|Unlicense")
PINNED = {
    "fdroid": ("https://f-droid.org/repo", "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB"),
    "izzyondroid": ("https://apt.izzysoft.de/fdroid/repo", "3BF0D6ABFEAE2F401707B6D966BE743BF0EEE49C2561B9BA39073711F628937A"),
}

def repo_url(base, name):
    name = name.lstrip("/")
    if not name or any(p in ("", ".", "..") for p in name.split("/")) or any(c in name for c in "?#\\:"):
        raise ValueError("Unsafe repository path")
    value = base.rstrip("/") + "/" + quote(name, safe="/-._")
    if len(value) > 2048:
        raise ValueError("URL too long")
    return value

def localized(value, default=""):
    if not isinstance(value, dict):
        return value or default
    return next((value[k] for k in ("ar", "ar-SA", "en-US", "en") if value.get(k)), next(iter(value.values()), default))

def clean(value, limit):
    if not isinstance(value, str):
        return ""
    return " ".join(html.unescape(re.sub(r"<[^>]*>", " ", value)).split())[:limit]

def media(base, value):
    obj = localized(value, {})
    return repo_url(base, obj["name"]) if isinstance(obj, dict) and obj.get("name") else ""

def index_for(source):
    from fdroidserver.index import download_repo_index_v2
    base, fingerprint = PINNED[source["id"]]
    if (base, fingerprint) != (source["baseUrl"], source["fingerprint"]):
        raise ValueError("Repository pin mismatch")
    data, _ = download_repo_index_v2(base + "?fingerprint=" + fingerprint, verify_fingerprint=True, timeout=120)
    if not isinstance(data, dict) or not data.get("packages"):
        raise ValueError("Empty or invalid signed index")
    return data

def candidates(source, index):
    base = source["baseUrl"]
    for package, item in sorted(index["packages"].items()):
        if not PACKAGE.fullmatch(package):
            continue
        meta = item.get("metadata", {})
        license_id = meta.get("license") or ""
        origin = meta.get("sourceCode") or ""
        if not isinstance(license_id, str) or not LICENSE.fullmatch(license_id) or not isinstance(origin, str) or urlsplit(origin).scheme != "https" or not urlsplit(origin).hostname:
            continue
        if meta.get("antiFeatures"):
            continue
        key = (source["id"] + "." + package).lower()
        if len(key) > 80:
            continue
        versions = sorted(item.get("versions", {}).values(), key=lambda v: v.get("manifest", {}).get("versionCode", 0), reverse=True)
        for v in versions:
            m, f = v.get("manifest", {}), v.get("file", {})
            name, digest, size = f.get("name", ""), f.get("sha256", ""), f.get("size", 0)
            abi = m.get("nativecode") or []
            signers = (m.get("signer") or {}).get("sha256") or []
            if len(signers) != 1 or not HEX.fullmatch(signers[0]):
                continue
            signer = signers[0].lower()
            preferred = meta.get("preferredSigner")
            if preferred and preferred.lower() != signer:
                continue
            if v.get("releaseChannels") or v.get("antiFeatures") or (abi and "arm64-v8a" not in abi):
                continue
            if (m.get("usesSdk") or {}).get("minSdkVersion", 1) > 35:
                continue
            if not name.lower().endswith(".apk") or not HEX.fullmatch(digest) or type(size) is not int or size <= 0 or not isinstance(m.get("versionName"), str) or not 1 <= len(m["versionName"]) <= 45 or type(m.get("versionCode")) is not int or not 1 <= m["versionCode"] <= 2**31-1:
                continue
            try:
                icon = media(base, meta.get("icon"))
                banner = media(base, meta.get("featureGraphic"))
                shots = localized((meta.get("screenshots") or {}).get("phone"), [])
                apk = repo_url(base, name)
                screenshots = [repo_url(base, x["name"]) for x in shots if isinstance(x, dict) and x.get("name")][:2]
            except (ValueError, TypeError, KeyError):
                continue
            if not icon:
                continue
            summary = clean(localized(meta.get("summary")), 140)
            desc = clean(localized(meta.get("description")), 320)
            yield {"id": key, "name": clean(localized(meta.get("name"), package), 100) or package[:100],
                   "packageName": package, "category": "game" if any(x.lower() in ("game", "games") for x in meta.get("categories", [])) else "app",
                   "version": m["versionName"], "versionCode": m["versionCode"], "sizeBytes": size,
                   "apkUrl": apk, "iconUrl": icon, "bannerUrl": banner, "screenshots": screenshots,
                   "summary": summary, "description": (desc or summary) + "\nSource: " + origin[:400] + " (" + license_id + ").",
                   "changelog": clean(localized(v.get("whatsNew")), 180), "sha256": digest.lower(),
                   "featured": False, "enabled": True, "managedSource": source["id"], "signerSha256": signer,
                   "license": license_id, "sourceCode": origin[:400]}
            break

def serialize(catalog):
    # Compact output leaves room for more entries under the Android 2 MB limit.
    return json.dumps(catalog, ensure_ascii=False, separators=(",", ":")) + "\n"

def synchronize(catalog, groups, batch):
    result = copy.deepcopy(catalog)
    apps = result["apps"]
    positions = {a["packageName"]: i for i, a in enumerate(apps)}
    added = updated = 0
    size = len(serialize(result).encode("utf-8"))
    # Round-robin source lists so one source cannot use the whole capacity first.
    from itertools import zip_longest
    for row in zip_longest(*groups):
        for item in row:
            if item is None:
                continue
            p = positions.get(item["packageName"])
            item_bytes = len(serialize(item).encode("utf-8")) - 1
            if p is not None:
                old = apps[p]
                # Never replace manual entries, change their signing identity, or downgrade.
                if old.get("managedSource") != item["managedSource"] or old.get("signerSha256") != item["signerSha256"] or item["versionCode"] <= old["versionCode"] or not old.get("enabled", True):
                    continue
                new = {**item, "id": old["id"], "featured": old.get("featured", False), "enabled": old.get("enabled", True)}
                delta = len(serialize(new).encode("utf-8")) - len(serialize(old).encode("utf-8"))
                if size + delta <= MAX_CATALOG_BYTES:
                    apps[p] = new
                    size += delta
                    updated += 1
                continue
            if added >= batch or len(apps) >= MAX_APPS or size + item_bytes + 1 > MAX_CATALOG_BYTES:
                continue
            positions[item["packageName"]] = len(apps)
            apps.append(item)
            size += item_bytes + 1
            added += 1
    if len(serialize(result).encode("utf-8")) > MAX_CATALOG_BYTES:
        raise ValueError("Catalog exceeds safe byte limit")
    return result, added, updated

def self_test():
    source = {"id": "test", "baseUrl": "https://example.org/repo"}
    version = {"manifest": {"versionName": "1", "versionCode": 1, "signer": {"sha256": ["b"*64]}}, "file": {"name": "/x.apk", "sha256": "a"*64, "size": 42}}
    item = {"metadata": {"name": {"ar": "Test"}, "license": "MIT", "sourceCode": "https://example.org/code", "icon": {"en-US": {"name": "/x.png"}}}, "versions": {"v": version}}
    rows = list(candidates(source, {"packages": {"org.example.app": item}}))
    assert len(rows) == 1 and rows[0]["apkUrl"] == "https://example.org/repo/x.apk"
    assert not list(candidates(source, {"packages": {"org.example.app": {**item, "metadata": {**item["metadata"], "license": "Proprietary"}}}}))
    catalog = {"apps": [{"packageName": "org.manual.app", "versionCode": 1}]}
    result, a, u = synchronize(catalog, [rows, rows], 10)
    assert a == 1 and u == 0 and len(result["apps"]) == 2 and len(catalog["apps"]) == 1
    result2, a, u = synchronize(result, [[{**rows[0], "versionCode": 2}]], 10)
    assert a == 0 and u == 1 and result2["apps"][1]["versionCode"] == 2
    assert synchronize(result2, [[{**rows[0], "versionCode": 3, "signerSha256": "c"*64}]], 10)[2] == 0
    assert synchronize(catalog, [[{**rows[0], "packageName": "org.manual.app", "versionCode": 3}]], 10)[1:] == (0, 0)
    assert synchronize(catalog, [rows], 0)[1:] == (0, 0)
    print("Self-test passed")

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--output", default="source-candidates.json")
    p.add_argument("--limit", type=int, default=500)
    p.add_argument("--publish", action="store_true")
    args = p.parse_args()
    if args.self_test:
        self_test()
        return
    config = json.loads((ROOT / "catalog/sources.json").read_text())
    catalog = json.loads(CATALOG.read_text())
    groups, report = [], []
    for source in config["sources"]:
        if not source.get("enabled") or source.get("mode") != "sync":
            report.append({"id": source["id"], "status": "disabled"})
            continue
        try:
            rows = list(candidates(source, index_for(source)))
            if not rows:
                raise ValueError("No eligible entries")
            groups.append(rows)
            report.append({"id": source["id"], "status": "ok", "eligible": len(rows)})
        except Exception as exc:
            report.append({"id": source["id"], "status": "error", "error": str(exc)})
    print(json.dumps(report, ensure_ascii=False))
    if not groups or any(x["status"] == "error" for x in report):
        raise SystemExit("Upstream verification failed; catalog not changed")
    result, added, updated = synchronize(catalog, groups, max(0, min(1000, args.limit)))
    output = serialize(result)
    Path(args.output).write_text(output, encoding="utf-8")
    from validate_catalog import validate
    validate(args.output)
    print(f"New: {added}; updated: {updated}; total: {len(result['apps'])}; bytes: {len(output.encode('utf-8'))}")
    if args.publish and (added or updated):
        CATALOG.write_text(output, encoding="utf-8")

if __name__ == "__main__":
    main()
