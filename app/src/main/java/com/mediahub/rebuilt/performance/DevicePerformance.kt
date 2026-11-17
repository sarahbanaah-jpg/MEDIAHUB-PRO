package com.mediahub.rebuilt.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.mediahub.rebuilt.BuildConfig
import java.io.File

data class PerformanceSnapshot(
    val availableRamMb: Long,
    val totalRamMb: Long,
    val appUsedMb: Long,
    val freeStorageMb: Long,
    val totalStorageMb: Long,
    val batteryPercent: Int,
    val batteryTempC: Float?,
    val isCharging: Boolean,
    val uptimeMinutes: Long,
    val cacheSizeMb: Long
) {
    val ramUsedPercent: Int
        get() = if (totalRamMb <= 0) 0 else (((totalRamMb - availableRamMb) * 100) / totalRamMb).toInt().coerceIn(0, 100)

    val storageUsedPercent: Int
        get() = if (totalStorageMb <= 0) 0 else (((totalStorageMb - freeStorageMb) * 100) / totalStorageMb).toInt().coerceIn(0, 100)
}

object DevicePerformance {

    fun snapshot(context: Context): PerformanceSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem / (1024 * 1024)
        val availRam = memInfo.availMem / (1024 * 1024)

        val runtime = Runtime.getRuntime()
        val appUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        val storage = storageStats()
        val battery = batteryStats(context)
        val cacheMb = cacheSizeBytes(context) / (1024 * 1024)

        return PerformanceSnapshot(
            availableRamMb = availRam,
            totalRamMb = totalRam,
            appUsedMb = appUsed,
            freeStorageMb = storage.first,
            totalStorageMb = storage.second,
            batteryPercent = battery.first,
            batteryTempC = battery.second,
            isCharging = battery.third,
            uptimeMinutes = SystemClock.elapsedRealtime() / 60_000L,
            cacheSizeMb = cacheMb
        )
    }

    fun clearSafeCache(context: Context): Long {
        var freed = 0L
        listOf(context.cacheDir, context.externalCacheDir).forEach { dir ->
            if (dir != null && dir.exists()) {
                freed += deleteContents(dir)
            }
        }
        // Keep updates folder empty of partial downloads older than 1 day
        val updates = File(context.cacheDir, "updates")
        if (updates.exists()) {
            updates.listFiles()?.forEach { f ->
                if (System.currentTimeMillis() - f.lastModified() > 24 * 60 * 60 * 1000L) {
                    freed += f.length()
                    f.delete()
                }
            }
        }
        return freed / (1024 * 1024)
    }

    private fun storageStats(): Pair<Long, Long> {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val free = stat.availableBlocksLong * blockSize / (1024 * 1024)
            val total = stat.blockCountLong * blockSize / (1024 * 1024)
            free to total
        } catch (_: Exception) {
            0L to 0L
        }
    }

    private fun batteryStats(context: Context): Triple<Int, Float?, Boolean> {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            val temp = if (tempTenths != Int.MIN_VALUE) tempTenths / 10f else null
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            Triple(percent, temp, charging)
        } catch (_: Exception) {
            Triple(-1, null, false)
        }
    }

    private fun cacheSizeBytes(context: Context): Long {
        var total = 0L
        listOf(context.cacheDir, context.externalCacheDir).forEach { dir ->
            if (dir != null) total += sizeOf(dir)
        }
        return total
    }

    private fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var sum = 0L
        file.listFiles()?.forEach { sum += sizeOf(it) }
        return sum
    }

    private fun deleteContents(dir: File): Long {
        var freed = 0L
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                freed += deleteContents(child)
                child.delete()
            } else {
                freed += child.length()
                child.delete()
            }
        }
        return freed
    }

    fun summaryLines(snapshot: PerformanceSnapshot): List<String> {
        return listOf(
            "RAM free ${snapshot.availableRamMb} MB / ${snapshot.totalRamMb} MB (${snapshot.ramUsedPercent}% used)",
            "App memory ${snapshot.appUsedMb} MB",
            "Storage free ${snapshot.freeStorageMb} MB / ${snapshot.totalStorageMb} MB",
            if (snapshot.batteryPercent >= 0)
                "Battery ${snapshot.batteryPercent}%${if (snapshot.isCharging) " · charging" else ""}${snapshot.batteryTempC?.let { " · ${it}°C" } ?: ""}"
            else "Battery unavailable",
            "App cache ${snapshot.cacheSizeMb} MB · uptime ${snapshot.uptimeMinutes} min",
            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
    }
}
