    @Test fun friday_after_1700_does_not_fallback_to_thursday_when_friday_feed_is_unavailable() = runTest {
        
                val fridayFinal = LiveHeroSnapshot(
                    result = "36",
                    set = "1600",
                    value = "20000",
                    sessionLabel = LIVE_SESSION_MORNING_LABEL,
                    date = friday.toString(),
                )
                val __scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
                val collector = LiveCollector(
                    scope = __scope,
                    fetcher = { null },
                    clock = { LocalTime.of(17, 30) },
                    dateProvider = { friday },
                    historicalFeedFetcher = { null },
                    historicalFinalFetcher = { date ->
                        if (date == friday) fridayFinal else null
                    },
                )
        
                collector.start()
                runCurrent()
        
                val state = collector.state.value as LiveUiState.Data
                assertNull(state.feed)
                assertEquals("36", state.hero?.result)
                assertEquals(friday.toString(), state.hero?.date)
                assertFalse(state.heroLive)
        } finally {
            __scope.cancel()
        }
    }

    @Test fun schedule_aware_history_projection_hides_future_evening_result() {
        val row = historyRow(
            tuesday,
            morning = "44",
            evening = "66",
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        )

        val projected = historyRowToFeed(
            row,
            tuesday,
            LocalTime.of(12, 30),
        )

        assertEquals(tuesday.toString(), projected.date)
        assertEquals("44", projected.morning.result)
        assertTrue(projected.morning.finalized)
        assertEquals("--", projected.evening.result)
        assertFalse(projected.evening.finalized)
        assertEquals("80", projected.modern930)
        assertEquals("33", projected.internet930)
        assertEquals("--", projected.modern200)
        assertEquals("--", projected.internet200)
    }

}
