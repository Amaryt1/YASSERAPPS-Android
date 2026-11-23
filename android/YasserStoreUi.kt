package com.vythera.vyxelapps

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val YA_GREEN = Color(0xFFC7FF00)
private val YA_BG = Color(0xFF050706)
private val YA_CARD = Color(0xFF161B18)
private val YA_MUTED = Color(0xFFAFB9B1)

private fun humanSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
    else -> String.format("%.0f MB", bytes / 1_048_576.0)
}

/**
 * A storefront (not a technical GitHub repo browser).
 * Shows verified-URL catalog entries first, and retains all existing providers.
 */
@Composable
fun YasserStoreHome(
    state: UiState,
    onAppClick: (GitHubRepo) -> Unit,
    onSearch: () -> Unit,
    onUpdates: () -> Unit,
    onRefreshSources: () -> Unit
) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    val response by produceState(
        initialValue = YasserCatalog.cached(context),
        key1 = reload
    ) {
        value = YasserCatalog.refresh(context)
    }

    val customApps = remember(response.apps) { response.apps.map { it.sourceRepo } }
    // Only owner-published catalog entries are visible to customers.
    val allApps = remember(response.apps) { response.apps.map { it.sourceRepo } }
    val games = remember(response.apps) {
        response.apps.filter { it.category == "game" }.map { it.sourceRepo }
    }
    val appsOnly = remember(response.apps) {
        response.apps.filter { it.category != "game" }.map { it.sourceRepo }
    }
    val featured = response.apps.firstOrNull { it.featured }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(YA_BG),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "brand") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.splash_icon),
                    contentDescription = "YASSERAPPS",
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("YASSER", color = Color.White, fontWeight = FontWeight.Black, fontSize = 23.sp)
                        Text("APPS", color = YA_GREEN, fontWeight = FontWeight.Black, fontSize = 23.sp)
                    }
                    Text("متجر التطبيقات والألعاب للأندرويد", color = YA_MUTED, fontSize = 12.sp)
                }
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/YasserAppsIQ"))
                    runCatching { context.startActivity(intent) }
                }) {
                    Icon(Icons.Rounded.Send, contentDescription = "قناة YASSERAPPS", tint = YA_GREEN)
                }
                IconButton(onClick = { reload += 1; onRefreshSources() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "تحديث المتجر", tint = Color.White)
                }
            }
        }

        item(key = "search") {
            Surface(
                onClick = onSearch,
                color = YA_CARD,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(55.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF303830))
            ) {
                Row(Modifier.padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = YA_GREEN)
                    Spacer(Modifier.width(11.dp))
                    Text("ابحث عن تطبيق أو لعبة...", color = YA_MUTED, fontSize = 15.sp)
                }
            }
        }

        item(key = "categories") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                YasserCategoryChip("التطبيقات", selected == 0, Modifier.weight(1f)) {
                    selected = 0; showAll = false
                }
                YasserCategoryChip("الألعاب", selected == 1, Modifier.weight(1f)) {
                    selected = 1; showAll = false
                }
                YasserCategoryChip("التحديثات", false, Modifier.weight(1f), onUpdates)
            }
        }

        if (featured != null && selected == 0) {
            item(key = "featured") {
                Surface(
                    onClick = { onAppClick(featured.sourceRepo) },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    color = YA_CARD,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Box {
                        if (featured.bannerUrl.isNotEmpty()) {
                            AsyncImage(
                                model = featured.bannerUrl, contentDescription = featured.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = .95f), Color.Black.copy(alpha = .25f))
                                )
                            )
                        )
                        Column(
                            Modifier.align(Alignment.CenterStart).padding(19.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Text("مميز على YASSERAPPS", color = YA_GREEN, fontWeight = FontWeight.Bold)
                            Text(featured.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
                            Text(featured.summary, color = Color.White.copy(alpha = .85f), maxLines = 2)
                            Text("عرض التفاصيل  ←", color = YA_GREEN, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (selected == 0) {
            item(key = "store_intro") {
                Row(
                    modifier = Modifier.fillMaxWidth().background(
                        Brush.horizontalGradient(listOf(Color(0xFF173014), Color(0xFF101410))),
                        RoundedCornerShape(20.dp)
                    ).border(1.dp, YA_GREEN.copy(alpha = .3f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painterResource(R.drawable.splash_icon), null, Modifier.size(63.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("YASSERAPPS", color = YA_GREEN, fontWeight = FontWeight.Black, fontSize = 19.sp)
                        Text("تطبيقات وألعاب • تحميل وتحديث", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        if (response.warning != null) {
            item(key = "connection_warning") {
                Text(response.warning.orEmpty(), color = Color(0xFFFFCC8A), fontSize = 12.sp)
            }
        }
        if (selected == 0) {
            item(key = "latest_apps_title") {
                YasserSectionTitle("أحدث التطبيقات", if (showAll) "عرض أقل" else "عرض الكل") { showAll = !showAll }
            }
            val visible = if (showAll) appsOnly else appsOnly.take(10)
            items(visible, key = { "apps:" + it.id }) { repo ->
                YasserAppListRow(repo, onAppClick)
            }
            if (appsOnly.isEmpty()) {
                item(key = "apps_empty") {
                    YasserEmptyMessage(
                        if (state.isLoading) "جاري تحميل تطبيقات المتجر..."
                        else "ماكو تطبيقات منشورة حاليًا. ترقب الإضافات الجديدة."
                    )
                }
            }
            item(key = "games_title") { YasserSectionTitle("أحدث الألعاب", "عرض الكل") { selected = 1; showAll = true } }
            items(games.take(4), key = { "sample-games:" + it.id }) { repo -> YasserAppListRow(repo, onAppClick) }
        } else {
            item(key = "all_games_title") {
                YasserSectionTitle("ألعاب أندرويد", if (showAll) "عرض أقل" else "عرض الكل") { showAll = !showAll }
            }
            items(if (showAll) games else games.take(10), key = { "games:" + it.id }) {
                repo -> YasserAppListRow(repo, onAppClick)
            }
            if (games.isEmpty()) item(key = "games_empty") {
                YasserEmptyMessage("ماكو ألعاب منشورة حاليًا. ترقب الإضافات الجديدة.")
            }
        }
    }
}

@Composable
private fun YasserCategoryChip(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(17.dp),
        color = if (active) YA_GREEN else YA_CARD,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) YA_GREEN else Color.DarkGray)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (active) Color.Black else Color.White,
                fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun YasserSectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(action, color = YA_GREEN, fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onClick).padding(8.dp))
    }
}

@Composable
private fun YasserEmptyMessage(message: String) {
    Surface(color = YA_CARD, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, color = YA_MUTED, modifier = Modifier.padding(19.dp), fontSize = 13.sp)
    }
}

@Composable
private fun YasserAppListRow(repo: GitHubRepo, onAppClick: (GitHubRepo) -> Unit) {
    val name = repo.displayName.ifBlank { repo.name }
    val link = repo.iconUrlOrNull
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onAppClick(repo) },
        color = YA_CARD,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF303B32))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = link, contentDescription = name,
                modifier = Modifier.size(62.dp).background(Color(0xFF242A24), RoundedCornerShape(13.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.splash_icon)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                Text(repo.description.orEmpty().ifBlank { "تطبيق أندرويد" },
                    color = YA_MUTED, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (repo.cdnVersion.isNotBlank())
                        Text("v" + repo.cdnVersion, color = YA_GREEN, fontSize = 11.sp)
                    if (repo.apkSize > 0) Text(humanSize(repo.apkSize), color = YA_MUTED, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onAppClick(repo) },
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = YA_GREEN),
                shape = RoundedCornerShape(13.dp)
            ) {
                Text("تثبيت", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

/** Custom catalog app details, with real verified APK installer callbacks. */
@Composable
fun YasserCatalogDetail(
    app: YasserCatalogApp,
    install: InstallState,
    onInstall: () -> Unit,
    onDownloadOnly: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(19.dp)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(YA_BG),
        contentPadding = PaddingValues(18.dp, 20.dp, 18.dp, 138.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, "رجوع", tint = Color.White)
                }
                Text("YASSERAPPS", color = YA_GREEN, fontSize = 22.sp,
                    fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, app.name + "\n" + app.apkUrl)
                    }
                    runCatching { context.startActivity(Intent.createChooser(share, "مشاركة التطبيق")) }
                }) {
                    Icon(Icons.Rounded.Share, "مشاركة", tint = YA_GREEN)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = app.iconUrl, contentDescription = app.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(108.dp).background(YA_CARD, shape)
                        .border(1.dp, YA_GREEN, shape)
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(app.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(app.summary, color = YA_MUTED, maxLines = 2)
                    Text("إصدار " + app.version + "  •  " + humanSize(app.sizeBytes),
                        color = YA_GREEN, fontSize = 13.sp)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onInstall,
                    enabled = install.apkAsset != null && install.downloadProgress == null,
                    modifier = Modifier.fillMaxWidth().height(53.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YA_GREEN),
                    shape = shape
                ) {
                    Icon(Icons.Rounded.Download, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(if (install.isInstalled) "إعادة التثبيت / تحديث" else "تثبيت",
                        color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                TextButton(
                    onClick = onDownloadOnly,
                    enabled = install.apkAsset != null && install.downloadProgress == null,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("تحميل فقط", color = YA_GREEN) }
                if (install.isLoadingRelease) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = YA_GREEN)
                }
                install.downloadProgress?.let {
                    LinearProgressIndicator(
                        progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
                        color = YA_GREEN
                    )
                }
                install.error?.let {
                    Text(it, color = Color(0xFFFF8585), fontSize = 12.sp)
                }
            }
        }
        if (app.screenshots.isNotEmpty()) {
            item {
                Text("صور التطبيق", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(11.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(app.screenshots) { screenshot ->
                        AsyncImage(
                            model = screenshot, contentDescription = "لقطة شاشة",
                            contentScale = ContentScale.Crop, modifier = Modifier.width(238.dp)
                                .height(160.dp).background(YA_CARD, shape)
                        )
                    }
                }
            }
        }
        item {
            YasserDetailPanel("الوصف", app.description.ifBlank { app.summary })
        }
        if (app.changelog.isNotBlank()) {
            item { YasserDetailPanel("سجل التحديثات · " + app.version, app.changelog) }
        }
        item {
            Text(
                "تحقّق من أذونات التطبيق وموثوقية ملف التحميل قبل التثبيت.",
                color = YA_MUTED, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun YasserDetailPanel(title: String, content: String) {
    Column(
        modifier = Modifier.fillMaxWidth().background(YA_CARD, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = YA_GREEN, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(content, color = Color.White, fontSize = 14.sp, lineHeight = 23.sp)
    }
}
