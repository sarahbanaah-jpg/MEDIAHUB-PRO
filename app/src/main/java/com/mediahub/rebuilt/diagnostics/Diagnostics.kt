package com.mediahub.rebuilt.diagnostics

import android.content.Context
import android.os.Build
import com.mediahub.rebuilt.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Diagnostics {
    fun createReport(context: Context): File {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val report = File(dir, "mediahub-diagnostics-${System.currentTimeMillis()}.zip")
        val info = """
            MediaHub Pro diagnostics
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            App ID: ${BuildConfig.APPLICATION_ID}
            Generated: ${java.util.Date()}
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
            zip.putNextEntry(ZipEntry("diagnostic-checks.txt"))
            zip.write(("Cache writable: ${context.cacheDir.canWrite()}\nFiles writable: ${context.filesDir.canWrite()}\n").toByteArray())
            zip.closeEntry()
        }
        return report
    }
}
