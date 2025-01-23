package app.aaps.plugins.automation.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class AutomationStringKey(
    override val key: String,
    override val defaultValue: String,
) : StringNonPreferenceKey {

    AutomationEvents("AUTOMATION_EVENTS", ""),
}
