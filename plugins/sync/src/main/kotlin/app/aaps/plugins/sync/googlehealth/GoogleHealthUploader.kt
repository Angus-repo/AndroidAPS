package app.aaps.plugins.sync.googlehealth

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Mass
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.googlehealth.events.EventGoogleHealthNewLog
import app.aaps.plugins.sync.googlehealth.keys.GoogleHealthBooleanKey
import app.aaps.plugins.sync.googlehealth.keys.GoogleHealthLongKey
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class GoogleHealthUploader @Inject constructor(
    private val context: Context,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
) {

    sealed class SyncOutcome {
        object Success : SyncOutcome()
        object PermanentFailure : SyncOutcome()
        data class TransientFailure(val message: String) : SyncOutcome()
    }

    suspend fun sync(): SyncOutcome {
        val availability = HealthConnectClient.getSdkStatus(context)
        if (availability != HealthConnectClient.SDK_AVAILABLE) {
            rxBus.send(EventGoogleHealthNewLog("ERR", "Health Connect not available (status=$availability)"))
            return SyncOutcome.PermanentFailure
        }

        val client = HealthConnectClient.getOrCreate(context)

        val requiredPermissions = buildSet {
            if (preferences.get(GoogleHealthBooleanKey.SyncBloodGlucose))
                add(HealthPermission.getWritePermission(BloodGlucoseRecord::class))
            if (preferences.get(GoogleHealthBooleanKey.SyncCarbs))
                add(HealthPermission.getWritePermission(NutritionRecord::class))
            if (preferences.get(GoogleHealthBooleanKey.SyncExercise))
                add(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
        }
        val grantedPermissions = client.permissionController.getGrantedPermissions()
        if (!grantedPermissions.containsAll(requiredPermissions)) {
            rxBus.send(EventGoogleHealthNewLog("ERR", "Missing Health Connect permissions — open the Google Health Connect plugin screen and tap Grant Permissions"))
            return SyncOutcome.PermanentFailure
        }

        val now = dateUtil.now()
        val cap = now - T.hours(24).msecs() // never look back more than 24h

        var totalInserted = 0

        if (preferences.get(GoogleHealthBooleanKey.SyncBloodGlucose)) {
            val lastSynced = preferences.get(GoogleHealthLongKey.LastSyncedBgAt)
            val from = max(lastSynced, cap)
            val bgList = persistenceLayer.getBgReadingsDataFromTime(from, true).blockingGet()
                .filter { it.timestamp > lastSynced }
            if (bgList.isNotEmpty()) {
                val records = bgList.mapNotNull { gv ->
                    try {
                        BloodGlucoseRecord(
                            time = Instant.ofEpochMilli(gv.timestamp),
                            zoneOffset = ZoneOffset.UTC,
                            metadata = Metadata.manualEntry(clientRecordId = "aaps-bg-${gv.timestamp}"),
                            level = BloodGlucose.milligramsPerDeciliter(gv.value),
                            specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
                            relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                        )
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.CORE, "Error converting BG record", e)
                        null
                    }
                }
                try {
                    if (records.isNotEmpty()) client.insertRecords(records)
                    preferences.put(GoogleHealthLongKey.LastSyncedBgAt, now)
                    totalInserted += records.size
                    rxBus.send(EventGoogleHealthNewLog("BG", "Sent ${records.size} new glucose records"))
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "BG insert failed", e)
                    rxBus.send(EventGoogleHealthNewLog("ERR", "BG insert failed (will retry): ${e.message}"))
                    return SyncOutcome.TransientFailure(e.message ?: "BG insert failed")
                }
            }
        }

        if (preferences.get(GoogleHealthBooleanKey.SyncCarbs)) {
            val lastSynced = preferences.get(GoogleHealthLongKey.LastSyncedCarbsAt)
            val from = max(lastSynced, cap)
            val carbsList = persistenceLayer.getCarbsFromTime(from, true).blockingGet()
                .filter { it.timestamp > lastSynced && it.amount > 0.0 }
            if (carbsList.isNotEmpty()) {
                val records = carbsList.mapNotNull { ca ->
                    try {
                        val startInstant = Instant.ofEpochMilli(ca.timestamp)
                        NutritionRecord(
                            startTime = startInstant,
                            startZoneOffset = ZoneOffset.UTC,
                            endTime = startInstant.plusMillis(1),
                            endZoneOffset = ZoneOffset.UTC,
                            metadata = Metadata.manualEntry(clientRecordId = "aaps-carbs-${ca.timestamp}"),
                            totalCarbohydrate = Mass.grams(ca.amount),
                            name = "AAPS",
                        )
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.CORE, "Error converting carbs record", e)
                        null
                    }
                }
                try {
                    if (records.isNotEmpty()) client.insertRecords(records)
                    preferences.put(GoogleHealthLongKey.LastSyncedCarbsAt, now)
                    totalInserted += records.size
                    rxBus.send(EventGoogleHealthNewLog("CARBS", "Sent ${records.size} new carb records"))
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Carbs insert failed", e)
                    rxBus.send(EventGoogleHealthNewLog("ERR", "Carbs insert failed (will retry): ${e.message}"))
                    return SyncOutcome.TransientFailure(e.message ?: "Carbs insert failed")
                }
            }
        }

        if (preferences.get(GoogleHealthBooleanKey.SyncExercise)) {
            val lastSynced = preferences.get(GoogleHealthLongKey.LastSyncedExerciseAt)
            val from = max(lastSynced, cap)
            val exerciseList = persistenceLayer.getTherapyEventDataFromToTime(from, now)
                .blockingGet()
                .filter { it.timestamp > lastSynced && it.type == TE.Type.EXERCISE && it.duration > 0 }
            if (exerciseList.isNotEmpty()) {
                val records = exerciseList.mapNotNull { te ->
                    try {
                        val startInstant = Instant.ofEpochMilli(te.timestamp)
                        val endInstant = Instant.ofEpochMilli(te.timestamp + te.duration)
                        ExerciseSessionRecord(
                            startTime = startInstant,
                            startZoneOffset = ZoneOffset.UTC,
                            endTime = endInstant,
                            endZoneOffset = ZoneOffset.UTC,
                            metadata = Metadata.manualEntry(clientRecordId = "aaps-exercise-${te.timestamp}"),
                            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
                            title = te.note ?: "Exercise",
                        )
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.CORE, "Error converting exercise record", e)
                        null
                    }
                }
                try {
                    if (records.isNotEmpty()) client.insertRecords(records)
                    preferences.put(GoogleHealthLongKey.LastSyncedExerciseAt, now)
                    totalInserted += records.size
                    rxBus.send(EventGoogleHealthNewLog("EX", "Sent ${records.size} new exercise records"))
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Exercise insert failed", e)
                    rxBus.send(EventGoogleHealthNewLog("ERR", "Exercise insert failed (will retry): ${e.message}"))
                    return SyncOutcome.TransientFailure(e.message ?: "Exercise insert failed")
                }
            }
        }

        if (totalInserted == 0)
            rxBus.send(EventGoogleHealthNewLog("OK", "Nothing new to sync"))
        else
            rxBus.send(EventGoogleHealthNewLog("OK", "Sync complete: $totalInserted new records"))
        return SyncOutcome.Success
    }

    fun resetSyncCheckpoints() {
        preferences.put(GoogleHealthLongKey.LastSyncedBgAt, 0L)
        preferences.put(GoogleHealthLongKey.LastSyncedCarbsAt, 0L)
        preferences.put(GoogleHealthLongKey.LastSyncedExerciseAt, 0L)
    }
}
