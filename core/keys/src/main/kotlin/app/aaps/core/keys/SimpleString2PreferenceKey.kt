package app.aaps.core.keys

/**
 * A simple implementation of String2PreferenceKey for static keys.
 */
data class SimpleString2PreferenceKey(
    override val key: String,
    override val defaultValue: String = "",
    override val delimiter: String = "_",
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : String2PreferenceKey
