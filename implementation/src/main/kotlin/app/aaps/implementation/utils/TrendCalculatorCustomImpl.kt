package app.aaps.implementation.utils

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import dagger.Reusable
import javax.inject.Inject

@Reusable
class TrendCalculatorCustomImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences
) : TrendCalculator {

    override fun getTrendArrow(autosensDataStore: AutosensDataStore): TrendArrow? {
        val data = autosensDataStore.getBucketedDataTableCopy() ?: return null
        if (data.isEmpty()) return null
        return calculateDirection(data)
    }

    override fun getTrendDescription(autosensDataStore: AutosensDataStore): String {
        return when (getTrendArrow(autosensDataStore)) {
            TrendArrow.DOUBLE_DOWN     -> rh.gs(app.aaps.core.ui.R.string.a11y_arrow_double_down)
            TrendArrow.SINGLE_DOWN     -> rh.gs(app.aaps.core.ui.R.string.a11y_arrow_single_down)
            TrendArrow.FORTY_FIVE_DOWN -> rh.gs(app.aaps.core.ui.R.string.a11y_arrow_forty_five_down)
            TrendArrow.FLAT            -> rh.gs(app.aaps.core.ui.R.string.a11y_arrow_flat)
            TrendArrow.FORTY_FIVE_UP   -> rh.gs(app.aaps.core.ui.R.string.a11y_arrow_forty_five_up)
            TrendArrow.SINGLE_UP       -> rh.gs(app.aaps.core.ui.R.string.a11y_arrow_single_up)
            TrendArrow.DOUBLE_UP       -> rh.gs(app.aaps.core.ui.R.string.a11y_arrow_double_up)
            TrendArrow.NONE            -> rh.gs(app.aaps.core.ui.R.string.a11y_arrow_none)
            else                       -> rh.gs(app.aaps.core.ui.R.string.a11y_arrow_unknown)
        }
    }

    private fun calculateDirection(readings: MutableList<InMemoryGlucoseValue>): TrendArrow {

        val lookbackMinutes = preferences.get(IntKey.TrendCustomLookbackMinutes).coerceAtLeast(1)

        if (readings.size < 2) return TrendArrow.NONE

        val current = readings[0]
        val lookbackStartTime = current.timestamp - lookbackMinutes * 60 * 1000 - 1000 // 1 second tolerance

        aapsLogger.info(LTag.APS, "trend_custom: current.timestamp: $current.timestamp, lookbackMinutes: $lookbackMinutes")


        val recentReadings = readings.filter { it.timestamp >= lookbackStartTime }
        if (recentReadings.size < 2) return TrendArrow.NONE


        val previousAverage = recentReadings.drop(1).map { it.recalculated }.average()

        aapsLogger.info(LTag.APS, "trend_custom: current.recalculated: $current.recalculated, previousAverage: $previousAverage = recentReadings: ($current.recalculated - $previousAverage)")


        // val slope = if (current.timestamp == lookbackStartTime) 0.0
        // else (current.recalculated - previousAverage) * lookbackMinutes / (current.timestamp - lookbackStartTime)
        val slope = (current.recalculated - previousAverage) / lookbackMinutes

        aapsLogger.info(LTag.APS, "trend_custom: (current.recalculated - previousAverage): (${current.recalculated} - $previousAverage)")
        aapsLogger.info(LTag.APS, "trend_custom: slope: $slope, previousRecalculatedAverage: $previousAverage")

        val slopeByMinute = slope //slope * 60000

        return when {
            slopeByMinute <= -3.5 -> TrendArrow.DOUBLE_DOWN
            slopeByMinute <= -2   -> TrendArrow.SINGLE_DOWN
            slopeByMinute <= -1   -> TrendArrow.FORTY_FIVE_DOWN
            slopeByMinute <= 1    -> TrendArrow.FLAT
            slopeByMinute <= 2    -> TrendArrow.FORTY_FIVE_UP
            slopeByMinute <= 3.5  -> TrendArrow.SINGLE_UP
            slopeByMinute <= 40   -> TrendArrow.DOUBLE_UP
            else                  -> TrendArrow.NONE
        }
    }
}
