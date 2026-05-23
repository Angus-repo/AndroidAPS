package app.aaps.plugins.sync.googlehealth

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
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
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleHealthUploader @Inject constructor(
    private val context: Context,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
) {

    suspend fun sync() {
        val availability = HealthConnectClient.getSdkStatus(context)
        if (availability != HealthConnectClient.SDK_AVAILABLE) {
            rxBus.send(EventGoogleHealthNewLog("ERR", "Health Connect not available (status=$availability)"))
            return
        }

        val client = HealthConnectClient.getOrCreate(context)
        val from = dateUtil.now() - T.hours(24).msecs()
        val records = mutableListOf<Record>()

        if (preferences.get(GoogleHealthBooleanKey.SyncBloodGlucose)) {
            val bgList = persistenceLayer.getBgReadingsDataFromTime(from, true).blockingGet()
            for (gv in bgList) {
                try {
                    records += BloodGlucoseRecord(
                        time = Instant.ofEpochMilli(gv.timestamp),
                        zoneOffset = ZoneOffset.UTC,
                        level = BloodGlucose.milligramsPerDeciliter(gv.value),
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
                        mealType = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                        relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                    )
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Error converting BG record", e)
                }
            }
            rxBus.send(EventGoogleHealthNewLog("BG", "Prepared ${bgList.size} glucose records"))
        }

        if (preferences.get(GoogleHealthBooleanKey.SyncCarbs)) {
            val carbsList = persistenceLayer.getCarbsFromTime(from, true).blockingGet()
            for (ca in carbsList) {
                if (ca.amount <= 0.0) continue
                try {
                    val startInstant = Instant.ofEpochMilli(ca.timestamp)
                    records += NutritionRecord(
                        startTime = startInstant,
                        startZoneOffset = ZoneOffset.UTC,
                        endTime = startInstant.plusMillis(1),
                        endZoneOffset = ZoneOffset.UTC,
                        carbohydrates = Mass.grams(ca.amount),
                        name = "AAPS",
                    )
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Error converting carbs record", e)
                }
            }
            rxBus.send(EventGoogleHealthNewLog("CARBS", "Prepared ${carbsList.size} carb records"))
        }

        if (preferences.get(GoogleHealthBooleanKey.SyncExercise)) {
            val exerciseList = persistenceLayer.getTherapyEventDataFromToTime(from, dateUtil.now())
                .blockingGet()
                .filter { it.type == TE.Type.EXERCISE && it.duration > 0 }
            for (te in exerciseList) {
                try {
                    val startInstant = Instant.ofEpochMilli(te.timestamp)
                    val endInstant = Instant.ofEpochMilli(te.timestamp + te.duration)
                    records += ExerciseSessionRecord(
                        startTime = startInstant,
                        startZoneOffset = ZoneOffset.UTC,
                        endTime = endInstant,
                        endZoneOffset = ZoneOffset.UTC,
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
                        title = te.note ?: "Exercise",
                    )
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Error converting exercise record", e)
                }
            }
            rxBus.send(EventGoogleHealthNewLog("EX", "Prepared ${exerciseList.size} exercise records"))
        }

        if (records.isNotEmpty()) {
            try {
                client.insertRecords(records)
                rxBus.send(EventGoogleHealthNewLog("OK", "Inserted ${records.size} records into Health Connect"))
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error inserting Health Connect records", e)
                rxBus.send(EventGoogleHealthNewLog("ERR", "Insert failed: ${e.message}"))
            }
        } else {
            rxBus.send(EventGoogleHealthNewLog("OK", "No records to insert"))
        }
    }
}
