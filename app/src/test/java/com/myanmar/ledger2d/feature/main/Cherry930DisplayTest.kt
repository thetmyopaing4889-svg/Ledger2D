package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test

class Cherry930DisplayTest {
    @Test fun morningAndAfternoonUseTheirOwnReferences() {
        assertEquals(CherrySessionReference("9:30","08","63"),
            cherrySessionReference(DrawSession.MORNING,"08","63","12","34"))
        assertEquals(CherrySessionReference("2:00","12","34"),
            cherrySessionReference(DrawSession.EVENING,"08","63","12","34"))
    }
    @Test fun afternoonNeverFallsBackToMorning() {
        assertEquals(CherrySessionReference("2:00",null,null),
            cherrySessionReference(DrawSession.EVENING,"08","63",null,null))
        assertEquals(CherrySessionReference("2:00","04",null),
            cherrySessionReference(DrawSession.EVENING,"08","63","04","—"))
    }
    @Test fun morningNeverFallsBackToAfternoon() {
        assertEquals(CherrySessionReference("9:30",null,null),
            cherrySessionReference(DrawSession.MORNING,null,"9","12","34"))
    }
    @Test fun dashboardMissingEvidenceIsNotARecordedMiss() {
        assertEquals("—",successDashboardCount(0,0,false))
        assertEquals("0/7 မှန်",successDashboardCount(0,7,false))
        assertEquals("2/14 မှန်",successDashboardCount(2,14,false))
    }
}
