package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CherryDiscoveryBackgroundRunnerTest {
    private val days = generateSequence(LocalDate.parse("2026-09-01")) {
        it.plusDays(1)
    }.filter(DrawSchedule::isWeekday).take(81).toList()

    private val history = (0 until 160).map { i ->
        HistoricDraw(days[i / 2], if (i % 2 == 0)
            DrawSession.MORNING else DrawSession.EVENING,
            ((i * 17) % 100).toString().padStart(2, '0'),
            HistoricSource.HISTORY)
    }

    @Test fun asyncSearchHasSameDeterministicAnswerAndCanResume() = runBlocking {
        val target = DrawIdentity(days[80], DrawSession.MORNING)
        val expected = CherryFormulaDiscoveryEngine.nextRound(history, target)
        val actual = CherryDiscoveryBackgroundRunner.nextRound(history, target)
        assertEquals(expected, actual)
        assertEquals(2,
            CherryDiscoveryBackgroundRunner.nextRound(
                history, target, actual).completedStages)
    }

    @Test fun cancelledScopeCannotStartDiscoveryOrResearch() = runBlocking {
        val cancelled = Job().apply { cancel() }
        val target = DrawIdentity(days[80], DrawSession.MORNING)
        try {
            withContext(cancelled) {
                CherryDiscoveryBackgroundRunner.nextRound(history, target)
            }
            fail("Expected discovery cancellation")
        } catch (_: CancellationException) { /* expected */ }
        try {
            withContext(cancelled) {
                CherryDiscoveryBackgroundRunner.research(
                    history, days[79], years = 2, samples = 10)
            }
            fail("Expected research cancellation")
        } catch (_: CancellationException) { /* expected */ }
    }
}
