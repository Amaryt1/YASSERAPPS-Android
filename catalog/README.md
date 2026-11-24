# YASSERAPPS 11.0.2 — direct marketplace catalog

This branch replaces the former F-Droid and port feeds with the seven owner-selected providers in sources.json. Only Aptoide has a tested public listing interface; all other providers remain disabled until their authorized catalog/download interfaces are available and validated.

The client fetches public Aptoide metadata directly in pages of 50, interleaves games and applications, stores its accumulated catalog and cursors locally on the device, and resumes on subsequent foreground refreshes. Target: at most 20,000 unique packages; actual results depend on upstream pagination, availability, duplicates and limits. A cycle is bounded to 40 page requests and approximately 100 seconds, plus individual network timeouts. The app refreshes after 15 minutes while active during growth, then after six hours at the target. It refreshes popular first pages each cycle; this is not a guarantee of immediate updates for all 20,000 packages. No background service is claimed.

Aptoide metadata entries link to the source. Missing verdicts are clearly labeled; explicitly non-trusted entries are excluded. A matching trusted detail verdict is not an APK verification. Direct installs remain gated on the existing SHA-256, package/version and signer checks; Android controls consent and signing compatibility. No signing or Play Protect bypass is implemented. APK bytes and copied app catalogs are not stored in GitHub.

## Pending provider integration

APKPure, LiteAPKs, TapTap, Aurora, Uptodown, APKMirror need actual approved public or partner feed URLs and documentation for pagination, download authorization, digest/signature metadata, and rate limits. Aurora is a client for Google Play, not a public independent catalog endpoint. Do not embed partner secrets in an APK or public repository. Provider permission does not supply a working API or grant rights to every third-party app.

CI uses synthetic fixtures only for logic regression and separately checks the actual live device catalog and isolated featured rotation. Real catalog counts are reported in DIRECT_CATALOG; metadata-only counts are never reported as downloadable APKs.
