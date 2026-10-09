# 2D LIVE Daily Flow Refactor Plan

**Status:** Pre-implementation design record  
**Scope:** 2D LIVE Daily Flow responsibility separation only  
**Baseline:** commit \`62b26751d8c0fa72bb5db42fc08e3d985ec83a1e\` (#492)  
**Working branch:** \`refactor/daily-flow-refactor-plan\`  
**Implementation status at the time this record is written:** Not started  
**Rule:** Do not implement on \`main\`. Keep all refactor work on the dedicated branch created from the exact baseline above.

## 1. Purpose

The existing Daily Flow behavior is not being replaced with a new business design. The purpose is to reorganize responsibilities that are currently concentrated and intertwined in \`LiveCollector.kt\` and partly co-located with the UI/API in \`LiveScreen.kt\`.

The target is to make the LIVE schedule, independent request lifecycles, shared state transitions, and display projection separately understandable and testable. A slow operation or a change to one responsibility must not unnecessarily block or mutate an unrelated responsibility.

This is a responsibility-boundary refactor, not a feature rewrite. Existing observable behavior is the compatibility contract unless a test proves the current code differs from the recorded requirements and that discrepancy is separately reviewed before any correction.

## 2. Exact source baseline and repository guardrails

Use only commit \`62b26751d8c0fa72bb5db42fc08e3d985ec83a1e\` as the starting point. Do not substitute the current \`main\` branch or older aspirational architecture documents for this source.

The CI run associated with this baseline, [run 37766264728](https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/37766264728), completed successfully. Its build job reports success for unit tests/lint/debug APK and release APK verification. This is historical baseline evidence, not proof that a future refactor passes.

### Strict boundaries

- Do not make changes on \`main\`.
- Do not change betting, parser, calculations, commission, payout, reports, limits, closed-number behavior, saved-data semantics, or unrelated navigation/UI.
- Do not change the Room database schema, Room repositories, \`LiveDailyResultMerger\`, \`LiveRoomKeeper\`, or \`LiveRoomKeeperWorker\`.
- Do not redesign History Result, Calendar, historical recovery sources, or historical backfill.
- Do not add a second LIVE provider or restore an aspirational multi-source design. The actual baseline app-scoped collector uses Luke as its LIVE API and configures \`secondaryFetcher = null\`.
- Do not modify API endpoints, API response mapping, timeout behavior, persistent SharedPreferences keys/payload shape, Room patch semantics, or the public contract consumed by \`LiveScreen\`, except for compile-required import/type moves that preserve behavior.
- Keep the current callbacks that connect Daily Flow to caches, current-day Closed Day hold-feed retrieval, historical recovery, and Room persistence as existing integration boundaries. These are not permission to refactor the external subsystems.

## 3. Current structure observed in the baseline

### \`LiveCollector.kt\`

The file is approximately 2,269 lines and currently contains several distinct responsibilities:

1. Yangon date/time rules and schedule helpers such as \`dailyCycleDate()\`, \`liveSessionForTime()\`, \`liveWindowAction()\`, and \`shouldPoll()\`.
2. App-scoped scheduler startup and the recurring scheduler loop.
3. Normal Luke LIVE requests, request sequence tracking, provider-time ordering, and late-response rejection.
4. Independent 09:30 and 14:00 reference retry cycles.
5. Validation and merge/protection rules for LIVE, reference pairs, and finalized session values.
6. Current-day Closed Day detection/hold handling in the LIVE display flow.
7. Cached-feed restoration and startup fallback/recovery orchestration.
8. Display resolution, Pending projection, hero selection, and publication through \`LiveUiState\`.
9. Cache writes and integration with the existing Room patch callback.

The risk is not that every rule is wrong. It is that the same class owns too many decisions and state mutations, making it hard to change one responsibility without affecting another.

### \`LiveScreen.kt\`

This file contains the Compose LIVE UI and Calendar UI, data models (\`LiveFeedData\`, \`LiveSessionData\`, \`LiveUiState\`), and the Luke API client. The screen also calls \`collector.fetchCycle()\` on entry and uses it for manual retry.

A future extraction may move API/data-model declarations to focused files, but it must preserve the screen API and visible behavior. Do not silently remove the screen-entry fetch or retry path during a structural refactor.

### App and existing integration boundaries

- \`LedgerApplication.onCreate()\` starts the app-scoped \`LiveCollector\` and separately enqueues \`LiveRoomKeeperScheduler\`.
- \`LiveScreen\` observes the collector's StateFlow.
- \`LiveCollector\` invokes the existing cache callbacks, current-day Closed Day held-feed callback, historical recovery callbacks, and Room patch saver.
- \`LiveRoomKeeper\` is a separate historical backfill worker and must remain unchanged.
- \`LiveDailyResultPatch\` and \`LiveDailyResultMerger\` have persistence semantics of their own. This refactor must not redesign them.

## 4. Daily Flow behavior that must remain unchanged

All schedule interpretation uses the Asia/Yangon app clock, not the provider timestamp. Provider timestamps are used to assess response ordering and whether a response belongs to the intended reference cycle.

| Yangon time / condition | Required existing behavior |
|---|---|
| Before 09:30 on a working day | Display the previous working-day hold as currently resolved by the baseline; do not begin the new day's 09:30 cycle early. |
| 09:30 | Begin the current working-day reference cycle. The old reference values are masked as Pending until the current-cycle pair is accepted. |
| 11:30 | Begin the morning LIVE session. If 09:30 reference has not succeeded, the session-card reset is forced once at this boundary. |
| 11:30–12:01 | Normal LIVE polling continues at a 3-second cadence. |
| 12:01–12:31 | Morning finalization/catch-up remains the same morning session owner; poll only while the morning final is not yet available. |
| 14:00 | Begin the current working-day afternoon reference cycle independently from the morning reference lifecycle. |
| 16:00–16:30 | Evening LIVE polling continues at a 3-second cadence. |
| 16:30–17:00 | Evening finalization/catch-up continues only while the evening final is not available. |
| Outside LIVE polling windows | Do not start normal LIVE polling. Current reference catch-up behavior remains governed by the existing independent retry windows. |
| Reference retry | Retry cadence remains 60 seconds while that cycle's existing retry window is open. A failed/stale dedicated response must not erase a valid reference already accepted from another valid Luke observation. |
| Saturday/Sunday | Hold Friday's state; no LIVE polling or reference retries. Monday before 09:30 still holds Friday; Monday at 09:30 starts Monday's cycle. |
| Luke-confirmed Closed Day | Preserve the existing LIVE-only hold of the previous working-day display, stop current-cycle polling/retries as the baseline does, and resume at the next working-day 09:30 boundary. Do not conflate this with the app's manual betting/report Closed Day system. |

The policies \`liveSessionForTime()\`, \`liveWindowAction()\`, \`shouldPoll()\`, and display phase resolution are intentionally not identical. Do not collapse them into one simplistic phase enum. In particular, the session owner spans the applicable finalization/catch-up window even when the coarse display action changes.

### Existing concurrency and data-integrity rules

- A slow LIVE request must not block the next scheduled request.
- LIVE, 09:30-reference, and 14:00-reference work have independent lifecycles even though they use the same Luke fetcher.
- Network waits must remain outside the shared-state critical section.
- Requests may overlap. Do not add a global “only one request in flight” guard.
- Provider timestamp orders responses when both timestamps are available. Request sequence is a tie-breaker for equal/unavailable provider time according to the baseline rules.
- An older or invalid response cannot replace a newer accepted state.
- A final session value cannot regress to Pending, and an active session's valid LIVE value cannot be erased by an incoming partial/Pending snapshot.
- A normal accepted Luke snapshot may contain valid current-cycle reference values and may promote those values even if the dedicated reference request is still pending/failed.
- Successful 09:30 reference may reset session cards once. If it fails until 11:30, reset once at 11:30; a late 09:30 success must fill the reference without repeating the reset.
- When an active LIVE session has no valid current-session feed, the display must not flash a previous-day or wrong-session hero as today's LIVE value.
- Failures return no new feed and must not erase the last valid snapshot.

## 5. Target responsibility boundaries

These are proposed internal boundaries, not a request to rewrite the app into a different architecture framework.

### A. Schedule policy

Candidate home: \`LiveSchedulePolicy.kt\`

Own only deterministic date/time decisions: cycle date, session ownership, coarse window action, polling eligibility, and the open/closed retry-window decisions. Inputs should be explicit dates/times and relevant state; policy functions must not perform I/O or mutate collector state.

Keep the existing distinct schedule decisions separate. Preserve their behavior through direct unit tests before changing their call sites.

### B. API and feed models

Candidate homes: \`LiveApi.kt\`, \`LiveFeedData.kt\`, \`LiveUiState.kt\`

Keep Luke endpoint access, HTTP parsing, data models, and Compose UI responsibilities clearly separated. Preserve parsing defaults, timeout values, provider-time conversion, source tags, and close-day observation mapping. Keep model changes mechanical; do not alter the JSON contract.

No secondary provider should be wired in as part of this refactor.

### C. Request lifecycles

Candidate home: \`LiveRequestCoordinator.kt\` or focused internal LIVE/reference workers

- LIVE polling owns its cadence and each request execution.
- The morning reference worker owns its independent retry job/date.
- The afternoon reference worker owns its independent retry job/date.
- Workers obtain data and submit typed results/events; they do not directly mutate unrelated shared state.
- Lifecycle cancellation and date/session scoping must preserve the existing retry windows and not block another worker.

Initially preserve the existing external entry points, including scheduler-start and screen-triggered fetch, to avoid changing behavior accidentally.

### D. Canonical shared-state transitions

Candidate home: \`LiveStateReducer.kt\` or an equivalently focused internal state owner

Create one clearly defined path for applying accepted LIVE/reference/Closed Day events to shared Daily Flow state. Carry over the baseline's validation, provider timestamp/sequence ordering, active-LIVE protection, final-session protection, reference promotion, pending/reset dates, and held-final logic.

A reducer must not hold a lock while waiting for network/history I/O. Preserve serial state commits while allowing network work to overlap. Do not migrate all state at once: move one class of transition at a time and test it.

### E. Display projection

Candidate home: \`LiveDisplayProjector.kt\`

Use the current \`resolveLiveState()\` logic as the behavioral source. Make display projection consume an explicit immutable snapshot of the necessary current state and return a resolution. It must not write the cache, Room, \`lastLive\`, \`lastFinal\`, request counters, or retry dates.

The transition that remembers a newly resolved hero/final should happen explicitly in the state owner, not as a hidden side effect of projection. Preserve Pending display-only feeds and ensure they never replace canonical raw feed/cache/history state.

### F. Side-effect adapters and orchestrator

Keep \`LiveCollector\` as a narrow app-scoped orchestrator and StateFlow publisher after migration. Existing cache, Closed Day, history/recovery, and Room-saver callbacks remain boundary adapters. Persistence should be triggered after an accepted state transition; slow/suspending callbacks must not run inside the state lock.

Do not redesign the adapters or their external subsystems as part of this work.

## 6. Suggested migration order

Each phase should be a separate small commit on the working branch. Do not combine every change into one large rewrite.

1. **Baseline and inventory.** Confirm the branch points to the exact baseline; capture current test/build results and enumerate all Daily Flow tests relevant to the proposed move.
2. **Extract deterministic schedule policy.** Move policy helpers without changing expressions or edge cases. Run schedule/Fresh Install/weekend/Closed Day/finalization-boundary tests.
3. **Separate API and models.** Move Luke API and data model declarations to focused files, preserve public/internal visibility and all screen call sites. Run compile, tests, lint, and build.
4. **Introduce request-result events while preserving request lifecycle.** Keep overlapping requests and separate retry jobs. Add/adjust tests to prove a slow request does not block a later one and that the 09:30/14:00 jobs remain independent.
5. **Centralize state commits in small slices.** First handle normal LIVE responses and ordering, then dedicated reference results/reference promotion, then Closed Day, then startup/recovery integration. After each slice, run focused tests before continuing.
6. **Separate display projection and persistence effects.** Make display resolution side-effect-free while preserving hero/last-final behavior, cache format, and integration callbacks. Do not alter Room patch generation/merge semantics.
7. **Simplify orchestration only after parity is proven.** Keep scheduler lifecycle and current entry points compatible; remove duplication only if a test-backed behavior-preserving change is proven.
8. **Final audit and CI.** Review the full diff against the baseline; run all unit tests, lint, Debug APK build and Release APK verification; inspect generated CI logs and ensure non-scope files/subsystems were not changed.

If any phase fails a baseline test or build, stop at that phase and resolve the cause before moving on. Do not hide or skip an existing failing test to make the refactor look green.

## 7. Required regression coverage

At minimum preserve and pass the existing tests covering:

- \`daily_cycle_date_switches_at_0930_and_skips_weekend\`
- Fresh-install starts before 09:30, after 09:30, during afternoon reference, and after the evening final window
- Successful 09:30 reference reset, failed reference reset at 11:30, and late reference success without a second reset
- Weekend hold and no weekend requests
- Friday post-final stop behavior and Monday reference/hero hold
- Luke-confirmed Closed Day hold/notice/expiration
- Morning/evening finalization windows, session ownership, and no wrong-session/previous-day hero flash
- Reference stale-cycle rejection, independent retries, promotion from normal Luke observations, and no regression from a failed dedicated retry
- \`request_failure_keeps_last_successful_snapshot\`
- \`slow_request_does_not_block_next_scheduled_request\`
- \`late_older_response_cannot_overwrite_newer_response\`
- \`final_result_cannot_regress_to_pending\`
- \`background_polling_works_without_opening_live_screen\`
- \`newer_luke_result_wins_even_when_its_request_started_first\`

Also verify integration compatibility: \`LedgerApplication\` still starts the collector and Keeper separately; \`LiveScreen\` still observes the same state contract and renders its Live and Calendar views; cache load/save compatibility remains; the existing Room saver continues to receive the same patch semantics; Room Keeper files/schema/repository remain unchanged.

## 8. Out-of-scope change policy

Any diff that changes Room schema/entities/DAOs, \`LiveDailyResultMerger\`, Keeper scheduling/backfill, History Result/Calendar rules, betting or financial calculations, manual Closed Day behavior, or unrelated UI is an automatic review stop unless a specific, separate scope change is approved first.

Moving a type to another file may require imports or visibility adjustments. Such edits must be strictly mechanical and must not change the type's fields, equality behavior, persistence representation, schedule decisions, or UI semantics.

## 9. Completion criteria

The refactor is considered ready only when all conditions below are true:

1. The branch descends from the exact #492 SHA and \`main\` has not been changed.
2. The responsibility boundaries are reflected in the actual code, not only in filenames.
3. Existing Daily Flow timing, retries, response ordering, holds, finals, and visible state behavior pass regression tests.
4. The UI, Calendar, caches, Room patch integration, and Room Keeper remain compatible and out-of-scope subsystems are unchanged.
5. All required CI checks pass on the refactored branch and their results are inspected.
6. The final diff has been reviewed for unintended changes before proposing a merge.

**Decision record:** preserve behavior first; improve separation second; no unrelated subsystem rewrites; no implementation on \`main\`.

## 10. Implementation progress checkpoint — 2026-10-09

This section records work completed after the design record above; it does not change the original baseline, scope, or acceptance criteria.

### Extracted boundaries on the working branch

- Deterministic date/time policies, reference retry-window eligibility, and the LIVE-only Closed Day hold-date rule are in `LiveSchedulePolicy.kt`, with boundary tests for polling, Friday post-final retry cutoff, next-working-day retry, Closed Day suppression, and the Monday 09:30 hold expiry.
- LIVE feed/UI models and Luke client have been moved to `LiveModels.kt` and `LiveApi.kt` without changing the screen entry/retry contract.
- Display resolution is in `LiveDisplayProjector.kt`; UI-state mapping, reference/Pending-card overlay, and synthetic Pending-feed construction are pure projection helpers.
- Normal LIVE request completion and dedicated reference completion carry typed events into the existing serialized state-acceptance methods: `LiveRequestResultEvent.kt` and `LiveReferenceResultEvent.kt`.
- `LiveCollector` still owns scheduling, shared mutable state, response acceptance, cache/Room integrations and their existing callbacks. `publishLocked` now names display resolution, remembered hero/final effects, and state publication as separate steps.
- Added targeted tests for polling boundaries and display projection, including Closed Day hold, 09:30/reference reset, 11:30 session reset, evening-session reset, and synthetic Pending feed behavior.

### Verification and current readiness

- Last confirmed successful full CI run is build #500 on the prior UI-state mapping extraction: [run 37915036515](https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/37915036515).
- Later projection extractions and their new regression tests are present on this branch, but their CI result has not yet been verified in this checkpoint. Do not treat the branch as merge-ready until a full CI pass is observed and the final diff is reviewed.
- The draft review is [PR #27](https://github.com/thetmyopaing4889-svg/Ledger2D/pull/27), targeting `fix/403-live-integration-root-cause-20261007`, the branch whose tip is the exact #492 baseline. This is deliberate: current `main` has diverged from that baseline. Keep the PR draft and do not merge it as part of the refactor work.
- `.github/workflows/android.yml` has temporary trigger-only edits on this working branch to attempt branch CI. Restore those workflow trigger edits before final handoff, unless a separate approved CI design replaces them. The workflow on `main` and the base branch has not been changed by this refactor.

### Current remaining work

1. Obtain and inspect CI for the latest source changes; fix any actual failures before proceeding.
2. Continue only low-risk extractions with tests and preserve the exact request/retry/state/cache/Room callback ordering.
3. Review the complete PR diff against #492 and confirm no excluded subsystems are touched.
4. Revert temporary workflow trigger edits on this branch, verify the final diff/base, and keep the PR unmerged until all completion criteria in section 9 are met.
