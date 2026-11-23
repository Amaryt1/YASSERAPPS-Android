#!/usr/bin/env python3
"""Generate a pinned YASSERAPPS release feed ONLY after an APK has been signed.

The checksum is computed from the actual published, signed binary and the
download URL must point to the owner's GitHub release.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    args = parser.parse_args()
    if not args.apk.is_file() or args.apk.stat().st_size < 1000:
        raise SystemExit("Signed APK missing or empty")
    if not re.fullmatch(r"v[0-9][0-9A-Za-z._-]*", args.tag):
        raise SystemExit("Invalid release tag")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
        raise SystemExit("Invalid release repository")
    if args.version_code < 1:
        raise SystemExit("Invalid Android version code")
    digest = hashlib.file_digest(args.apk.open("rb"), "sha256").hexdigest()
    metadata = {
        "schemaVersion": 1,
        "store": "YASSERAPPS",
        "enabled": True,
        "versionCode": args.version_code,
        "version": args.version,
        "apkUrl": f"https://github.com/{args.repository}/releases/download/{args.tag}/{args.apk.name}",
        "sha256": digest,
        "sizeBytes": args.apk.stat().st_size,
        "notes": f"تحديث YASSERAPPS {args.version} — إصدار رسمي موقّع."
    }
    Path("catalog/store-release.json").write_text(
        json.dumps(metadata, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8"
    )
    Path("store-release-sha256.txt").write_text(
        f"{digest}  {args.apk.name}\n", encoding="utf-8")
    print("Generated signed-update feed; SHA-256:", digest)
