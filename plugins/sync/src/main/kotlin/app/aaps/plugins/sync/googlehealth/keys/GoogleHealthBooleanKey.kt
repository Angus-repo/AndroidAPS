package app.aaps.plugins.sync.googlehealth.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey

enum class GoogleHealthBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val calculatedDefaultValue: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    SyncBloodGlucose("google_health_sync_bg", defaultValue = true),
    SyncCarbs("google_health_sync_carbs", defaultValue = true),
    SyncExercise("google_health_sync_exercise", defaultValue = true),
}
