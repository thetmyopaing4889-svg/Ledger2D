package com.myanmar.ledger2d.feature.live

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SetLiveProbeFingerprintTest {
    private fun snapshot(update: String, index: String, value: String): SetMarketSnapshot {
        val source = LocalDateTime.parse(update)
            .atZone(ZoneId.of("Asia/Yangon"))
            .withZoneSameInstant(ZoneId.of("Asia/Bangkok"))
        return SetMarketSnapshot(index, value, source, "Open", "60")
    }

    @Test fun threeSecondCadenceRunsSequentiallyAndAccountsForHttpDuration() {
        assertEquals(3_000L, setLiveProbeDelayMs(0L))
        assertEquals(2_100L, setLiveProbeDelayMs(900L))
        assertEquals(300L, setLiveProbeDelayMs(3_000L))
        assertEquals(300L, setLiveProbeDelayMs(6_000L))
    }

    @Test fun identical_snapshot_does_not_count_as_change() {
        val a = snapshot("2026-11-19T14:16:00", "1,461.81", "35,473.96")
        val b = snapshot("2026-11-19T14:16:00", "1,461.81", "35,473.96")
        assertEquals(setProbeSnapshotFingerprint(a), setProbeSnapshotFingerprint(b))
    }

    @Test fun new_source_time_or_changed_index_or_value_counts_as_change() {
        val a = snapshot("2026-11-19T14:16:00", "1,461.81", "35,473.96")
        val later = snapshot("2026-11-19T14:16:15", "1,461.81", "35,473.96")
        val indexChanged = snapshot("2026-11-19T14:16:00", "1,461.82", "35,473.96")
        val valueChanged = snapshot("2026-11-19T14:16:00", "1,461.81", "35,474.96")
        assertNotEquals(setProbeSnapshotFingerprint(a), setProbeSnapshotFingerprint(later))
        assertNotEquals(setProbeSnapshotFingerprint(a), setProbeSnapshotFingerprint(indexChanged))
        assertNotEquals(setProbeSnapshotFingerprint(a), setProbeSnapshotFingerprint(valueChanged))
    }
}
