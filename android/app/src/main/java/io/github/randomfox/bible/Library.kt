package io.github.randomfox.bible

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** One translation offered for download, from catalog.json in the GitHub release "translations". */
data class CatalogEntry(
    val id: Int, val code: String, val name: String, val description: String, val license: String,
    val version: String, val sort: Int, val file: String?, val size: Long, val sha256: String?, val bundled: Boolean,
)

/** Fetches the catalog and downloads, checks and installs translation packs. Downloads outlive the screen. */
class Library(private val context: Context, private val db: BibleDb) {
    var catalog by mutableStateOf<List<CatalogEntry>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Download progress per translation id: 0..1 while downloading, null when idle. */
    val progress = mutableStateMapOf<Int, Float>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cached = File(context.filesDir, "catalog.json")
    private var baseUrl = BASE_URL

    init { runCatching { parse(cached.readText()) } }

    private fun parse(json: String) {
        val root = JSONObject(json)
        baseUrl = root.optString("base_url", BASE_URL)
        val list = root.getJSONArray("translations")
        catalog = (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            CatalogEntry(o.getInt("id"), o.getString("code"), o.getString("name"), o.getString("description"),
                o.getString("license"), o.getString("version"), o.getInt("sort"), o.optString("file").ifEmpty { null },
                o.optLong("size"), o.optString("sha256").ifEmpty { null }, o.optBoolean("bundled"))
        }.sortedBy { it.sort }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val json = get(BASE_URL + "catalog.json").use { it.readBytes().decodeToString() }
            parse(json)
            cached.writeText(json)
            error = null
        } catch (e: Exception) {
            error = "Couldn't reach the translation list. Check your connection." +
                if (catalog != null) " Showing the last list downloaded." else ""
        }
    }

    fun download(e: CatalogEntry) {
        if (e.file == null || progress[e.id] != null) return
        progress[e.id] = 0f
        scope.launch {
            val gz = File(context.cacheDir, e.file)
            val raw = File(context.cacheDir, "${e.code}.db")
            try {
                val sha = MessageDigest.getInstance("SHA-256")
                get(baseUrl + e.file).use { input ->
                    gz.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); sha.update(buf, 0, n); done += n
                            if (e.size > 0) progress[e.id] = (done.toFloat() / e.size).coerceAtMost(1f)
                        }
                    }
                }
                val hex = sha.digest().joinToString("") { "%02x".format(it) }
                check(e.sha256 == null || hex == e.sha256) { "The download was damaged. Try again." }
                progress[e.id] = 1f
                GZIPInputStream(gz.inputStream()).use { input -> raw.outputStream().use { input.copyTo(it, 1 shl 16) } }
                db.install(raw)
                error = null
            } catch (ex: Exception) {
                error = "${e.name}: " + (ex.message?.takeIf { ex is IllegalStateException || ex is IllegalArgumentException }
                    ?: "download failed. Check your connection and try again.")
            } finally {
                gz.delete(); raw.delete()
                progress.remove(e.id)
            }
        }
    }

    private fun get(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
        check(responseCode == 200) { "Server returned $responseCode." }
        inputStream
    }

    companion object {
        const val BASE_URL = "https://github.com/randomfox/bible-translation/releases/download/translations/"
    }
}

@Composable
fun LibraryScreen(db: BibleDb, state: AppState, library: Library) {
    val c = LocalBibleColors.current
    LaunchedEffect(Unit) { library.refresh() }
    val installed = db.translations
    val entries = (library.catalog.orEmpty() + installed.filter { t -> library.catalog.orEmpty().none { it.id == t.id } }
        .map { CatalogEntry(it.id, it.code, it.name, it.description, it.license, it.version, 999, null, 0, null, it.id == BibleDb.CORE_ID) })
    Scaffold(topBar = { BlueBar({ Text("Translations") }, onBack = { state.screen = Screen.Reader }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                Text("Download the translations you want to read and compare. They are stored on your phone and work offline.",
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = c.muted, fontSize = 14.sp)
                library.error?.let { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 4.dp), color = c.severity[2], fontSize = 14.sp) }
                if (library.catalog == null && library.error == null)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp))
            }
            items(entries, key = { it.id }) { e ->
                val have = db.installedVersion(e.id)
                val p = library.progress[e.id]
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${e.name} (${e.code})", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(listOfNotNull(if (e.size > 0) "%.1f MB".format(e.size / 1e6) else null, e.license)
                                .joinToString(" · "), color = c.muted, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        when {
                            e.bundled -> Text("Built in", color = c.muted, fontSize = 14.sp)
                            p != null -> Text(if (p >= 1f) "Installing…" else "${(p * 100).toInt()}%", color = c.verseNumber)
                            have == null -> Button({ library.download(e) }, enabled = e.file != null) { Text("Download") }
                            e.file != null && have != e.version -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ remove(db, state, e.id) }) { Text("Remove") }
                                Button({ library.download(e) }) { Text("Update") }
                            }
                            else -> OutlinedButton({ remove(db, state, e.id) }) { Text("Remove") }
                        }
                    }
                    if (p != null) LinearProgressIndicator({ p }, Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(e.description, Modifier.padding(top = 6.dp), fontSize = 14.sp, lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                }
                HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = c.muted.copy(alpha = 0.15f))
            }
        }
    }
}

private fun remove(db: BibleDb, state: AppState, id: Int) {
    if (state.translationId == id) { state.translationId = BibleDb.CORE_ID; state.chapter = 1; state.target = null }
    state.compare = state.compare - id
    state.save()
    db.remove(id)
}
