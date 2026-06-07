package com.example.dexplayer.player

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

// ── Data model ────────────────────────────────────────────────────────────────

data class Playlist(
    val id:    String             = UUID.randomUUID().toString(),
    val name:  String,
    val items: List<PlaylistItem> = emptyList(),
    val createdAt: Long           = System.currentTimeMillis()
)

// ── Repository ────────────────────────────────────────────────────────────────

class PlaylistRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("dexplayer_playlists", Context.MODE_PRIVATE)
    private val KEY   = "playlists_json"

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun loadAll(): List<Playlist> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { parsePlaylist(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun save(playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { arr.put(serializePlaylist(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun create(name: String): Playlist {
        val pl  = Playlist(name = name.trim())
        val all = loadAll().toMutableList().also { it.add(pl) }
        save(all)
        return pl
    }

    fun rename(id: String, newName: String) {
        val all = loadAll().map { if (it.id == id) it.copy(name = newName.trim()) else it }
        save(all)
    }

    fun delete(id: String) {
        save(loadAll().filter { it.id != id })
    }

    fun addItems(playlistId: String, newItems: List<PlaylistItem>) {
        val all = loadAll().map { pl ->
            if (pl.id == playlistId) {
                // Deduplicate by URI
                val existing = pl.items.map { it.uri.toString() }.toSet()
                val toAdd    = newItems.filter { it.uri.toString() !in existing }
                pl.copy(items = pl.items + toAdd)
            } else pl
        }
        save(all)
    }

    fun removeItem(playlistId: String, itemIndex: Int) {
        val all = loadAll().map { pl ->
            if (pl.id == playlistId) {
                pl.copy(items = pl.items.toMutableList().also { it.removeAt(itemIndex) })
            } else pl
        }
        save(all)
    }

    fun reorderItems(playlistId: String, from: Int, to: Int) {
        val all = loadAll().map { pl ->
            if (pl.id == playlistId) {
                val list = pl.items.toMutableList()
                val item = list.removeAt(from)
                list.add(to, item)
                pl.copy(items = list)
            } else pl
        }
        save(all)
    }

    // ── M3U Export ────────────────────────────────────────────────────────────

    fun exportM3U(playlist: Playlist, destUri: Uri): Boolean {
        return try {
            // MODE_WRITE truncates the file cleanly before writing
            val out = context.contentResolver.openOutputStream(destUri, "wt")
                ?: context.contentResolver.openOutputStream(destUri)
                ?: return false

            out.use { stream ->
                val w = OutputStreamWriter(stream, Charsets.UTF_8)
                w.write("#EXTM3U\n")
                w.write("# DexPlayer export — ${playlist.name}\n")
                w.write("# ${playlist.items.size} tracks\n\n")
                playlist.items.forEach { item ->
                    val display = buildString {
                        if (item.artist.isNotBlank()) append("${item.artist} - ")
                        append(item.title.ifBlank { item.uri.lastPathSegment ?: "Track" })
                    }
                    // #EXTINF duration (unknown = -1), display name
                    w.write("#EXTINF:-1,$display\n")
                    // Write full URI — content:// URIs are preserved exactly
                    w.write("${item.uri}\n")
                    // Optional extra metadata comments for round-trip fidelity
                    if (item.title.isNotBlank())  w.write("#DEXTITLE:${item.title}\n")
                    if (item.artist.isNotBlank()) w.write("#DEXARTIST:${item.artist}\n")
                    w.write("\n")
                }
                w.flush()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepo", "exportM3U failed", e)
            false
        }
    }

    // ── M3U Import ────────────────────────────────────────────────────────────
    // Reads a .m3u / .m3u8 file from a SAF URI and creates a new Playlist

    // ── M3U Import ────────────────────────────────────────────────────────────

    fun importM3U(sourceUri: Uri, suggestedName: String): Playlist? {
        return try {
            // Take persistable permission so we can re-read the file later if needed
            try {
                context.contentResolver.takePersistableUriPermission(
                    sourceUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* not all URIs support this — ignore */ }

            val lines = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readLines()
            } ?: run {
                android.util.Log.e("PlaylistRepo", "importM3U: could not open $sourceUri")
                return null
            }

            android.util.Log.d("PlaylistRepo", "importM3U: read ${lines.size} lines from $sourceUri")

            val items         = mutableListOf<PlaylistItem>()
            var pendingTitle  = ""
            var pendingArtist = ""

            lines.forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#EXTINF:") -> {
                        // #EXTINF:-1,Artist - Title
                        val info = trimmed.substringAfter(",", "").trim()
                        if (info.contains(" - ")) {
                            pendingArtist = info.substringBefore(" - ").trim()
                            pendingTitle  = info.substringAfter(" - ").trim()
                        } else {
                            pendingArtist = ""
                            pendingTitle  = info
                        }
                    }
                    // DexPlayer custom metadata comments — override EXTINF if present
                    trimmed.startsWith("#DEXTITLE:") -> {
                        pendingTitle = trimmed.removePrefix("#DEXTITLE:").trim()
                    }
                    trimmed.startsWith("#DEXARTIST:") -> {
                        pendingArtist = trimmed.removePrefix("#DEXARTIST:").trim()
                    }
                    trimmed.startsWith("#") || trimmed.isBlank() -> { /* skip other comments */ }
                    else -> {
                        // URI or file path line
                        val uri = resolveM3ULine(trimmed)
                        if (uri != null) {
                            val isVideo = isVideoUri(trimmed)
                            val title   = pendingTitle.ifBlank { uri.lastPathSegment ?: "Track" }
                            items.add(PlaylistItem(
                                uri     = uri,
                                isVideo = isVideo,
                                title   = title,
                                artist  = pendingArtist
                            ))
                            android.util.Log.d("PlaylistRepo", "importM3U: added $title — $uri")
                        } else {
                            android.util.Log.w("PlaylistRepo", "importM3U: could not resolve line: $trimmed")
                        }
                        pendingTitle  = ""
                        pendingArtist = ""
                    }
                }
            }

            android.util.Log.d("PlaylistRepo", "importM3U: total items=${items.size}")

            if (items.isEmpty()) {
                android.util.Log.w("PlaylistRepo", "importM3U: no items found in file")
                return null
            }

            val pl  = Playlist(name = suggestedName, items = items)
            val all = loadAll().toMutableList().also { it.add(pl) }
            save(all)
            pl
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepo", "importM3U failed", e)
            null
        }
    }

    private fun resolveM3ULine(line: String): Uri? {
        return try {
            when {
                line.startsWith("content://") -> Uri.parse(line)
                line.startsWith("file://")    -> Uri.parse(line)
                line.startsWith("http://") || line.startsWith("https://") -> Uri.parse(line)
                line.startsWith("/")          -> Uri.parse("file://$line")
                else -> {
                    // Relative path or unknown — try as-is
                    Uri.parse(line)
                }
            }
        } catch (e: Exception) { null }
    }

    private fun isVideoUri(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp4","mkv","avi","mov","wmv","flv","webm","m4v","3gp","ts","m2ts")
    }

    // ── JSON serialization ────────────────────────────────────────────────────

    private fun serializePlaylist(pl: Playlist): JSONObject = JSONObject().apply {
        put("id",        pl.id)
        put("name",      pl.name)
        put("createdAt", pl.createdAt)
        val arr = JSONArray()
        pl.items.forEach { item ->
            arr.put(JSONObject().apply {
                put("uri",     item.uri.toString())
                put("isVideo", item.isVideo)
                put("title",   item.title)
                put("artist",  item.artist)
            })
        }
        put("items", arr)
    }

    private fun parsePlaylist(obj: JSONObject): Playlist {
        val arr   = obj.getJSONArray("items")
        val items = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PlaylistItem(
                uri     = Uri.parse(o.getString("uri")),
                isVideo = o.optBoolean("isVideo", false),
                title   = o.optString("title", ""),
                artist  = o.optString("artist", "")
            )
        }
        return Playlist(
            id        = obj.getString("id"),
            name      = obj.getString("name"),
            items     = items,
            createdAt = obj.optLong("createdAt", 0L)
        )
    }
}
