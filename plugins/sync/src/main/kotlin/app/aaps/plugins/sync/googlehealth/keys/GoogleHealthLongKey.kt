package app.aaps.plugins.sync.googlehealth.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey

enum class GoogleHealthLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val min: Long = Long.MIN_VALUE,
    override val max: Long = Long.MAX_VALUE,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : LongPreferenceKey {

    LastSyncedBgAt("google_health_last_synced_bg", 0L),
    LastSyncedCarbsAt("google_health_last_synced_carbs", 0L),
    LastSyncedExerciseAt("google_health_last_synced_exercise", 0L),
}
