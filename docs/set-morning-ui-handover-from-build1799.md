# SET Morning UI Handover Test — Build 1799 Baseline

## Isolation
- Luke LIVE polling, result_1200, final side effects, Room/History and auto-save unchanged.
- The SET coordinator provides only an optional in-memory visual overlay for the morning LIVE hero.
- 11:59 Yangon: warm up SET while Luke continues. 12:00 Yangon: show fresh SET LIVE if available, otherwise Luke.
- Poll SET during 11:59–12:04 only while an Activity is visible. Request cadence is bounded to 3s, never overlapping. Stop immediately on 401/403/429 or when source update passes cutoff. Do not circumvent a block.
- Source timestamp, not the phone clock, picks eligible snapshot (last <= 12:01:00 Yangon). Phone time is only used for warm-up, handover and UI freshness.
- If a usable cutoff snapshot is captured and a later source timestamp crosses 12:01:00, show SET 2D as UI Result only. The SET cutoff selector is experimental; no permanent accuracy claim.
- Luke result_1200 immediately supersedes SET display and alone activates any downstream system behavior.
- A disagreement shows a short corrected-result label in the morning hero.
- Debug APK provides both the normal Cherry LIVE launcher and the SET Snapshot Test launcher (separate package from production). Release APK keeps the original launcher.
- Working-day and Luke-confirmed closed-day display suppression remain in place. No SET result in historical/calendar cards.

## Verification
- Offline UI policy tests: handover at 12:00, stale fallback, cutoff result, Luke priority, mismatch, weekends/closed days.
- Android CI tests, lint, debug and release.
- Phone test on trading day: source update timing, SET connection duration, 3s acceptance and matched Luke result.
- SET site Terms and Conditions permit personal non-commercial viewing/download; request rate authorization is not stated explicitly. Treat 3s network cadence as a bounded experimental setting, respect all access restrictions, stop on 401/403/429.
- No SET page requests are made from WorkManager or debug-only one-shot test automatically.
