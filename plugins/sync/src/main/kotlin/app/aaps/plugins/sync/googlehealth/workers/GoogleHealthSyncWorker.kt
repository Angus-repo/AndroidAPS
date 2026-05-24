package app.aaps.plugins.sync.googlehealth.workers

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.googlehealth.GoogleHealthUploader
import app.aaps.plugins.sync.googlehealth.events.EventGoogleHealthNewLog
import app.aaps.plugins.sync.googlehealth.events.EventGoogleHealthUpdateGUI
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class GoogleHealthSyncWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var uploader: GoogleHealthUploader
    @Inject lateinit var rxBus: RxBus

    override suspend fun doWorkAndLog(): Result {
        rxBus.send(EventGoogleHealthNewLog("UPL", "Start (attempt #${runAttemptCount + 1})"))
        return try {
            when (val outcome = uploader.sync()) {
                is GoogleHealthUploader.SyncOutcome.Success -> {
                    rxBus.send(EventGoogleHealthUpdateGUI())
                    Result.success()
                }
                is GoogleHealthUploader.SyncOutcome.TransientFailure -> {
                    rxBus.send(EventGoogleHealthNewLog("UPL", "Retrying later: ${outcome.message}"))
                    rxBus.send(EventGoogleHealthUpdateGUI())
                    Result.retry()
                }
                is GoogleHealthUploader.SyncOutcome.PermanentFailure -> {
                    rxBus.send(EventGoogleHealthUpdateGUI())
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            rxBus.send(EventGoogleHealthNewLog("ERR", "Unexpected error (will retry): ${e.message}"))
            rxBus.send(EventGoogleHealthUpdateGUI())
            Result.retry()
        }
    }
}
