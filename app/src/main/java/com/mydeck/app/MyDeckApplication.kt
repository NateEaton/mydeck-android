package com.mydeck.app

import android.app.Application

import dagger.hilt.android.HiltAndroidApp
import com.mydeck.app.util.createLogDir
import fr.bipi.treessence.context.GlobalContext.startTimber
import timber.log.Timber
import java.io.File


@HiltAndroidApp
class MyDeckApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        cleanupOldLogs()
        initTimberLog()
        Thread.setDefaultUncaughtExceptionHandler(
            CustomExceptionHandler(this)
        )
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
            e.printStackTrace()
        } finally {
            // If there was a default handler, call it to let the system handle the crash
            defaultHandler?.uncaughtException(thread, throwable) ?: android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}

const val LOGFILE = "MyDeckAppLog"
const val LOGDIR = "logs"
