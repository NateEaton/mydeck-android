package com.mydeck.app.util

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build

/**
 * Reads versionName / versionCode from the installed PackageInfo at runtime.
 *
 * Avoids `BuildConfig.VERSION_NAME` / `BuildConfig.VERSION_CODE`, which the
 * Kotlin compiler inlines at every call site as `static final` constants.
 * Incremental compilation does not invalidate inlined call sites when only
 * the generated `BuildConfig.java` value changes (the ABI is unchanged), so
 * those constants silently go stale across version bumps until a clean build.
 */
object AppVersion {
    fun versionName(context: Context): String =
        packageInfo(context).versionName.orEmpty()

    fun versionCode(context: Context): Long {
        val info = packageInfo(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun packageInfo(context: Context): PackageInfo =
        context.packageManager.getPackageInfo(context.packageName, 0)
}
