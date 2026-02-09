package com.mydeck.app

import android.app.Application

import dagger.hilt.android.HiltAndroidApp
import com.mydeck.app.util.createLogDir
import com.mydeck.app.io.prefs.SettingsDataStore
import fr.bipi.treessence.context.GlobalContext.startTimber
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import javax.inject.Inject


@HiltAndroidApp
class MyDeckApplication : Application() {
    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate() {
        super.onCreate()
        cleanupOldLogs()
        initTimberLog()
        Thread.setDefaultUncaughtExceptionHandler(
            CustomExceptionHandler(this)
        )
    }

    private fun cleanupOldLogs() {
        try {
            val retentionDays = runBlocking {
                settingsDataStore.getLogRetentionDays()
            }
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
            e.printStackTrace()
        }
    }

    private fun initTimberLog() {
        val logDir = createLogDir(filesDir)
        startTimber {
            if (BuildConfig.DEBUG) {
                debugTree()
                logDir?.let {
                    fileTree {
                        level = 3 // Log.DEBUG
                        fileName = LOGFILE
                        dir = it.absolutePath
                        fileLimit = 3
                        sizeLimit = 128
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
                        sizeLimit = 128
                        appendToFile = true
                    }
                }
            }
        }
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
