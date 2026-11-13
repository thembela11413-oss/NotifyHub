package com.notifyhub.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notifyhub.NotifyHubApplication
import com.notifyhub.data.settings.ConnectionState
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as NotifyHubApplication).container
        val settings = c.settings.current()
        val check = UrlPolicy.validate(settings.serverUrl, settings.allowHttp)
        if (check !is UrlCheck.Valid) {
            c.settings.setConnection(ConnectionState.NOT_CONFIGURED, null)
            return Result.success() // nothing to do until a server is configured; events stay PENDING
        }
        val dao = c.db.notificationDao()
        val token = c.credentials.load()
        val deviceId = c.settings.deviceId()
        dao.resetStaleSending()

        while (true) {
            val batch = dao.getSendable(BATCH_SIZE, MAX_ATTEMPTS)
            if (batch.isEmpty()) return Result.success()
            val ids = batch.map { it.eventId }
            dao.markSending(ids, System.currentTimeMillis())
            when (val result = c.transport.send(check.url, token, batch.map { it.toPayload() }, deviceId)) {
                SendResult.Success -> {
                    dao.markDelivered(ids, System.currentTimeMillis())
                    c.settings.setLastSync(System.currentTimeMillis())
                    c.settings.setConnection(ConnectionState.OK, null)
                }
                is SendResult.Failure -> {
                    dao.markFailed(ids, result.message)
                    c.settings.setConnection(ConnectionState.ERROR, result.message)
                    return if (result.retryable) Result.retry() else Result.failure()
                }
            }
        }
    }

    companion object {
        const val BATCH_SIZE = 50
        const val MAX_ATTEMPTS = 10
    }
}

object SyncScheduler {
    private const val PERIODIC = "notifyhub-sync-periodic"
    private const val NOW = "notifyhub-sync-now"
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Backstop that retries pending events every 15 minutes once a server is configured. */
    fun ensurePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun syncNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
    }
}
