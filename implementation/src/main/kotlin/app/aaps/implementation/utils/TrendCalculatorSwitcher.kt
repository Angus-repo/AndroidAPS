package app.aaps.implementation.utils

import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.Preferences
import dagger.Reusable
import javax.inject.Inject

@Reusable
class TrendCalculatorSwitcher @Inject constructor(
    private val preferences: Preferences,
    private val defaultImpl: TrendCalculatorImpl,
    private val customImpl: TrendCalculatorCustomImpl
) : TrendCalculator {

    private fun active(): TrendCalculator =
        if (preferences.get(BooleanKey.OverviewUseCustomTrendCalculator)) customImpl else defaultImpl

    override fun getTrendArrow(autosensDataStore: AutosensDataStore) =
        active().getTrendArrow(autosensDataStore)

    override fun getTrendDescription(autosensDataStore: AutosensDataStore) =
        active().getTrendDescription(autosensDataStore)
}
