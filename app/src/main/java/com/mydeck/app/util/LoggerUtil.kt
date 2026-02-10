package com.mydeck.app.util


import com.mydeck.app.BuildConfig
import com.mydeck.app.LOGDIR
import fr.bipi.treessence.file.FileLoggerTree
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class LogFileInfo(
    val file: File,
    val name: String,
    val sizeKb: Int,
    val label: String
)

fun getLatestLogFile(): File? {
    return getAllLogFiles().firstOrNull()?.file
}

fun getAllLogFiles(): List<LogFileInfo> {
    val tree = Timber.forest().firstOrNull { it is FileLoggerTree } as FileLoggerTree?
    val files = tree?.files?.sortedBy { it.name } ?: emptyList()

    return files.mapIndexed { index, file ->
        val label = when {
            index == 0 -> "Current"
            files.size >= 3 && index == files.lastIndex -> "Oldest"
            else -> "Previous"
        }
        LogFileInfo(
            file = file,
            name = file.name,
            sizeKb = (file.length() / 1024).toInt(),
            label = label
        )
    }
}

fun createLogDir(parentDir: File): File? {
    val logdir = File(parentDir, LOGDIR)
    return if (logdir.isDirectory) {
        Timber.tag("LOGDIR").i("logdir $logdir already exists")
        logdir
    } else {
        logdir.mkdirs().let {
            if (it) {
                Timber.tag("LOGDIR").i("logdir $logdir created")
                logdir
            } else {
                Timber.tag("LOGDIR").w("logdir $logdir not created")
                null
            }
        }
    }
}

fun logAppInfo() {
    Timber.tag("APP-INFO")
    Timber.i("versionName=${BuildConfig.VERSION_NAME}")
    Timber.tag("APP-INFO")
    Timber.i("versionCode=${BuildConfig.VERSION_CODE}")
    Timber.tag("APP-INFO")
    Timber.i("flavor=${BuildConfig.FLAVOR}")
}

fun clearLogFiles(): Boolean {
    val tree = Timber.forest().firstOrNull { it is FileLoggerTree } as FileLoggerTree?
    return tree?.files?.all { file ->
        try {
            file.writeText("")
            true
        } catch (e: Exception) {
            Timber.tag("LOGDIR").e(e, "Failed to clear log file: ${file.name}")
            false
        }
    } ?: false
}

fun createLogFilesZip(context: android.content.Context): File? {
    val tree = Timber.forest().firstOrNull { it is FileLoggerTree } as FileLoggerTree?
    val logFiles = tree?.files ?: return null

    if (logFiles.isEmpty()) return null

    val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
    val zipFile = File(context.cacheDir, "mydeck-logs-$timestamp.zip")

    return try {
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            logFiles.sortedBy { it.name }.forEach { file ->
                try {
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(zip)
                    }
                    zip.closeEntry()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to add ${file.name} to zip")
                }
            }
        }
        zipFile
    } catch (e: Exception) {
        Timber.e(e, "Failed to create log zip file")
        zipFile.delete()
        null
    }
}
