# YASSERAPPS provider handoff

For Android Oyun Club and APKVision: supply the authorized HTTPS endpoint with JSON object containing `apps` (array) and `next` (relative same-origin URL or null). See v1.json and the existing approved-mod-feeds instructions. Provide permission evidence and release-level rights. No APK hosting in this repository. No credentials in public files.

The separate `catalog/api/review/v1/` contains factual sample metadata from the measured public probe; it is not an installable catalogue. It contains no binary URLs or source download pages. Its snapshot is static and dated. The store-facing `catalog/api/v1/` remains empty until releases are approved and verified. Do not invent fields or treat a reported provider relationship as release permission.

Offline export preflight: `python3 catalog/validate_provider_feed.py /path/to/authorized-export.json`. This validates an owner-reviewed export envelope (`schemaVersion`, `source`, `permissionUrl`, review flag, `apps`) and rejects invalid entries and duplicates. It never verifies binary bytes or publishes entries. CI runs the synthetic tests and checks any staged export before generating its review artifact.
