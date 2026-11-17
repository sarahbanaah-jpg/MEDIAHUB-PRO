package com.mediahub.rebuilt.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.mediahub.rebuilt.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Diagnostics {
    fun createReport(context: Context): File {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val report = File(dir, "mediahub-diagnostics-${System.currentTimeMillis()}.zip")
        val stats = StatFs(Environment.getDataDirectory().path)
        val permissions = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS
        ).joinToString("\n") { permission ->
            "$permission: ${if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) "granted" else "not granted"}"
        }
        val info = """
            MediaHub Pro diagnostic report
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            Package: ${BuildConfig.APPLICATION_ID}
            Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Generated: ${Date()}
            Free internal storage: ${stats.availableBytes / (1024 * 1024)} MiB
            Cache writable: ${context.cacheDir.canWrite()}
            App files writable: ${context.filesDir.canWrite()}

            Permission status:
            $permissions
        """.trimIndent()
        ZipOutputStream(FileOutputStream(report)).use { zip ->
            zip.putNextEntry(ZipEntry("device-and-app.txt"))
            zip.write(info.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val crash = File(context.filesDir, "last-crash.txt")
            if (crash.isFile) {
                zip.putNextEntry(ZipEntry("last-crash.txt"))
                crash.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("report-notes.txt"))
            zip.write("This report contains app/device diagnostics and the app's last recorded uncaught crash only. It does not include personal media files. Review before sharing.".toByteArray())
            zip.closeEntry()
        }
        return report
    }

    fun clearLastCrash(context: Context) {
        File(context.filesDir, "last-crash.txt").delete()
    }
}
