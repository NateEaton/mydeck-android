package com.mydeck.app

import android.app.Application
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.CachedAnnotationDao
import dagger.hilt.android.HiltAndroidApp
import com.mydeck.app.util.createLogDir
import fr.bipi.treessence.context.GlobalContext.startTimber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject


@HiltAndroidApp
class MyDeckApplication : Application() {

    @Inject lateinit var bookmarkDao: BookmarkDao
    @Inject lateinit var cachedAnnotationDao: CachedAnnotationDao

    override fun onCreate() {
        super.onCreate()
        cleanupStagingTables()
        cleanupOldLogs()
        initTimberLog()
        Thread.setDefaultUncaughtExceptionHandler(
            CustomExceptionHandler(this)
        )
    }

    private fun cleanupStagingTables() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                bookmarkDao.clearAllRemoteBookmarkIds()
                cachedAnnotationDao.clearRemoteAnnotationIds()
                Timber.d("Startup staging table cleanup complete")
            } catch (e: Exception) {
                Timber.w(e, "Startup staging table cleanup failed")
            }
        }
    }

    private fun cleanupOldLogs() {
        val retentionDays = getRetentionDays()
        try {
            val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
            val logDir = File(filesDir, LOGDIR)
            if (logDir.exists() && logDir.isDirectory) {
                logDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to cleanup old logs")
        }
    }

    private fun getRetentionDays(): Int {
        return if (isDebugBuild()) 7 else 30
    }

    private fun initTimberLog() {
        val logDir = createLogDir(filesDir)
        startTimber {
            if (isDebugBuild()) {
                debugTree()
                logDir?.let {
                    fileTree {
                        level = 3 // Log.DEBUG
                        fileName = LOGFILE
                        dir = it.absolutePath
                        fileLimit = 3
                        sizeLimit = 128 * 1024 // 128 KB in bytes
                        appendToFile = true
                    }
                }
            } else {
                logDir?.let {
                    fileTree {
                        level = 5 // Log.WARN
                        fileName = LOGFILE
                        dir = it.absolutePath
                        fileLimit = 3
                        sizeLimit = 128 * 1024 // 128 KB in bytes
                        appendToFile = true
                    }
                }
            }
        }
    }

    private fun isDebugBuild(): Boolean {
        return BuildConfig.DEBUG || BuildConfig.BUILD_TYPE.contains("debug", ignoreCase = true)
    }
}

class CustomExceptionHandler(private val application: Application) :
    Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Timber.e(throwable, "CRASH: Uncaught exception")
        } catch (e: Exception) {
            // Handle any exceptions that occur during logging (e.g., file write errors)
            android.util.Log.w("CrashHandler", "Timber failed while logging crash", e)
        } finally {
            // If there was a default handler, call it to let the system handle the crash
            defaultHandler?.uncaughtException(thread, throwable) ?: android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}

const val LOGFILE = "MyDeckAppLog"
const val LOGDIR = "logs"
