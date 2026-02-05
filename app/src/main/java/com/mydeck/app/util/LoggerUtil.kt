package com.mydeck.app.util


import com.mydeck.app.BuildConfig
import com.mydeck.app.LOGDIR
import fr.bipi.treessence.file.FileLoggerTree
import timber.log.Timber
import java.io.File

fun getLatestLogFile(): File? {
    val tree = Timber.forest().firstOrNull { it is FileLoggerTree } as FileLoggerTree?
    return tree?.files?.sortedBy { it.name }?.firstOrNull()
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