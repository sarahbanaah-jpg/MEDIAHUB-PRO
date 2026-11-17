package com.mediahub.rebuilt.updates

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            UpdateChecker.checkInBackground(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
