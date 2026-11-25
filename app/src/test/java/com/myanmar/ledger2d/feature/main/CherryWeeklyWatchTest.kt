package com.myanmar.ledger2d.feature.main

import org.junit.Assert.*
import org.junit.Test

class CherryWeeklyWatchTest {
    @Test fun allFiveWatchesAreSeparateFromBornInstances() {
        listOf("s3-week","s3-six","new-groups-weekly-nine",
            "new-groups-weekly-break","new-groups-weekly-wheel").forEach {
            assertTrue(it,isCherryWeeklyWatch(it))
        }
        assertFalse(isCherryWeeklyWatch("new-mob-wheel@2026-11-23-MORNING"))
        assertFalse(isCherryWeeklyWatch("new-day-mon-b4@2026-11-23-MORNING"))
    }

    @Test fun clarifiedReferencesUseActualReaderText() {
        val source="nonempty original source"
        assertTrue(requireNotNull(CherryRuleSources.lookup(source,"s9-wailin")).quote.contains("မနက်ထိပ်စီး"))
        assertTrue(requireNotNull(CherryRuleSources.lookup(source,"new-day-wed-b9@2026-11-25-MORNING")).quote.contains("1456"))
        assertTrue(requireNotNull(CherryRuleSources.lookup(source,"s6-57")).quote.contains("357"))
        assertNull(CherryRuleSources.lookup(source,"unknown"))
    }
}
