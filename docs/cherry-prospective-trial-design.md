# Cherry 2D — Prospective Trial, Phase 1 (Domain Only)

Date: 2026-11-15. Branch: feature/2d-rules-dual-view.

## Why this exists

The formula discovery prototype finds consecutive **retrospective** digit-category fits. In real public 2D history, ten retrospective WAM matches were sometimes found even after dates were shuffled, so those fits are not proof of future predictive usefulness. Treat fitted streaks, historical walk-forward results, and genuinely time-stamped future tests as **three different types of evidence**.

## Implemented, experimental domain contract

- New isolated pure Kotlin file: CherryProspectiveTrial.kt. No UI, Room migration, network activity, background task, or change to DOC 8 / Gemini 210 / production formula selection.
- Freeze an entire **target AM/PM session** before the draw's Yangon session cutoff:
  - AM: 12:00 Asia/Yangon (cutoff before 12:00).
  - PM: 16:30 Asia/Yangon (cutoff before 16:30).
- Lock **all three independent categories together** (LONE, WAM, BREAK), including **explicit abstentions** where no suitable formula was selected.
- Accept only an ID already in the evaluated discovery session, and record its exact digit output, original formula ID, historical-streak count, grammar stage, completed-stage count, and a SHA-256 fingerprint of the discovery input history.
- For each session, allow a **single immutable lock**, never a replacement or second pick after seeing the outcome.
- Accept outcome evaluation only **after** the session cutoff, for an existing locked target and a valid two-digit confirmed WINNER or completed DAILY result. Reject unverified HISTORY fallback, invalid outcome, wrong date/session, and a second evaluation.
- Record hit/miss/abstention by **category**, never claim exact pair accuracy. Pending entries are not counted as misses; abstentions are neither hit nor miss.
- Journal functions return new instances without mutating their earlier snapshots.

## Crucial limitation: not independently provable YET

The phase-one API accepts a **caller-supplied local Instant** for unit-testability. A phone clock can be changed or a record reconstructed afterward. SHA-256 of the history input prevents accidental confusion about which input was used, **but does not notarize the timestamp**. The current in-memory journal also does not persist across app restarts.

**Do not label any phase-one record as an independently verified real-time pre-result lock.** Its evidence label is deliberately `LOCAL_PRE_RESULT_LOCK_UNVERIFIED`. This is a prototype, not a working server-verified betting tool.

For a genuine prospective study the later implementation needs: durable append-only storage, trusted time attestations (for example a remote timestamp/transaction log), validated source/finality of results, and reproducible unique session identities. Use a *new* future set of targets, not backdated or historical replay outputs, for the prospective evidence count.

## Required testing before app integration

1. GitHub Kotlin unit tests: cutoff enforcement and Yangon zone, all-category atomic lock, default and explicit selections, abstentions, non-overwrite, confirmed source, held-out validation, immutable snapshots.
2. Fuzz/negative-control and category-baseline tests for the formula finder remain separate; a local lock alone cannot erase historical selection bias.
3. On-device coroutine cancellation, memory and latency measurements still required.
4. A user-friendly discovery screen may eventually display an exploratory historical fit **without** representing it as an official prediction.

## Comparison currently established

Kotlin GitHub CI #1552 passed the shuffled-date negative control: one of six shuffled histories found a WAM retrospective ten-streak. This is only a small qualitative negative control, not a significance test. CI #1566 passed the source-precedence and exhaustive 00–99 baseline tests (as of the creation of this design note).

Nothing here proves a stable edge beyond chance or improves the legal/financial risk of buying 2D outcomes.
