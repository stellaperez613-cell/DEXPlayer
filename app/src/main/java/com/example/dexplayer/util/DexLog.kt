package com.example.dexplayer.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * DexLog — file-based logger for DexPlayer.
 *
 * Writes to:  Documents/DexPlayer/logs/dexplayer_YYYY-MM-DD.log
 *
 * Usage:
 *   DexLog.i("TAG", "message")
 *   DexLog.e("TAG", "message", exception)
 *   DexLog.section("Starting playlist manager")
 *
 * Call DexLog.init(context) from Application.onCreate()
 * Call DexLog.flush() before the process dies if possible
 */
object DexLog {

    // ── Config ────────────────────────────────────────────────────────────────
    private const val DIR_NAME   = "DexPlayer"
    private const val MAX_FILES  = 5        // keep last 5 days of logs
    private const val MAX_SIZE   = 5 * 1024 * 1024L  // 5 MB max per file

    // ── State ─────────────────────────────────────────────────────────────────
    private var logFile: File? = null
    private val queue   = LinkedBlockingQueue<String>(4096)
    private val running = AtomicBoolean(false)
    private val ts      = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        if (running.getAndSet(true)) return

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "$DIR_NAME/logs"
        )
        dir.mkdirs()

        val today   = dateStr.format(Date())
        logFile     = File(dir, "dexplayer_$today.log")

        // Rotate old files
        pruneOldLogs(dir)

        // Header for this session
        writeRaw("═".repeat(72))
        writeRaw("DexPlayer Session Started — ${ts.format(Date())}")
        writeRaw("Device : ${Build.MANUFACTURER} ${Build.MODEL}")
        writeRaw("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        writeRaw("ABI    : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
        writeRaw("═".repeat(72))

        // Background writer thread — drains queue to disk
        thread(name = "DexLog-Writer", isDaemon = true) {
            while (running.get() || queue.isNotEmpty()) {
                try {
                    val line = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (line != null) appendToFile(line)
                } catch (_: InterruptedException) {}
            }
        }

        // Catch uncaught exceptions — write full stack trace before crash
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                wtf("CRASH", "Uncaught exception on thread '${thread.name}'", throwable)
                flush()
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        i("DexLog", "Logger initialized — writing to ${logFile?.absolutePath}")
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Verbose */
    fun v(tag: String, msg: String) = log("V", tag, msg)

    /** Debug */
    fun d(tag: String, msg: String) = log("D", tag, msg)

    /** Info */
    fun i(tag: String, msg: String) = log("I", tag, msg)

    /** Warning */
    fun w(tag: String, msg: String, err: Throwable? = null) = log("W", tag, msg, err)

    /** Error */
    fun e(tag: String, msg: String, err: Throwable? = null) = log("E", tag, msg, err)

    /** What a Terrible Failure — crash level */
    fun wtf(tag: String, msg: String, err: Throwable? = null) = log("F", tag, msg, err)

    /**
     * Visual section divider — use at major lifecycle boundaries.
     * e.g. DexLog.section("Loading playlist")
     */
    fun section(title: String) {
        val line = "── $title " + "─".repeat(maxOf(0, 60 - title.length))
        enqueue(line)
    }

    /** Force all queued lines to disk — call before process death if possible */
    fun flush() {
        val deadline = System.currentTimeMillis() + 2000
        while (queue.isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
    }

    /** Returns the path to today's log file for display in Settings */
    fun logFilePath(): String = logFile?.absolutePath ?: "not initialized"

    /** Returns all lines from today's log file — for in-app log viewer */
    fun readLog(): String {
        return try { logFile?.readText() ?: "(log file not found)" }
        catch (e: Exception) { "(error reading log: ${e.message})" }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun log(level: String, tag: String, msg: String, err: Throwable? = null) {
        val time   = ts.format(Date())
        val thread = Thread.currentThread().name.take(20).padEnd(20)
        val line   = "$time [$level] [$thread] $tag: $msg"
        enqueue(line)
        if (err != null) {
            enqueue("$time [$level] [$thread] $tag: ${err.javaClass.name}: ${err.message}")
            // Write each stack frame
            err.stackTrace.take(20).forEach { frame ->
                enqueue("$time [$level] [$thread]     at $frame")
            }
            // Causes
            var cause = err.cause
            var depth = 0
            while (cause != null && depth < 3) {
                enqueue("$time [$level] [$thread] Caused by: ${cause.javaClass.name}: ${cause.message}")
                cause.stackTrace.take(10).forEach { frame ->
                    enqueue("$time [$level] [$thread]     at $frame")
                }
                cause = cause.cause
                depth++
            }
        }
    }

    private fun enqueue(line: String) {
        if (!queue.offer(line)) {
            // Queue full — drop oldest and try again
            queue.poll()
            queue.offer(line)
        }
    }

    private fun writeRaw(line: String) {
        enqueue(line)
    }

    private fun appendToFile(line: String) {
        try {
            val file = logFile ?: return
            // Roll over if too large
            if (file.exists() && file.length() > MAX_SIZE) {
                val rolled = File(file.parent, file.nameWithoutExtension + "_overflow.log")
                file.renameTo(rolled)
            }
            FileWriter(file, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println(line)
                }
            }
        } catch (_: Exception) {}
    }

    private fun pruneOldLogs(dir: File) {
        try {
            val logs = dir.listFiles { f -> f.extension == "log" }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            logs.drop(MAX_FILES).forEach { it.delete() }
        } catch (_: Exception) {}
    }
}
