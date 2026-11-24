# Approved provider exports
The live catalogue can import large authorized metadata exports from Uptodown, TapTap, or APKMirror. Place a provider-authorized JSON export at `catalog/approved-feeds/uptodown.json`, `taptap.json`, or `apkmirror.json`. Only add data you have explicit permission to publish. Do not commit API credentials or private correspondence.
Required shape (illustrative, not real records):
```json
{"source":"Uptodown","authorization":"Public license or permission reference","apps":[{"name":"App name","publisherUrl":"https://example.en.uptodown.com/android","description":"Authorized app summary","iconUrl":"https://img.utdstc.com/example.png","category":"app","version":"1.0"}]}
```
The importer validates source URLs and icons, deduplicates by listing ID, retains previous records and publishes metadata only. No APK installation rights are inferred from a metadata export. Uptodown automatic bulk extraction is paused pending authorization. Existing entries are retained pending review of publication rights.
