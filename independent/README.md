# YASSERAPPS Android — independent edition

This Android application is original project code maintained in this repository. It does not copy, package or build any Vyxel application code, resources, or dependencies.

- Uses Java Android SDK and Gradle's standard Android application plugin
- Loads only this repository's `catalog/apps.json` as its user-visible source of apps
- Displays apps/games with working APK links defined by the catalog administrator
- Arabic black + fluorescent green storefront
- Search only among the store's catalog
- Android system Download Manager and system package installer
- Installed-package version check and update indications
- Optional cached offline catalog

**Catalog is currently empty.** No fake downloads, versions or app listings are generated.

**Development debug build only:** GitHub Actions debugging APKs are not signed with your production key. Publish a release only after generating and safely storing a permanent signing key.

Do not publish APKs without permission or verification. Only direct HTTPS `.apk` entries are accepted for installation.

Build: `gradle --no-daemon :app:assembleDebug`.
