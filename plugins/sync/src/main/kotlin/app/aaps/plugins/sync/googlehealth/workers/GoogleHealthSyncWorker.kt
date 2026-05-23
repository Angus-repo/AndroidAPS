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
        rxBus.send(EventGoogleHealthNewLog("UPL", "Start"))
        return try {
            uploader.sync()
            rxBus.send(EventGoogleHealthNewLog("UPL", "End"))
            rxBus.send(EventGoogleHealthUpdateGUI())
            Result.success()
        } catch (e: Exception) {
            rxBus.send(EventGoogleHealthNewLog("ERR", e.message))
            rxBus.send(EventGoogleHealthUpdateGUI())
            Result.failure()
        }
    }
}
