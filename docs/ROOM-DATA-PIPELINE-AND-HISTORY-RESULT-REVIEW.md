# Room Data Pipeline and History Result Review Plan

**Status:** Planning / audit record only — no redesign or implementation approved  
**Repository:** `thetmyopaing4889-svg/Ledger2D`  
**Working branch:** `refactor/daily-flow-refactor-plan`  
**Source revision inspected for this record:** `c81c6aef3aed5d3d313b5364f016b1b3f2cceb4d`  
**Change in this step:** Documentation only. No Kotlin source, database schema, migration, test, UI, or runtime behavior is changed.

## 1. Why this record exists

Ledger2D already has a Room-backed LIVE daily-result data path and a separate Room Keeper background process. The data-intake implementation has been built, but its behavior still needs a focused audit and explicit confirmation against the intended requirements.

Before that audit and confirmation were completed, a design question arose: could the existing History Result data be used to simplify or replace the historical work currently performed by Room Keeper, so that the overall Room data system is easier to maintain and more reliable?

That is a proposal to investigate, not a decision to implement. This record captures the background, the current architecture as observed in source, the reason for the review, and the order in which decisions must be made. It is intended to give future work a reliable shared starting point instead of relying on assumptions or informal conversation.

## 2. Intended purpose of the Room data system

The product goals described for this system are:

1. **Reduce manual work when entering winning numbers.** Winning results received automatically from the LIVE data flow should be available to support the manual workflow, so the user does not have to re-enter information unnecessarily.
2. **Support date-based 2D LIVE information.** LIVE data should be retained in a structured, date-aware form so the app can provide the appropriate information for its date/calendar experience and recover useful data when a live response is not currently available.

These are the intended user outcomes. They must not be confused with proof that every consumer path is already connected. The present review's first stage is deliberately narrower: audit the path that accepts LIVE observations and writes them into the LIVE daily-result Room table. Whether those saved values are consumed by another screen or copied into manual winning-number records is a separate question and must not be silently folded into this first stage.

## 3. Current components and responsibilities found in source

### 3.1 Room database and LIVE daily-result storage

The app's Room database is `ledger2d.db`, currently declared at database version 7 in `LedgerDatabase.kt`. Migration 6 → 7 creates the `live_daily_results` table and a unique index on `date`.

The `LiveDailyResultEntity` row is keyed by app date and contains:

- The 09:30 Modern and Internet reference values.
- The 14:00 Modern and Internet reference values.
- Morning result, SET, and VALUE fields.
- Evening result, SET, and VALUE fields.
- Separate source timestamps for the 09:30 reference group, 14:00 reference group, morning result group, and evening result group.
- An `updatedAt` value.

The unique date index expresses the intended one-row-per-date storage model. `LiveDailyResultPatch` is a partial update: in its declared contract, a null field means “no new value for this field,” not “erase the stored value.”

### 3.2 LIVE data intake and patch creation

`LedgerApplication.onCreate()` starts the app-scoped `LiveCollector` and supplies a saver callback that forwards patches to `container.liveResults.apply(patches)`.

The source path includes:

1. `LiveApi` parses a Luke response into a `LiveFeedData` observation.
2. `LiveCollector` accepts or rejects observations according to the current LIVE policies, then requests Room patches through `buildLiveDailyResultPatches()`.
3. `LiveDailyResultPatchBuilder.kt` validates stored values and decides which date and fields a snapshot may update.
4. `RoomLiveDailyResultRepository.apply()` applies those patches inside a Room transaction.
5. `LiveDailyResultMerger` combines the incoming partial patch with the existing row and upserts a row only when the merged content changes.

Normal LIVE polling and the reference-fetch retry processes can both produce eligible observations. Therefore, this is not simply a single write that must occur once at each wall-clock timestamp: the audit must check how repeated observations, delayed responses, retries, and later corrections affect persistence.

### 3.3 Current schedule and data groups relevant to intake

The schedule policy defines these important boundaries in Yangon time:

| Time | Current schedule role | Data group that must be examined in the intake audit |
|---|---|---|
| 09:30 | Start of the morning reference cycle | Modern / Internet reference values for that day's cycle |
| 11:30 | Morning LIVE polling begins | Current morning LIVE observations and any eligible reference values carried by an accepted snapshot |
| 12:01 | Morning close/finalization boundary | Morning result, SET, and VALUE as they become available or are corrected |
| 12:31 | End of morning catch-up window | Whether late morning results have been captured without regressing valid data |
| 14:00 | Start of the afternoon reference cycle | Modern / Internet afternoon reference values for that day's cycle |
| 16:00 | Evening LIVE polling begins | Current evening LIVE observations and any eligible reference values carried by an accepted snapshot |
| 16:30 | Evening close/finalization boundary | Evening result, SET, and VALUE as they become available or are corrected |
| 17:00 | End of evening catch-up window | Whether late evening results have been captured without regressing valid data |

