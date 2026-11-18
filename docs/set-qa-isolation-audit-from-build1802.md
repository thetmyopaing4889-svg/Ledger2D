# Cherry 2D — SET UI isolation and regression fixes (from Build 1802)

## Constraints preserved
- Based on successful Build 1802. Do not touch main, Research, Better Data, formula engine, Luke API/collector, source reference handling, Room schema or winning-number auto-save.
- SET is an **experimental UI-only** snapshot source. Only Luke result_1200 is an authoritative system result. SET never modifies Luke models, history, database, or winning-number flow.
- The morning SET source timestamp (Yangon) determines the eligible last <= 12:01:00 snapshot, not the phone's clock. Phone clock only schedules polling and checks UI freshness.
- Polling interval remains 3 seconds when SET is active. Requests are bounded and never overlap; stop on 401/403/429. Respect the SET website's terms.

## Changes
1. SET reader starts only when the user opens the LIVE tab and the Activity lifecycle is STARTED; stops requesting when the user leaves or the app is backgrounded.
2. Coordinator coroutine sleeps until the next morning window outside 11:59–12:04 instead of waking every 3 seconds. No WorkManager SET task.
3. Debug-only connection test launcher has its own :setprobe process. The Application skips normal DB, Room auto-save and WorkManager startup in that process. The normal debug Cherry launcher remains intact. Release has no probe launcher.
4. Morning result card mirrors a selected UI-only SET result when Luke has not yet confirmed. Its visual override is not a Luke LiveSessionData or persistent result.
5. A bounded private SET evidence file stores distinct snapshots, selected UI result and Luke comparison. A selected UI result is also saved under a separate display-only SharedPreferences key and can be shown after same-day process restart. There is no fallback that invents a cutoff without a recorded source crossing. Debug-only probe has a read-only audit display button.
6. App source and Room/history/auto-save remain Luke-only.

## Validation
- JVM/Robolectric tests for display source priority, stale fallback, selector cutoff and same-day UI persistence / audit.
- CI: unit tests, debug lint/debug APK, release verification.
- On-device morning 11:59–12:05 test remains required for accurate handover, 3s web access, selected source timestamp, and final match with Luke.
- If SET is unavailable or rate-limited, Luke LIVE/final continues; no false SET UI result.
- This is a display experiment, not a verified published SET snapshot cutoff rule.
