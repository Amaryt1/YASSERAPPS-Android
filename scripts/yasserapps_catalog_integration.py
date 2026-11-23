#!/usr/bin/env python3
"""Add the native YASSERAPPS storefront, preserving original source adapters.

Apply AFTER customize.py, yasserapps_home_hotfix.py, yasserapps_store_cleanup.py
against a clean upstream checkout pinned by the repo's GitHub Actions workflow.
"""
from pathlib import Path
import shutil
import sys

base = Path(sys.argv[1] if len(sys.argv) > 1 else "upstream")
java = base / "app/src/main/java/com/vythera/vyxelapps"

def edit(relative, old, new, expected=1):
    path = java / relative
    content = path.read_text(encoding="utf-8-sig")
    found = content.count(old)
    if found != expected:
        raise RuntimeError(f"{relative}: expected {expected} matches, found {found} of {old[:65]!r}")
    path.write_text(content.replace(old, new), encoding="utf-8")
    print("Patched", relative, old[:45].replace("\n", " "))

edit("AppData.kt",
    '    APTOIDE  ("Aptoide",     0xFFF57C00L),',
    '    YASSERAPPS("YASSERAPPS", 0xFFC7FF00L),\n    APTOIDE  ("Aptoide",     0xFFF57C00L),')

# Both exhaustive menus use identical source labels, with different indentation.
edit("AppComponents.kt",
    'AppSource.APTOIDE  -> "View on Aptoide"',
    'AppSource.YASSERAPPS -> "View on YASSERAPPS"\n                            AppSource.APTOIDE  -> "View on Aptoide"',
    expected=2)

for source in ("YasserCatalog.kt", "YasserStoreUi.kt"):
    source_path = Path("android") / source
    if not source_path.is_file():
        raise RuntimeError(f"Missing {source_path}")
    shutil.copy2(source_path, java / source)
    print("Installed Kotlin component", source)

home = "HomeScreen.kt"
edit(home,
'''                                    AppDetailScreen(
                                        repo                  = repo,''',
'''                                    val catalogRecord = if (repo.source == AppSource.YASSERAPPS)
                                        YasserCatalog.cached(context).apps.firstOrNull { it.sourceRepo.id == repo.id }
                                    else null
                                    if (catalogRecord != null) {
                                        YasserCatalogDetail(
                                            app = catalogRecord,
                                            install = installState,
                                            onInstall = { installState.apkAsset?.let { viewModel.downloadAndInstall(repo, it) } },
                                            onDownloadOnly = { installState.apkAsset?.let { viewModel.downloadOnly(repo, it) } },
                                            onBack = { viewModel.refreshInstall(repo.id); selectedRepo = null }
                                        )
                                    } else AppDetailScreen(
                                        repo                  = repo,''')

edit(home,
'''                                    VAppTab.HOME -> HomeTab(
                                        state          = state,
                                        viewModel      = viewModel,
                                        installed      = installedSet,
                                        listState      = homeListState,
                                        onAppClick     = { r -> viewModel.addToHistory(r); selectedRepo = r },
                                        onSeeAll       = { showSeeAll = true },
                                        onSearchClick  = { selectedTab = VAppTab.SEARCH },
                                        onScrollChange = { scrolling -> dockVisible = !scrolling },
                                        onProfileClick = { selectedTab = VAppTab.PROFILE},
                                        onOpenModules  = { showModules = true }
                                    )''',
'''                                    VAppTab.HOME -> YasserStoreHome(
                                        state = state,
                                        onAppClick = { r -> viewModel.addToHistory(r); selectedRepo = r },
                                        onSearch = { selectedTab = VAppTab.SEARCH },
                                        onUpdates = { selectedTab = VAppTab.INSTALLED },
                                        onRefreshSources = { viewModel.loadAll() }
                                    )''')

# Search still uses original providers. Add the user-managed catalog to results.
edit(home,
'''                                        results               = state.searchResults,
                                        platform              = state.platform,''',
'''                                        results               = (
                                            YasserCatalog.cached(context).apps
                                                .filter { a ->
                                                    state.searchQuery.length >= 2 &&
                                                    (a.name.contains(state.searchQuery, ignoreCase = true) ||
                                                     a.summary.contains(state.searchQuery, ignoreCase = true) ||
                                                     a.packageName.contains(state.searchQuery, ignoreCase = true))
                                                }
                                                .map { it.sourceRepo } + state.searchResults
                                            ).distinctBy { it.packageId.ifBlank { it.full_name } },
                                        platform              = state.platform,''')

# Replace the technical "Profile" nav entry with a real search tab, keep downloads.
components = "AppComponents.kt"
edit(components,
'''        Triple(VAppTab.HOME,      Icons.Rounded.Home,     s.navHome),
        Triple(VAppTab.INSTALLED, Icons.Rounded.Download, s.navInstalled),
        Triple(VAppTab.PROFILE,   Icons.Rounded.Person,   s.navProfile),
        Triple(VAppTab.SETTINGS,  Icons.Rounded.Settings, s.navSettings)''',
'''        Triple(VAppTab.HOME,      Icons.Rounded.Home,     "الرئيسية"),
        Triple(VAppTab.SEARCH,    Icons.Rounded.Search,   "البحث"),
        Triple(VAppTab.INSTALLED, Icons.Rounded.Download, "التنزيلات"),
        Triple(VAppTab.SETTINGS,  Icons.Rounded.Settings, "الإعدادات")''',
expected=2)

print("YASSERAPPS custom catalog + native storefront integrated.")
