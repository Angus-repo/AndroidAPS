package app.aaps.plugins.sync.googlehealth

import android.content.Context
import android.text.Spanned
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.HtmlHelper
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.googlehealth.events.EventGoogleHealthNewLog
import app.aaps.plugins.sync.googlehealth.events.EventGoogleHealthUpdateGUI
import app.aaps.plugins.sync.googlehealth.keys.GoogleHealthBooleanKey
import app.aaps.plugins.sync.googlehealth.workers.GoogleHealthSyncWorker
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleHealthPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
) : Sync, PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(GoogleHealthFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_blooddrop_48)
        .pluginName(R.string.google_health)
        .shortName(R.string.google_health_short)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_google_health),
    ownPreferences = listOf(GoogleHealthBooleanKey::class.java),
    aapsLogger, rh, preferences
) {

    @Suppress("PrivatePropertyName")
    private val JOB_NAME = this::class.java.simpleName

    private val disposable = CompositeDisposable()
    private val listLog: MutableList<EventGoogleHealthNewLog> = ArrayList()
    private var eventWorker: ScheduledExecutorService? = null
    private var scheduledEventPost: ScheduledFuture<*>? = null

    override val hasWritePermission: Boolean = true
    override val connected: Boolean = true
    override val status: String = ""

    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ WorkManager.getInstance(context).cancelUniqueWork(JOB_NAME) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventGoogleHealthNewLog::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                addToLog(event)
                aapsLogger.debug(LTag.CORE, "GoogleHealth ${event.action} ${event.logText}")
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ delayAndScheduleExecution("NEW_BG") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewHistoryData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ delayAndScheduleExecution("NEW_DATA") }, fabricPrivacy::logException)
        eventWorker = Executors.newSingleThreadScheduledExecutor()
    }

    override fun onStop() {
        super.onStop()
        scheduledEventPost?.cancel(false)
        scheduledEventPost = null
        eventWorker?.shutdown()
        eventWorker = null
        disposable.clear()
    }

    fun clearLog() {
        synchronized(listLog) { listLog.clear() }
        rxBus.send(EventGoogleHealthUpdateGUI())
    }

    fun triggerFullSync() {
        executeSync("FULL_SYNC")
    }

    private fun addToLog(event: EventGoogleHealthNewLog) {
        synchronized(listLog) {
            listLog.add(event)
            if (listLog.size >= Constants.MAX_LOG_LINES) listLog.removeAt(0)
        }
        rxBus.send(EventGoogleHealthUpdateGUI())
    }

    fun textLog(): Spanned {
        return try {
            val sb = StringBuilder()
            synchronized(listLog) { listLog.forEach { sb.append(it.toPreparedHtml()) } }
            HtmlHelper.fromHtml(sb.toString())
        } catch (_: OutOfMemoryError) {
            HtmlHelper.fromHtml("")
        }
    }

    private fun executeSync(origin: String) {
        rxBus.send(EventGoogleHealthNewLog("RUN", "Starting sync: $origin"))
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                JOB_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(GoogleHealthSyncWorker::class.java).build()
            )
    }

    private fun delayAndScheduleExecution(origin: String) {
        class PostRunnable : Runnable {
            override fun run() {
                scheduledEventPost = null
                executeSync(origin)
            }
        }
        scheduledEventPost?.cancel(false)
        scheduledEventPost = eventWorker?.schedule(PostRunnable(), 10, TimeUnit.SECONDS)
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "google_health_settings") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "google_health_settings"
            title = rh.gs(R.string.google_health)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = GoogleHealthBooleanKey.SyncBloodGlucose, title = R.string.google_health_sync_bg_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = GoogleHealthBooleanKey.SyncCarbs, title = R.string.google_health_sync_carbs_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = GoogleHealthBooleanKey.SyncExercise, title = R.string.google_health_sync_exercise_title))
        }
    }
}
