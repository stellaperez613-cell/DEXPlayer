package com.example.dexplayer.player

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

// ── Data model ────────────────────────────────────────────────────────────────

data class SubtitleCue(
    val startMs: Long,
    val endMs:   Long,
    val text:    String     // may contain \n for multi-line
)

// ── SRT parser ────────────────────────────────────────────────────────────────

object SrtParser {

    /**
     * Parses an SRT file from a URI.
     * Returns an empty list on any error so the player keeps working.
     */
    suspend fun parse(context: Context, uri: Uri): List<SubtitleCue> =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                    parseSrtText(text)
                } ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }

    private fun parseSrtText(raw: String): List<SubtitleCue> {
        // Normalize line endings, split into blocks separated by blank lines
        val blocks = raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
            .split(Regex("\n\\s*\n"))

        val cues = mutableListOf<SubtitleCue>()

        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 2) continue

            // First line is the sequence number — skip it
            // Find the timecode line (contains " --> ")
            val timecodeIdx = lines.indexOfFirst { " --> " in it }
            if (timecodeIdx < 0) continue

            val timecode = lines[timecodeIdx]
            val (startMs, endMs) = parseTimecode(timecode) ?: continue

            // Everything after the timecode line is subtitle text
            val textLines = lines.drop(timecodeIdx + 1)
            if (textLines.isEmpty()) continue

            val text = textLines
                .joinToString("\n")
                .replace(Regex("<[^>]+>"), "")  // strip HTML tags e.g. <i>, <b>, <font>
                .trim()

            if (text.isNotBlank()) {
                cues += SubtitleCue(startMs, endMs, text)
            }
        }

        return cues.sortedBy { it.startMs }
    }

    /** Parses "00:01:23,456 --> 00:01:25,789" (comma or dot as ms separator) */
    private fun parseTimecode(line: String): Pair<Long, Long>? {
        val parts = line.split(" --> ")
        if (parts.size != 2) return null
        val start = parseTime(parts[0].trim()) ?: return null
        val end   = parseTime(parts[1].trim()) ?: return null
        return start to end
    }

    private fun parseTime(s: String): Long? {
        return try {
            // Handles both "HH:MM:SS,mmm" and "HH:MM:SS.mmm"
            val normalized = s.replace(',', '.')
            val mainAndMs  = normalized.split('.')
            val ms         = mainAndMs.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
            val timeParts  = mainAndMs[0].split(':')
            val h          = timeParts.getOrNull(0)?.toLong() ?: 0L
            val m          = timeParts.getOrNull(1)?.toLong() ?: 0L
            val sec        = timeParts.getOrNull(2)?.toLong() ?: 0L
            (h * 3_600_000L) + (m * 60_000L) + (sec * 1_000L) + ms
        } catch (_: Exception) { null }
    }
}

// ── Auto-detection: find .srt next to the video ───────────────────────────────

object SubtitleFinder {

    /**
     * Given a video URI, tries to find a matching .srt file in the same folder.
     * Supports both content:// (MediaStore) and file:// URIs.
     *
     * Strategy:
     *   1. Get the video's display name  →  "MyMovie.mp4"
     *   2. Strip extension              →  "MyMovie"
     *   3. Query MediaStore for files named "MyMovie.srt" in the same parent path
     *
     * Returns the srt URI or null if not found.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun findSrt(context: Context, videoUri: Uri): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val displayName = getDisplayName(context, videoUri) ?: return@withContext null
                val baseName    = displayName.substringBeforeLast('.')

                // Build a list of candidate names (exact + language suffixes)
                val candidates  = buildCandidateNames(baseName)

                // Search MediaStore Downloads + Videos providers
                for (candidate in candidates) {
                    val found = queryMediaStore(context, candidate)
                    if (found != null) return@withContext found
                }
                null
            } catch (_: Exception) { null }
        }

    private fun buildCandidateNames(baseName: String): List<String> =
        listOf(
            "$baseName.srt",
            "$baseName.en.srt",
            "$baseName.es.srt",
            "$baseName.fr.srt",
            "$baseName.eng.srt",
            "${baseName}_en.srt",
            "${baseName}_es.srt",
        )

    private fun getDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryMediaStore(context: Context, fileName: String): Uri? {
        // Search in Downloads first (common place for manually added srt files)
        val collections = listOf(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external"),
        )
        for (collection in collections) {
            try {
                val result = context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MIME_TYPE),
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                    null
                )?.use { cur ->
                    if (cur.moveToFirst()) {
                        val id = cur.getLong(0)
                        Uri.withAppendedPath(collection, id.toString())
                    } else null
                }
                if (result != null) return result
            } catch (_: Exception) { continue }
        }
        return null
    }
}
