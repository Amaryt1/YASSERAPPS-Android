#!/usr/bin/env python3
"""Publish, update or hide an app in the YASSERAPPS catalog via GitHub Actions.

The workflow is accessible to authorized GitHub repository collaborators only.
No token or secret is stored in the Android app or admin HTML.
"""
import argparse
import json
import re
from pathlib import Path

from validate_catalog import validate

CATALOG = Path("catalog/apps.json")
ID = re.compile(r"^[a-z0-9][a-z0-9_.-]{2,79}$")
SHA = re.compile(r"^[a-fA-F0-9]{64}$")

def manage(op, raw):
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    apps = data["apps"]
    entry = json.loads(raw)
    if not isinstance(entry, dict):
        raise ValueError("Entry must be a JSON object")
    if op == "upsert":
        if not SHA.fullmatch(str(entry.get("sha256", ""))):
            raise ValueError("A verified SHA-256 checksum is required for new/updated APK releases")
        if not ID.fullmatch(str(entry.get("id", ""))):
            raise ValueError("Invalid app id")
        prior = next((app for app in apps if app.get("id") == entry["id"]), None)
        if prior is not None:
            if prior["packageName"] != entry.get("packageName"):
                raise ValueError("Cannot change existing packageName; use a new app id")
            if entry.get("versionCode", 0) < prior.get("versionCode", 0):
                raise ValueError("Cannot publish a versionCode downgrade")
            apps[apps.index(prior)] = entry
        else:
            apps.insert(0, entry)
    elif op == "disable":
        identity = str(entry.get("id", "")).strip()
        target = next((app for app in apps if app.get("id") == identity), None)
        if target is None:
            raise ValueError("No app matches that id")
        target["enabled"] = False
        target["featured"] = False
    elif op == "enable":
        identity = str(entry.get("id", "")).strip()
        target = next((app for app in apps if app.get("id") == identity), None)
        if target is None:
            raise ValueError("No app matches that id")
        target["enabled"] = True
    else:
        raise ValueError(f"Unsupported operation: {op}")
    original = CATALOG.read_text(encoding="utf-8")
    CATALOG.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    try:
        validate(CATALOG)
    except Exception:
        CATALOG.write_text(original, encoding="utf-8")
        raise
    print(f"Catalog action: {op}; affected app: {entry['id']}; entries: {len(apps)}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--operation", choices=["upsert", "disable", "enable"], required=True)
    parser.add_argument("--entry-json", required=True)
    args = parser.parse_args()
    manage(args.operation, args.entry_json)
