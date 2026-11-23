#!/usr/bin/env python3
"""Patch only the pinned Vyxel open-core checkout for YASSERAPPS."""
from pathlib import Path
import sys
root = Path(sys.argv[1] if len(sys.argv) > 1 else "upstream")
src = root / "app/src/main/java/com/vythera/vyxelapps"

def edit(relative, old, new):
    p = src / relative
    t = p.read_text(encoding="utf-8-sig")
    count = t.count(old)
    if count != 1:
        raise RuntimeError(f"{relative}: expected exactly one match, found {count} for: {old[:80]!r}")
    p.write_text(t.replace(old, new, 1), encoding="utf-8")
    print(f"Patched {relative}")

edit("HomeScreen.kt",
    '''        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {''',
    '''        // YASSERAPPS: surface real app cards above static promotion and modules.
        val yasserQuickApps = remember(
            state.recommendations, state.trending, state.fdroidApps, state.izzyApps
        ) {
            (state.recommendations + state.trending + state.fdroidApps + state.izzyApps)
                .distinctBy { it.id }
                .take(24)
        }
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {''')

edit("HomeScreen.kt",
    '''                    // Hero banner, collections, sources — hidden when a source filter is active
                    item(key = "featured") {''',
    '''                    // Apps and games first; no fabricated listings or APK URLs.
                    if (state.selectedSource == null) {
                        if (yasserQuickApps.isNotEmpty()) {
                            item(key = "yasserapps_apps_first") {
                                AppRow("تطبيقات مختارة", yasserQuickApps, installed,
                                    refreshToken = state.refreshToken) { onAppClick(it) }
                            }
                            if (state.games.isNotEmpty()) {
                                item(key = "yasserapps_games_first") {
                                    AppRow("ألعاب أندرويد", state.games.take(20), installed,
                                        refreshToken = state.refreshToken) { onAppClick(it) }
                                }
                            }
                        } else {
                            item(key = "yasserapps_no_apps") {
                                ErrorPlaceholder(
                                    "لم تصل تطبيقات من المصادر حاليًا. تحقق من الإنترنت واضغط إعادة المحاولة."
                                ) { viewModel.loadAll() }
                            }
                        }
                    }
                    // Hero banner, collections, sources — hidden when a source filter is active
                    item(key = "featured") {''')

edit("AppData.kt",
    '''        checkSelfUpdate()
        fetchAnnouncement()
        fetchFeaturedPins()''',
    '''        // YASSERAPPS: upstream release checks and publisher promotions disabled.
        // Add a YASSERAPPS-owned signed update feed before re-enabling.''')

edit("UpdateCheckWorker.kt",
    '''        checkVyxelUpdate()

        return Result.success()''',
    '''        // Never offer Vyxel upstream releases as YASSERAPPS updates.
        return Result.success()''')

edit("AppComponents.kt",
    '"https://t.me/vyxelapps_announcement"',
    '"https://t.me/YasserAppsIQ"')

strings = src / "expressive/ui/ExpressiveStrings.kt"
data = strings.read_text(encoding="utf-8-sig")
if "VYXEL STORE" not in data:
    raise RuntimeError("No original wordmark found")
strings.write_text(data.replace("VYXEL STORE", "YASSERAPPS").replace("متجر VYXEL", "YASSERAPPS"), encoding="utf-8")

prefix = chr(36) + "{com.vythera.vyxelapps.BuildConfig.VERSION_NAME}"
edit("expressive/ui/screens/SettingsScreen.kt",
    '"Vyxel Apps ' + prefix + '"',
    '"YASSERAPPS ' + prefix + '"')

print("YASSERAPPS home and update-isolation hotfix applied")
