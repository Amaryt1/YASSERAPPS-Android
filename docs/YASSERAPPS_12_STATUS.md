# YASSERAPPS 12.0.0

Owner-only workflow: Manage YASSERAPPS catalog (upsert, disable, enable, delete). Each published APK requires a real size, SHA-256, package, version code and signing certificate SHA-256; the selected file is verified before publication. `catalog/apps.json` is consumed by the store, not an APK archive. No owner APK has been supplied yet.

Private ratings, search history and user-selected backup files remain on device. Requests and reports are sent through the user's chosen share application, not an automatic server submission. Global rating aggregation, comments and global download totals require a deployed authenticated backend; no synthetic totals are shown. External sources without verified direct APK remain discovery only. Signed self-update requires the owner's permanent signing secrets and published release; debug APK is not a production release.

The mobile build includes ARM64 and ARMv7 only; x86 emulator builds retain all ABIs on pull requests.
