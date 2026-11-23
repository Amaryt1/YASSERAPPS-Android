# YASSERAPPS — catalog administration

This is **the only customer-visible YASSERAPPS application and game catalog**. The independent Android client does not browse third-party developer repositories. The Android app fetches it at runtime over HTTPS:

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
  "sha256": "REPLACE_WITH_ACTUAL_64_HEX_SHA256",
  "featured": false,
  "enabled": true
}
```

This example is **illustrative only**, and the SHA-256 placeholder deliberately fails validation. Replace it with a genuine SHA-256 from the downloadable package; it is not an actual published app. Replace every example value with a verified, authorized app and working HTTPS links.

To update an existing app, change its `version`, increase `versionCode`, update `apkUrl`, `changelog`, and size. Keep `id` and `packageName` stable. Published updates must use the **same package signing key** or Android will reject an in-place upgrade.

Set `category` to `game` to place it in Games, or `app` for Apps. Set `featured` true to promote it in the homepage banner. Set `enabled` false to hide it without deletion.

## Important

- The source is public because the repository is public. **Never include access tokens or secret download credentials** in the catalog or APK.
- Only publish APK files you have the right to distribute, and verify the publisher/signature and security of each file.
- URLs must use HTTPS and end in .apk, .apks, .apkm or .xapk. Split formats are installed through Android's PackageInstaller only when they contain APK splits without OBB/Android/data; other packages are rejected to prevent incomplete installs.
- The exact SHA-256 checksum is **required** for every catalog entry. The validator checks formatting, and the verification workflow downloads official APKs and checks actual hashes and package metadata. Split archives still need device-level installation testing.
- Run `python3 scripts/validate_catalog.py catalog/apps.json` before committing. GitHub Actions checks the JSON on every change.
- The application keeps the last successfully loaded catalog for offline use. Public GitHub raw caching and connectivity may delay changes briefly.
- Editing this JSON does **not** rebuild the APK; any new functionality in the Android client itself still requires a new signed APK.

## Brand banner and reference design

A featured app appears in the full-width homepage hero when `featured` is true. Provide `bannerUrl` for its promotional artwork. App and game cards are created dynamically from real catalog records; blank catalogs show an honest empty state rather than made-up installations. The app-details gallery uses `screenshots`, and the green install button downloads only the `apkUrl` associated with that record.

## Owner dashboard

Open [admin/index.html](../admin/index.html) for the Arabic administration form and app preview. Publishing requires an authorized GitHub collaborator to run [Manage YASSERAPPS catalog](../.github/workflows/manage-catalog.yml); no user-facing app tokens are required.
