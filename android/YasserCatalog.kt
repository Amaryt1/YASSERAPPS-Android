package com.vythera.vyxelapps

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Independent YASSERAPPS catalog. Never requires a GitHub personal token.
 * Existing upstream sources are not removed or replaced.
 */
data class YasserCatalogApp(
    val id: String,
    val name: String,
    val packageName: String,
    val category: String,
    val version: String,
    val versionCode: Int,
    val apkUrl: String,
    val iconUrl: String,
    val bannerUrl: String,
    val screenshots: List<String>,
    val summary: String,
    val description: String,
    val changelog: String,
    val sizeBytes: Long,
    val featured: Boolean
) {
    val sourceRepo: GitHubRepo
        get() = GitHubRepo(
            id = 14_000_000_000L + (id.hashCode().toLong() and 0xffffffffL),
            name = name,
            full_name = "YASSERAPPS/" + id,
            description = summary.ifBlank { description },
            html_url = "https://github.com/Amaryt1/YASSERAPPS-Android/blob/main/catalog/apps.json",
            owner = RepoOwner(login = "YASSERAPPS", avatar_url = iconUrl, type = "Organization"),
            source = AppSource.YASSERAPPS,
            apkUrl = apkUrl,
            cdnVersion = version,
            apkSize = sizeBytes,
            packageId = packageName
        )
}

data class YasserCatalogResult(
    val apps: List<YasserCatalogApp> = emptyList(),
    val warning: String? = null,
    val fromCache: Boolean = false
)

object YasserCatalog {
    const val URL = "https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/apps.json"
    private const val STORE_PREFS = "yasserapps_catalog"
    private const val CACHE_KEY = "last_good_catalog"

    private fun https(url: String): Boolean =
        runCatching {
            val parsed = java.net.URI(url)
            parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank() &&
                parsed.rawUserInfo == null
        }.getOrDefault(false)

    private fun parse(json: String): List<YasserCatalogApp> {
        val doc = JSONObject(json)
        require(doc.optInt("schemaVersion", 0) == 1 && doc.optString("store") == "YASSERAPPS")
        val array = doc.getJSONArray("apps")
        require(array.length() <= 5000) { "Catalog too large" }
        val seen = HashSet<String>()
        val results = ArrayList<YasserCatalogApp>(array.length())
        for (n in 0 until array.length()) {
            val row = array.getJSONObject(n)
            if (!row.optBoolean("enabled", true)) continue
            val id = row.getString("id")
            val url = row.getString("apkUrl")
            val icon = row.getString("iconUrl")
            val pkg = row.getString("packageName")
            val filename = java.net.URI(url).path.orEmpty().lowercase()
            if (id.isBlank() || !seen.add(id) || !https(url) || !https(icon) ||
                !listOf(".apk", ".xapk", ".apks", ".apkm").any { filename.endsWith(it) } ||
                !pkg.contains(".")
            ) continue
            val images = ArrayList<String>()
            val photos = row.optJSONArray("screenshots")
            if (photos != null) {
                for (i in 0 until minOf(photos.length(), 12)) {
                    val item = photos.optString(i)
                    if (https(item)) images.add(item)
                }
            }
            val banner = row.optString("bannerUrl")
            results.add(
                YasserCatalogApp(
                    id = id,
                    name = row.getString("name"),
                    packageName = pkg,
                    category = if (row.optString("category") == "game") "game" else "app",
                    version = row.getString("version"),
                    versionCode = row.getInt("versionCode"),
                    apkUrl = url,
                    iconUrl = icon,
                    bannerUrl = banner.takeIf(::https).orEmpty(),
                    screenshots = images,
                    summary = row.optString("summary"),
                    description = row.optString("description"),
                    changelog = row.optString("changelog"),
                    sizeBytes = row.optLong("sizeBytes", 0L).coerceAtLeast(0L),
                    featured = row.optBoolean("featured", false)
                )
            )
        }
        return results
    }

    fun cached(context: Context): YasserCatalogResult {
        val text = context.getSharedPreferences(STORE_PREFS, Context.MODE_PRIVATE)
            .getString(CACHE_KEY, null)
        if (text.isNullOrEmpty()) return YasserCatalogResult()
        return runCatching {
            YasserCatalogResult(apps = parse(text), fromCache = true)
        }.getOrDefault(YasserCatalogResult())
    }

    suspend fun refresh(context: Context): YasserCatalogResult = withContext(Dispatchers.IO) {
        val saved = cached(context)
        val conn = (URL(URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "YASSERAPPS-Android")
        }
        try {
            check(conn.responseCode == 200) { "HTTP " + conn.responseCode }
            // Limit untrusted payloads to 2 MB. Catalog updates do not install code.
            val bytes = conn.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total += n
                    check(total <= 2_000_000) { "Catalog too large" }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            val text = bytes.toString(Charsets.UTF_8)
            val apps = parse(text)
            context.getSharedPreferences(STORE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(CACHE_KEY, text).apply()
            YasserCatalogResult(apps)
        } catch (e: Exception) {
            YasserCatalogResult(
                apps = saved.apps,
                warning = "تعذّر تحديث سورس YASSERAPPS؛ نعرض آخر نسخة محفوظة.",
                fromCache = saved.fromCache
            )
        } finally {
            conn.disconnect()
        }
    }
}
