#!/usr/bin/env python3
"""Validate the editable YASSERAPPS catalog. Standard library only."""
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

VALID_CATEGORIES = {"app", "game"}
VALID_EXT = (".apk", ".apks", ".apkm", ".xapk")
REQUIRED = ("id", "name", "packageName", "category", "version", "versionCode", "apkUrl", "iconUrl")
PACK = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$")
ID = re.compile(r"^[a-z0-9][a-z0-9_.-]{2,79}$")
HEX = re.compile(r"^[0-9a-fA-F]{64}$")

def validate_url(raw, *, extension=False, optional=False):
    if optional and raw == "":
        return
    if not isinstance(raw, str):
        raise ValueError("URL must be a string")
    if len(raw) > 2048 or any(c.isspace() for c in raw):
        raise ValueError("URL contains whitespace or is too long")
    u = urlparse(raw)
    if u.scheme != "https" or not u.hostname or u.username or u.password:
        raise ValueError("URL must be public HTTPS without credentials")
    if extension and not u.path.lower().endswith(VALID_EXT):
        raise ValueError("File URL must point to APK, APKS, APKM or XAPK")

def validate(path):
    document = json.loads(Path(path).read_text(encoding="utf-8"))
    if document.get("schemaVersion") != 1 or document.get("store") != "YASSERAPPS":
        raise ValueError("unsupported catalog schema or wrong store")
    if document.get("channel") != "https://t.me/YasserAppsIQ":
        raise ValueError("incorrect store channel")
    apps = document.get("apps")
    if not isinstance(apps, list) or len(apps) > 5000:
        raise ValueError("apps must be an array of at most 5000 entries")
    seen_id, seen_package = set(), set()
    for i, app in enumerate(apps):
        try:
            if not isinstance(app, dict):
                raise ValueError("entry must be an object")
            if not all(field in app for field in REQUIRED):
                raise ValueError("missing one or more required fields")
            key = app["id"]
            if not isinstance(key, str) or not ID.fullmatch(key) or key in seen_id:
                raise ValueError("invalid/duplicate id")
            seen_id.add(key)
            package = app["packageName"]
            if not isinstance(package, str) or not PACK.fullmatch(package):
                raise ValueError("invalid Android packageName")
            if package in seen_package:
                raise ValueError("duplicate packageName (update existing entry instead)")
            seen_package.add(package)
            if not isinstance(app["name"], str) or not (1 <= len(app["name"]) <= 100):
                raise ValueError("invalid app name")
            if not isinstance(app["version"], str) or not (1 <= len(app["version"]) <= 45):
                raise ValueError("invalid version")
            if type(app["versionCode"]) is not int or not (1 <= app["versionCode"] <= 2**31-1):
                raise ValueError("versionCode must be a positive Android integer")
            if app["category"] not in VALID_CATEGORIES:
                raise ValueError("category must be app or game")
            validate_url(app["apkUrl"], extension=True)
            validate_url(app["iconUrl"])
            for field in ("bannerUrl",):
                validate_url(app.get(field, ""), optional=True)
            photos = app.get("screenshots", [])
            if not isinstance(photos, list) or len(photos) > 12:
                raise ValueError("screenshots must have no more than 12 URLs")
            for url in photos:
                validate_url(url)
            for field in ("summary", "description", "changelog"):
                if not isinstance(app.get(field, ""), str) or len(app.get(field, "")) > 12000:
                    raise ValueError(f"invalid {field}")
            if type(app.get("featured", False)) is not bool or type(app.get("enabled", True)) is not bool:
                raise ValueError("featured and enabled must be booleans")
            if type(app.get("sizeBytes", 0)) is not int or app.get("sizeBytes", 0) < 0:
                raise ValueError("sizeBytes must be nonnegative integer")
            checksum = app.get("sha256", "")
            if not isinstance(checksum, str) or not HEX.fullmatch(checksum):
                raise ValueError("sha256 is required and must be 64 hexadecimal characters")
        except (ValueError, TypeError, KeyError) as e:
            raise ValueError(f"apps[{i}]: {e}") from e
    print(f"YASSERAPPS catalog valid: {len(apps)} apps")
    return len(apps)

if __name__ == "__main__":
    try:
        validate(sys.argv[1] if len(sys.argv) > 1 else "catalog/apps.json")
    except (OSError, ValueError, json.JSONDecodeError) as e:
        print(f"CATALOG ERROR: {e}", file=sys.stderr)
        sys.exit(1)
