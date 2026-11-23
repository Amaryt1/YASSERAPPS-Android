# YASSERAPPS — catalog administration

This folder is **the exclusive YASSERAPPS store source**, not a replacement for the existing external sources. The Android app fetches it at runtime over HTTPS:

https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/apps.json

## Add an app or game

Edit `catalog/apps.json` directly in GitHub. Add an object inside `apps`:

```json
{
  "id": "example.openapp",
  "name": "Example Open App",
  "packageName": "org.example.openapp",
  "category": "app",
  "version": "1.0.0",
  "versionCode": 1,
  "sizeBytes": 1024000,
  "apkUrl": "https://example.org/releases/example-openapp.apk",
  "iconUrl": "https://example.org/images/example-openapp.png",
  "bannerUrl": "",
  "screenshots": [],
  "summary": "Short description",
  "description": "Detailed description",
  "changelog": "Initial release",
  "sha256": "",
  "featured": false,
  "enabled": true
}
```

This example is **illustrative only**; it is not an actual published app and is not in the live catalog. Replace every example value with a verified, authorized app and working HTTPS links.

To update an existing app, change its `version`, increase `versionCode`, update `apkUrl`, `changelog`, and size. Keep `id` and `packageName` stable. Published updates must use the **same package signing key** or Android will reject an in-place upgrade.

Set `category` to `game` to place it in Games, or `app` for Apps. Set `featured` true to promote it in the homepage banner. Set `enabled` false to hide it without deletion.

## Important

- The source is public because the repository is public. **Never include access tokens or secret download credentials** in the catalog or APK.
- Only publish APK files you have the right to distribute, and verify the publisher/signature and security of each file.
- URLs must use HTTPS and the APK URL path must end in .apk. Split formats like .xapk, .apks and .apkm are not currently accepted by this catalog, because the classic installation path verifies standard APK files only.
- When possible, set the exact SHA-256 checksum of your APK. The catalog validator checks formatting, **not** whether the remote binary matches; server-side verification is still recommended.
- Run `python3 scripts/validate_catalog.py catalog/apps.json` before committing. GitHub Actions checks the JSON on every change.
- The application keeps the last successfully loaded catalog for offline use. Public GitHub raw caching and connectivity may delay changes briefly.
- Editing this JSON does **not** rebuild the APK; any new functionality in the Android client itself still requires a new signed APK.
