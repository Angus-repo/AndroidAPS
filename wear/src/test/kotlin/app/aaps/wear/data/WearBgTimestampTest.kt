package app.aaps.wear.data

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** An older full resend must not overwrite a newer 1-minute glucose push. */
class WearBgTimestampTest {

    @Test fun olderPacketIsRejected() {
        assertThat(shouldAcceptWearBg(currentTimestamp = 10 * MINUTE, incomingTimestamp = 9 * MINUTE)).isFalse()
    }

    @Test fun newPacketIsAccepted() {
        assertThat(shouldAcceptWearBg(currentTimestamp = 9 * MINUTE, incomingTimestamp = 10 * MINUTE)).isTrue()
    }

    @Test fun equalTimestampAllowsCorrection() {
        assertThat(shouldAcceptWearBg(currentTimestamp = 10 * MINUTE, incomingTimestamp = 10 * MINUTE)).isTrue()
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
