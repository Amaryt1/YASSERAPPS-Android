# YASSERAPPS V8 — advanced features and test plan

This release builds on the **independent YASSERAPPS Android client**, not on any third-party app-store client, and preserves the black/neon-green Arabic interface.

## Included in V8

1. **Persistent downloads:** Android's system DownloadManager owns transfers while the app is closed, and a local record maps download ID, app, version, hash and filename.
2. **Progress:** Download page shows pending, active, paused-by-system, completed, and failed state, including bytes and percent when available.
3. **Management:** Cancel active transfer, delete completed files and history, and retry failed downloads where the app remains in the live catalog. Confirmation is required for deletion.
4. **Install verification:** Before opening Android's installer, check that the file exists, is not empty, has the declared Android package name and versionCode, and matches SHA-256 **if a checksum is provided**.
5. **Permission flow:** The user may grant the Android "Install unknown apps" permission for YASSERAPPS, and then return to Downloads and explicitly install. No silent installation.
6. **Update detection:** The Updates screen compares the catalog versionCode with installed app versions visible under Android package-visibility rules. A manual refresh fetches the latest catalog.
7. **User preferences:** Wi-Fi-only mode for new download requests and persistent favorites.
8. **Offline support:** The app keeps its last valid catalog for reading while offline (from the earlier independent version). Image loading still needs connectivity unless cached elsewhere.

## Deliberate boundaries

- **APK only**: the catalog currently accepts directly downloadable `.apk` packages. Split-app formats like XAPK, APKM and APKS are not installed by this release.
- DownloadManager **does not expose a manual pause/resume API**. It may pause/resume automatically based on network rules; Cancel and Retry remain available.
- Update checks run in-app, **not** as a silent background installer or a promise of real-time notifications.
- Android 11+ limits installed-app visibility; this release declares launcher-app queries rather than broad `QUERY_ALL_PACKAGES` permission. Some non-launcher apps may not be identified for update checks.
- If a catalog entry does not include `sha256`, a checksum cannot be verified. File package/version checks still run, but they do not prove publisher authenticity.
- Genuine Android updates require the new app APK to be signed with the same signing identity as the already-installed app. The OS enforces this.
- No fabricated apps, download counts, reviews or ratings are inserted. Current live catalog may remain empty until the owner adds approved entries.
- CI produces a **debug-signed test APK**. Production publishing and reliable updates of the YASSERAPPS client itself need a stable signing key, release workflow and owner-approved release channel.

## Suggested validation on a device

1. Confirm Home, Search, Downloads, Updates, Settings and Favorites open without exiting unexpectedly.
2. Add a trusted, redistributable, properly signed Android APK entry to the catalog with a correct packageName and versionCode (preferably SHA-256).
3. Verify listing, app detail page, progress and notifications during a real download.
4. Close and reopen YASSERAPPS mid-download: the transfer should continue, and history/progress should be recoverable.
5. Test Cancel, failed-download Retry, and Wi-Fi-only behavior.
6. Test a good APK, a SHA-256 mismatch, and a deliberately incorrect package/version: mismatches must not launch the installer.
7. Verify first-install permission and user's final Android installer confirmation.
8. Publish a new versionCode for an app already installed on the test device, then use Updates to verify discovery, download, and Android signature enforcement.
9. Test on more than one Android vendor/OS before distributing widely.

## Repository scope

Only `Amaryt1/YASSERAPPS-Android` is used for this project. The live catalog is `catalog/apps.json`; source code is under `independent/`. All APK distribution rights and checks are the responsibility of the catalog publisher.
