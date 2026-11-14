package app.aaps.ui.compose.overview

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Overview BG is display-only. These tests ensure 1-minute sensor samples appear immediately
 * without changing the separate 5-minute bucket supplied to the loop.
 */
class OverviewLiveBgTest {

    @Test
    fun newestRawReadingWinsOverFiveMinuteBucket() {
        val bucket = InMemoryGlucoseValue(timestamp = NOW - 5 * MINUTE, value = 166.0)
        val raw = gv(value = 162.0, timestamp = NOW)

        val shown = overviewDisplayBg(raw, bucket)

        assertEquals(162.0, shown?.value)
        assertEquals(NOW, shown?.timestamp)
        assertEquals(166.0, bucket.value) // The loop's bucket is not modified.
        assertEquals(NOW - 5 * MINUTE, bucket.timestamp)
    }

    @Test
    fun newReadingReplacesThePreviousMinuteWithoutAChangedBucket() {
        val bucket = InMemoryGlucoseValue(timestamp = NOW - 5 * MINUTE, value = 166.0)

        assertEquals(160.0, overviewDisplayBg(gv(value = 160.0, timestamp = NOW - MINUTE), bucket)?.value)
        assertEquals(162.0, overviewDisplayBg(gv(value = 162.0, timestamp = NOW), bucket)?.value)
    }

    @Test
    fun noValidRawReadingFallsBackToBucket() {
        val bucket = InMemoryGlucoseValue(timestamp = NOW - 5 * MINUTE, value = 166.0)

        assertEquals(bucket, overviewDisplayBg(null, bucket))
        assertEquals(bucket, overviewDisplayBg(gv(200.0).copy(isValid = false), bucket))
        assertNull(overviewDisplayBg(null, null))
    }

    @Test
    fun deltaUsesRawSampleNearFiveMinutesAgoNotLoopBucket() {
        val current = gv(162.0)
        val earlier = listOf(
            gv(170.0, timestamp = NOW - 5 * MINUTE),
            gv(173.0, timestamp = NOW - 6 * MINUTE),
            gv(158.0, timestamp = NOW - 4 * MINUTE)
        )

        assertEquals(-8.0, rawOverviewDelta(current, earlier))
    }

    @Test
    fun deltaIgnoresOtherSensorAndInvalidReadings() {
        val current = gv(162.0)
        val earlier = listOf(
            gv(120.0, timestamp = NOW - 5 * MINUTE, source = SourceSensor.LIBRE_3),
            gv(130.0, timestamp = NOW - 5 * MINUTE).copy(isValid = false),
            gv(164.0, timestamp = NOW - 5 * MINUTE + 15_000L)
        )

        assertEquals(-2.0, rawOverviewDelta(current, earlier))
    }

    @Test
    fun deltaIsUnavailableWhenFiveMinuteHistoryIsMissing() {
        val current = gv(162.0)

        assertNull(rawOverviewDelta(current, emptyList()))
        assertNull(rawOverviewDelta(current, listOf(gv(150.0, timestamp = NOW - 2 * MINUTE))))
        assertNull(rawOverviewDelta(current, listOf(gv(151.0, timestamp = NOW - 7 * MINUTE))))
    }

    private fun gv(
        value: Double,
        timestamp: Long = NOW,
        source: SourceSensor = SourceSensor.SYAI_TAG
    ) = GV(
        timestamp = timestamp,
        raw = null,
        value = value,
        trendArrow = TrendArrow.FLAT,
        noise = null,
        sourceSensor = source
    )

    private companion object {
        const val MINUTE = 60_000L
        const val NOW = 1_700_000_000_000L
    }
}
