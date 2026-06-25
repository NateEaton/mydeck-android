package com.mydeck.app.util

import android.os.Process
import android.util.Log
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Rotating file-logging [Timber.Tree], backed by [java.util.logging.FileHandler].
 *
 * Self-contained replacement for the former `fr.bipi.treessence` (Treessence) dependency,
 * which was sourced from JitPack — not buildable in F-Droid's build environment. This uses
 * only the JDK, so the project has no JitPack dependencies.
 *
 * Extends [Timber.DebugTree] so log lines carry the auto-generated class/method tag, and
 * emits the same logcat-style layout Treessence produced:
 *
 *     MM-dd HH:mm:ss:SSS <L>/<tag>(<tid>) : <message>
 *
 * Logs are written to `<dir>/<fileName><generation>` files (generation 0 = current), keeping
 * up to [fileLimit] files of [Builder.sizeLimit] bytes each, rotating oldest-out as they fill.
 * Only [files] is consumed by the app (for the in-app log viewer / export).
 */
class FileLoggerTree private constructor(
    private val dir: File,
    private val fileBaseName: String,
    private val fileLimit: Int,
    private val minPriority: Int,
    handler: FileHandler,
) : Timber.DebugTree() {

    private val logger: Logger = Logger.getAnonymousLogger().apply {
        useParentHandlers = false
        level = Level.ALL
        addHandler(handler)
    }

    /** Existing rotating log files, generation 0 (current) first. */
    val files: List<File>
        get() = (0 until fileLimit)
            .map { File(dir, "$fileBaseName$it") }
            .filter { it.isFile }

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= minPriority

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < minPriority) return
        // Build the "<L>/<tag>(<tid>) : <message>" body on the calling thread (so the thread id
        // is correct). LineFormatter prepends the timestamp under the handler lock (thread-safe).
        // Timber has already appended any throwable's stack trace to `message`.
        val body = "${priorityChar(priority)}/${tag ?: "MyDeck"}(${Process.myTid()}) : $message"
        logger.log(julLevel(priority), body)
    }

    private fun priorityChar(priority: Int): Char = when (priority) {
        Log.VERBOSE -> 'V'
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        else -> 'A'
    }

    private fun julLevel(priority: Int): Level = when (priority) {
        Log.VERBOSE, Log.DEBUG -> Level.FINE
        Log.INFO -> Level.INFO
        Log.WARN -> Level.WARNING
        else -> Level.SEVERE
    }

    class Builder {
        private var dir: String = "."
        private var fileName: String = "log"
        private var fileLimit: Int = 3
        private var sizeLimit: Int = 128 * 1024
        private var appendToFile: Boolean = true
        private var minPriority: Int = Log.DEBUG

        fun dir(value: String) = apply { dir = value }
        fun fileName(value: String) = apply { fileName = value }
        fun fileLimit(value: Int) = apply { fileLimit = value }
        fun sizeLimit(value: Int) = apply { sizeLimit = value }
        fun appendToFile(value: Boolean) = apply { appendToFile = value }
        fun minPriority(value: Int) = apply { minPriority = value }

        fun build(): FileLoggerTree {
            val directory = File(dir).apply { mkdirs() }
            // "%g" expands to the generation number, e.g. "MyDeckAppLog0", "MyDeckAppLog1", ...
            val pattern = File(directory, "$fileName%g").absolutePath
            val handler = FileHandler(pattern, sizeLimit, fileLimit, appendToFile).apply {
                formatter = LineFormatter()
                level = Level.ALL
            }
            return FileLoggerTree(directory, fileName, fileLimit, minPriority, handler)
        }
    }

    private class LineFormatter : Formatter() {
        private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss:SSS", Locale.US)
        override fun format(record: LogRecord): String =
            "${dateFormat.format(Date(record.millis))} ${record.message}\n"
    }
}