The time boundaries above describe the current code's schedule roles, not a guarantee that Luke returns a final field at that exact second. The audit must use actual provider fields and timestamps, as well as the app's schedule-time rules, to assess the result saved.

### 3.4 What Room Keeper currently does

`LiveRoomKeeper` is a separate historical-coverage/backfill process. It is not the main LIVE request collector.

From the current source, its core behavior is:

- It considers dates strictly before today; the current day remains owned by the normal LIVE collector.
- It resumes from a checkpoint stored in SharedPreferences, or starts from the previous working day when no checkpoint exists.
- It processes expected historical weekdays through yesterday, skipping weekends and dates marked closed in the app's manual Closed Day repository.
- Its primary historical source is the existing GitHub `2D_history` parser through `HistorySync.fetch2DHistory(date, date)`.
- Its backup source is the date-scoped ThaiStock2D result endpoint. That backup supplies result/SET/VALUE information but does not supply Modern/Internet reference fields.
- It fills fields that are missing or invalid; it is designed not to replace already-valid LIVE fields during a backfill.
- It advances the checkpoint when a date is complete or is not expected to have a result. An incomplete date remains pending rather than allowing the checkpoint to skip the gap.
- `LiveRoomKeeperWorker` is scheduled through WorkManager with a network-connected constraint and a unique work chain. The next run is delayed by 15 minutes after an incomplete/pending result or 6 hours after a completed pass.

The Keeper's responsibility is therefore historical coverage of `live_daily_results`, including recovery of missing fields. Whether that responsibility is still needed in its present form is one of the questions for the later History Result evaluation.

### 3.5 What History Result currently is

`history_results` is a separate Room table, added by migration 5 → 6, with a unique row per date. Its fields are non-null strings for the two daily results, their SET/VALUE values, and the 09:30/14:00 Modern/Internet values. The parser uses placeholder strings such as `-` when source fields are unavailable.

`RoomHistoryResultRepository.sync()` fetches the public historical dataset through `HistorySync`, upserts changed rows, and removes rows older than its configured rolling start date only after a valid source response has been received and merged. Its present range is based on approximately three years of history ending yesterday.

This is not the same storage contract as `live_daily_results`: the History Result table is a source-oriented historical dataset, while the LIVE daily-result table accepts partial updates, tracks source timestamps by data group, and is the destination used by the current Keeper backfill. Reusing History Result in a future design may be possible, but equivalence of coverage, validity, freshness, update behavior, and recovery guarantees must be demonstrated before changing responsibilities.

## 4. Why review the design now?

The intake path and the Keeper were built to make LIVE data more useful and to reduce gaps in the saved daily data. The remaining task is to prove that the intake path behaves correctly under normal and abnormal conditions, not merely that its classes and tables exist.

While preparing for that validation, the existing History Result system was identified as a possible way to simplify the historical-data arrangement. Before choosing that direction, the project must answer two different questions in the correct order:

1. **Does the current LIVE → patch builder → Room repository/merger → database path store the right values for the right date, without losing valid values or accepting stale/invalid ones?**
2. **After the current behavior and gaps are understood, can the existing History Result data and process cover the historical responsibilities currently assigned to Room Keeper without losing any required behavior?**

The second question must not be used as a shortcut around the first. If the intake behavior is not yet confirmed, replacing or removing surrounding components would make it harder to identify the original cause of a data discrepancy.

## 5. Agreed review sequence

### Stage 1 — Audit only the existing Room data-intake path

This is the first and current stage.

Inspect the implementation and existing tests for the path from an accepted Luke snapshot or reference response through patch construction and merge to Room persistence. The review should cover, at minimum:

- Correct date assignment when the provider's result date differs from the app's current cycle date.
- The 09:30 and 14:00 reference eligibility boundaries and rejection of stale-cycle observations.
- Morning and evening result/SET/VALUE capture around their finalization and catch-up windows.
- Validation of 2D values and SET/VALUE metrics before persistence.
- Empty, missing, placeholder, malformed, partial, duplicated, and corrected observations.
- Older, newer, equal, absent, or inconsistent source timestamps, including out-of-order and overlapping save work.
- The rule that a null/pending field must not erase previously valid stored content.
- Current-day Closed Day behavior, weekends, startup/cold-start conditions, and failed or delayed provider responses as they affect the Room patch path.
- The repository transaction and merge behavior, database migration/schema consistency, and the difference between unit-level merger tests and a real Room database round-trip.
- Whether the existing tests actually prove each requirement, and which cases remain untested.

