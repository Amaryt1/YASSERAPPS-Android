#!/usr/bin/env python3
"""YASSERAPPS: clean Android-only storefront, optional anonymous GitHub search.

This script is intentionally limited to the local pinned Vyxel checkout prepared
by the YASSERAPPS GitHub Action. It refuses unexpected source changes.
"""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "upstream")
src = root / "app/src/main/java/com/vythera/vyxelapps"

def patch(rel, old, new, count=1):
    p = src / rel
    s = p.read_text(encoding="utf-8-sig")
    n = s.count(old)
    if n != count:
        raise RuntimeError(f"{rel}: expected {count}, found {n}: {old[:85]!r}")
    p.write_text(s.replace(old, new), encoding="utf-8")
    print("OK", rel, old[:55].replace("\n", " "))

def cut_between(rel, begin, end):
    p = src / rel
    s = p.read_text(encoding="utf-8-sig")
    if s.count(begin) != 1 or s.count(end) != 1:
        raise RuntimeError(f"{rel}: unstable block markers {begin[:30]!r}")
    a = s.index(begin)
    b = s.index(end, a + len(begin))
    p.write_text(s[:a] + s[b:], encoding="utf-8")
    print("Removed redundant frontend section", rel, begin[:48])

# 1. GitHub personal token is OPTIONAL. Do not demand it at startup.
patch("MainActivity.kt",
'''            val screen = when {
                !settings.hasSeenOnboarding && settings.githubToken.isEmpty() -> "TOKEN_SETUP"
                else -> "HOME"
            }''',
'''            // YASSERAPPS works without asking users for GitHub credentials.
            val screen = "HOME"''')

# 2. Default localization and palette for new installs only.
patch("MainActivity.kt", 'getString("user_language_code", "en")', 'getString("user_language_code", "ar")')
patch("AppData.kt", 'val language                : String  = "English"', 'val language                : String  = "Arabic"')
patch("AppData.kt", 'val themeMode               : String  = "Light"', 'val themeMode               : String  = "Dark"')
patch("AppData.kt", 'val amoledBlack             : Boolean = false', 'val amoledBlack             : Boolean = true')
patch("AppData.kt", 'val accentHex      : String  = "#D0BCFF"', 'val accentHex      : String  = "#C7FF00"')

# 3. Classic screenshot is the default. Hide technical source filters in home.
patch("HomeScreen.kt",
'''            item(key = "source_chips") {
                HomeSourceChipsRow(
                    selectedSource = state.selectedSource,
                    onSourceSelect = { viewModel.setSourceFilter(it) },
                    modifier       = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }
''',
"")

# Keep source adapters internal; remove promotional blocks/modules/source browse
# but preserve search, normal app lists, install history and updates.
cut_between("HomeScreen.kt",
'''                    // Hero banner, collections, sources — hidden when a source filter is active''',
'''                    // Newly Launched''')

# Allow only entries with a known APK/XAPK/APKM/APKS URL on home. No random
# non-installable GitHub repositories labeled "Android games".
patch("HomeScreen.kt",
'''            (state.recommendations + state.trending + state.fdroidApps + state.izzyApps)
                .distinctBy { it.id }
                .take(24)''',
'''            (state.recommendations + state.trending + state.fdroidApps + state.izzyApps)
                .filter { app ->
                    val url = app.apkUrl.substringBefore('?').substringBefore('#')
                    listOf(".apk", ".xapk", ".apks", ".apkm")
                        .any { url.endsWith(it, ignoreCase = true) }
                }
                .distinctBy { it.id }
                .take(24)''')

patch("HomeScreen.kt",
'''AppRow("ألعاب أندرويد", state.games.take(20), installed,''',
'''AppRow("ألعاب أندرويد", state.games.filter { game ->
                                        val url = game.apkUrl.substringBefore('?').substringBefore('#')
                                        listOf(".apk", ".xapk", ".apks", ".apkm")
                                            .any { url.endsWith(it, ignoreCase = true) }
                                    }.take(20), installed,''')

# The long-tail category rails should also not include Windows/Linux and
# non-installable repository records.
patch("HomeScreen.kt",
'''            fun uniq(items: List<GitHubRepo>) = items.filter { seen.add(it.id) }''',
'''            fun uniq(items: List<GitHubRepo>) = items.filter { app ->
                val url = app.apkUrl.substringBefore('?').substringBefore('#')
                val apk = listOf(".apk", ".xapk", ".apks", ".apkm")
                    .any { url.endsWith(it, ignoreCase = true) }
                apk && seen.add(app.id)
            }''')

# Don't show the source name in the search hint, and use YASSERAPPS branding.
patch("AppComponents.kt",
'''                s.searchHint,
                style    = MaterialTheme.typography.bodyLarge,''',
'''                "ابحث عن تطبيق أو لعبة",
                style    = MaterialTheme.typography.bodyLarge,''')
patch("AppComponents.kt",
'''                    text     = s.discoverTitle.uppercase(),''',
'''                    text     = "YASSERAPPS",''')
patch("AppComponents.kt",
'''                    s.discoverTitle,
                    style      = if (npHeaderGradient)''',
'''                    "YASSERAPPS",
                    style      = if (npHeaderGradient)''')
patch("AppComponents.kt",
'''                if (isCyber) s.openSourceApps.uppercase() else s.openSourceApps,''',
'''                "تطبيقات وألعاب أندرويد",''')
patch("AppComponents.kt", 'painterResource(R.drawable.skpic)', 'painterResource(R.mipmap.ic_launcher)')

# Home cards show app identity, not the GitHub username of a source developer.
# Detailed license / source information remains accessible where required.
user = "@" + chr(36) + "{repo.owner.login}"
patch("AppComponents.kt",
'''                Text(
                    "''' + user + '''",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = ownerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
''',
"")
patch("AppComponents.kt",
'''            overlineContent = {
                Text(
                    "''' + user + '''",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            },''',
'''            overlineContent = null,''')
patch("AppComponents.kt",
'''                            Text(
                                "''' + user + '''",
                                style    = MaterialTheme.typography.labelLarge,
                                color    = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
''',
"")

# Replace the Android 12+ *separate* splash icon. The previous build patched
# launcher foreground but left drawable-nodpi/splash_icon.png as Vyxel artwork.
branding = Path("buildkit/branding/YASSERAPPS_icon_crop.png")
splash = root / "app/src/main/res/drawable-nodpi/splash_icon.png"
if not branding.is_file() or not splash.is_file():
    raise RuntimeError("Brand splash icon or original destination missing")
shutil.copy2(branding, splash)
print("Replaced splash with channel Y logo")

# No personal developer/support promotion in the end-user Profile screen.
# Preserve AGPL attribution in NOTICE and YASSERAPPS_ORIGIN.md.
cut_between("AppScreens.kt",
'''            // Support & Feedback
            item {''',
'''            // Favourites''')

# No more redirecting users to the original author's telegram/share links
# in the unchanged UI. The only official YASSERAPPS channel is ours.
print("YASSERAPPS clean storefront modifications complete.")
