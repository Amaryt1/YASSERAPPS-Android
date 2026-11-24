#!/usr/bin/env python3
"""Embed authentic curated app artwork in YASSERAPPS APK for immediate offline visuals.

Only a bounded amount of official catalog imagery is bundled to avoid making
the download huge. Other images still load lazily from the catalog's HTTPS URLs.
Do not bundle random internet search results or imagery from unrelated apps.
"""
import hashlib
import json
import sys
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen

catalog = Path(sys.argv[1] if len(sys.argv) > 1 else "catalog/apps.json")
destination = Path(sys.argv[2] if len(sys.argv) > 2 else "independent/app/src/main/assets/catalog_media")
apps = json.loads(catalog.read_text(encoding="utf-8"))["apps"]
destination.mkdir(parents=True, exist_ok=True)
media_urls = []
critical_urls = set()

for app in apps:
    if not app.get("enabled", True):
        continue
    icon = app.get("iconUrl", "")
    if icon: media_urls.append(icon)
    if app.get("featured"):
        art = app.get("bannerUrl") or (app.get("screenshots") or [""])[0]
        if not art:
            raise SystemExit("Featured entry must include authentic game/app imagery: " + app["name"])
        media_urls.append(art)
        critical_urls.add(art)
        for shot in app.get("screenshots", [])[:2]:
            media_urls.append(shot)
            critical_urls.add(shot)

# Keep mandatory featured media first, then fill remaining slots with icons.
media_urls = list(dict.fromkeys(media_urls))
if len(critical_urls) > 35:
    raise SystemExit("Too many mandatory featured media files")
media_urls = ([u for u in media_urls if u in critical_urls] +
              [u for u in media_urls if u not in critical_urls])[:35]
aggregate_bytes = 0

for url in media_urls:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username:
        raise SystemExit("Invalid media URL: " + url)
    filename = hashlib.sha256(url.encode("utf-8")).hexdigest() + ".img"
    target = destination / filename
    request = Request(url, headers={
        "User-Agent": "YASSERAPPS-Official-Media-Bundler/1.0",
        "Accept": "image/png,image/jpeg,image/webp,image/*",
    })
    try:
        with urlopen(request, timeout=30) as response:
            ctype = response.headers.get("Content-Type", "").split(";")[0]
            if not (ctype.startswith("image/") or ctype == "application/octet-stream"):
                raise ValueError("Unexpected image MIME type " + ctype)
            content = response.read(5_000_001)
            if len(content) > 5_000_000 or len(content) < 100:
                raise ValueError("Image size is invalid")
            # Basic file signature validation. APK contains bytes, never executable HTML.
            if not (content.startswith(b"\x89PNG\r\n\x1a\n") or
                    content.startswith(b"\xff\xd8\xff") or
                    content.startswith(b"RIFF") and content[8:12] == b"WEBP"):
                raise ValueError("Image format is not PNG, JPEG or WEBP")
        if aggregate_bytes + len(content) > 18_000_000:
            raise ValueError("Offline artwork would exceed 18MB cap")
        aggregate_bytes += len(content)
        target.write_bytes(content)
        print("BUNDLED", len(content), "bytes:", url)
    except Exception as error:
        if url in critical_urls:
            raise SystemExit(f"Required featured app/game artwork unavailable: {url}: {error}")
        print("OPTIONAL IMAGE NOT BUNDLED", url, str(error))

print(f"YASSERAPPS embedded {len(list(destination.glob('*.img')))} authentic catalog artwork files ({aggregate_bytes} bytes)")