Stage 1 is an investigation and verification task, not a code-fix task. Findings should be written as evidence-based outcomes: confirmed correct, confirmed discrepancy, or not yet proven. If a problem is found, document a reproducible case and the smallest relevant code/test evidence. Do not change code as part of this audit unless a separate change is later explicitly approved.

**Stage 1 completion gate:** deliver an audit report tied to concrete source locations and test evidence, with a clear statement of what passed, failed, or remains unverified. No redesign decision should be inferred from the audit findings alone.

### Stage 2 — Evaluate using History Result instead of the current Room Keeper role

Only after Stage 1 is reported, examine whether the existing History Result implementation can safely replace, reduce, or otherwise change the responsibilities now performed by Room Keeper.

This evaluation must compare the actual responsibilities and data contracts, including:

- Which fields each source provides and which fields can be absent or represented by placeholders.
- Date range, recency, completeness, closed-day handling, and whether today's data is owned by LIVE.
- Source reliability, fetch failures, delays, partial responses, corrections, and the ability to resume when a date remains incomplete.
- Whether historical data needs to be copied into `live_daily_results`, read directly from `history_results`, or served by distinct responsibilities.
- Whether the current LIVE values and source-timestamp protections could be overwritten or bypassed.
- Startup and offline behavior, checkpoint needs, repeated work, storage growth, and migration/backward-compatibility implications.
- Any difference in date/time assumptions between LIVE schedule handling and History Result sync.
- What would happen to data already stored by existing app installations.

The comparison must distinguish between using History Result as a historical source, using it directly as a display/storage source, and replacing the Keeper process. These are different architectural choices; they must not be treated as equivalent without evidence.

### Stage 3 — Write a design/decision document for approval

Record viable options, their consequences, risks, data-preservation requirements, tests, and the recommended approach. Keep observed facts separate from assumptions and recommendations.

Do not implement the proposed design until the user has reviewed and approved this document.

### Stage 4 — Implement only the approved design

Once approved, make the smallest scoped change and prove that it preserves required behavior. Include the required tests, migration/upgrade safety checks if applicable, and the relevant app verification. Avoid unrelated business logic, manual result entry, betting calculations, commissions, payouts, reports, limits, UI, or saved-data behavior.

### Stage 5 — Remove or retire Keeper only after replacement is proven

Removing Room Keeper is a later, separate decision. Do not remove or disable it simply because History Result already exists. First prove that every required responsibility is covered by the approved replacement, that existing data remains safe, and that the app behaves correctly for both existing installations and fresh installs. Then document and test the retirement step separately.

## 6. Guardrails for all stages

Until separately authorized, this record does **not** authorize:

- Changes to Kotlin source, the Room schema or migrations, entities, DAOs, repositories, Room merger, or Keeper scheduling/backfill.
- Deleting, disabling, or bypassing Room Keeper.
- Changing History Result fetching, parsing, date range, or storage behavior.
- Changing manual winning-number entry, LIVE UI/Calendar behavior, manual Closed Day behavior, betting, calculations, commissions, payouts, reports, limits, or any existing saved-data semantics.
- Treating a suspected issue as a confirmed defect without a source-grounded reproduction or test.
- Treating a passing unit test as proof of an end-to-end Room database write unless that test actually exercises Room.

All work must preserve existing data and behavior outside the explicitly approved scope. A finding that appears to require a broader change must be recorded and discussed before implementation.

## 7. Current status at the time of writing

- The Room LIVE data entity/table, DAO, repository, patch builder, merge rules, migration, collector callback, and Room Keeper worker are present in the inspected branch source.
- Focused unit tests exist for parts of patch creation, timestamp-based merging, preservation of valid values, accepted LIVE observations, and Keeper backfill/checkpoint behavior.
- The existence of these components and tests does not, by itself, prove that the complete persistence path is correct in every required situation.
- Stage 1 — Room intake audit — is the first pending task. No audit-completion claim is made by this planning document.
- The idea of using History Result to change or replace Room Keeper remains an unapproved proposal to investigate.
- No source implementation, Room schema, migration, test, or runtime behavior was changed to create this document.

## 8. Completion and hand-off criteria

The next deliverable after this record should be the Stage 1 intake-audit report. It should identify the inspected revision, the exact source and tests reviewed, the verification performed, concrete discrepancies (if any), coverage gaps, and a clear pass/fail/not-proven disposition for each requirement.

Only after that report is complete should the History Result vs Room Keeper evaluation begin. Only after that comparison is documented and approved should implementation planning or code changes proceed.

**Core rule:** understand and verify the existing data path first; compare replacement options second; obtain approval before implementation; preserve all existing behavior and stored data unless an explicit approved plan states otherwise.
