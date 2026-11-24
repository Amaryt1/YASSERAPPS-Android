# YASSERAPPS public catalog API
Read-only JSON files generated from the four existing metadata sources on each refresh. No credentials or APK hosting.
Base: https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/api/v1/
Fetch index.json for counts, generation and group paths. Pages contain at most 250 records.
Groups: all, app, game, aptoide, uptodown, apkmirror, taptap, and each source/app or source/game. Follow the relative next filename until null. If generatedAt changes between pages, restart pagination to avoid inconsistent results.
The API provides discovery metadata, not verified downloadable APKs. It neither grants redistribution rights nor expands upstream access. Client integration requires a separately tested Android change.
