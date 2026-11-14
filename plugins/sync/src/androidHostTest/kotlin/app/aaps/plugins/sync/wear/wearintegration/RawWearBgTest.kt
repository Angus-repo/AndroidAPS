package app.aaps.plugins.sync.wear.wearintegration

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Display-only 5-minute delta accompanying the new 1-minute watch glucose reading. */
class RawWearBgTest {

    @Test fun latestMinuteUsesSensorHistoryNotLastBucket() {
        val current = gv(162.0, NOW)
        val past = listOf(
            gv(166.0, NOW - 5 * MINUTE),
            gv(160.0, NOW - 4 * MINUTE),
            gv(168.0, NOW - 6 * MINUTE)
        )
        assertThat(rawWearDelta(current, past)).isEqualTo(-4.0)
    }

    @Test fun deltaIgnoresOtherSensorInvalidAndFutureReadings() {
        val past = listOf(
            gv(130.0, NOW - 5 * MINUTE, SourceSensor.LIBRE_3),
            gv(131.0, NOW - 5 * MINUTE).copy(isValid = false),
            gv(160.0, NOW + MINUTE),
            gv(164.0, NOW - 5 * MINUTE + 10_000L)
        )
        assertThat(rawWearDelta(gv(162.0, NOW), past)).isEqualTo(-2.0)
    }

    @Test fun noHistoricalGlucoseProducesNoFabricatedDelta() {
        val current = gv(162.0, NOW)
        assertThat(rawWearDelta(current, emptyList())).isNull()
        assertThat(rawWearDelta(current, listOf(gv(150.0, NOW - 2 * MINUTE)))).isNull()
        assertThat(rawWearDelta(current, listOf(gv(150.0, NOW - 7 * MINUTE)))).isNull()
    }

    private fun gv(value: Double, timestamp: Long, sensor: SourceSensor = SourceSensor.SYAI_TAG) =
        GV(timestamp = timestamp, raw = null, value = value, trendArrow = TrendArrow.FLAT, noise = null, sourceSensor = sensor)

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
    }
}
