# YASSERAPPS — متجر أندرويد

**Official Telegram:** https://t.me/YasserAppsIQ

This repository builds a branded Android **debug APK** from the AGPL-3.0 open-core project [Vyxel Apps](https://github.com/NikhilKain/vyxel-apps). It is an independent derivative, **not** the original Vyxel Apps release.

## What is included
- Brand: **YASSERAPPS** (black with neon-green logo and UI accent).
- Android application ID: `com.yasserapps.store`.
- Telegram button in Settings: [@YasserAppsIQ](https://t.me/YasserAppsIQ).
- Original Vyxel open-core sources, app browsing, downloading, update/install features (subject to build and device testing).
- `YASSERAPPS_Vyxel_BuildKit.zip` contains branding, patch script, license guidance, and source build instructions.
- GitHub Actions compiles the source on Ubuntu with Android SDK.

## Get the APK
1. Go to **Actions** → **Build YASSERAPPS Android APK**.
2. Open the latest successful run, or choose **Run workflow**.
3. Under **Artifacts** download `YASSERAPPS-Android-Debug-APK`; unzip to get the `.apk` file.
4. Install on Android 8 or newer and test before wider distribution.

**Status:** Build workflow configured; not guaranteed to be successful until the CI run finishes and the APK is tested. Debug APKs are not suitable as permanent public releases. Future upgrades require keeping the same production signing key.

## How it builds
The action verifies the SHA-256 of the kit ZIP, extracts it, checks out Vyxel Apps at pinned commit:
`e13fc79fa6523f794b258b253170423036eb3d3a`, runs `customize.py`, then runs `./gradlew :app:assembleDebug`.

GitHub Actions also uploads the modified corresponding source as an artifact. To reproduce locally, extract the ZIP and follow its `README.md`.

## Rights and licensing
- Original Vyxel Apps source: © its original author(s), licensed under GNU AGPL-3.0, see [upstream license](https://github.com/NikhilKain/vyxel-apps/blob/main/LICENSE).
- YASSERAPPS name, channel, branding, and independently created changes are associated with the YASSERAPPS project.
- Rebranding **does not transfer ownership** of Vyxel source code or remove its author and license requirements.
- Redistribution of a modified APK requires providing the corresponding source under the terms of AGPL-3.0. Archive artifacts have a limited retention period, so ensure source stays available for any published release.
- Third-party APK content may require separate rights or permission to redistribute.
- No proprietary premium Vyxel themes are included.

## Security
Review the application's permissions and third-party download sources before publishing. The workflow builds a debug release signed with a development key, not a production-signed release.
