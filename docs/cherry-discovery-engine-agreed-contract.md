# Cherry 2D Formula Discovery — agreed engine-only contract

Updated 2026-11-15. Experimental changes: draft PR #32 based on the
known-green `feature/2d-rules-dual-view` branch. **Do not merge to main or
wire into Better Data UI without a separate user review.**

## Why the engine was started

The pre-existing Math feature includes fixed DOC/Gemini expressions and
Champion/Grade selection. The user reported only Grade C rather than a
satisfactory Champion. The agreed goal is **results-first discovery**:
identify bounded mathematical relations between prior confirmed draws, derive
unchanged expressions and offsets, and search for a contiguous recent fit of
at least **three** completed AM/PM draws, with **ten or more** as the research
target. This is not a claim that ten future draws can be predicted.

Keep LONE, WAM and BREAK as **separate category-specific** searches. A
digit-category match is not an exact two-digit result match.

## User-approved engine behavior

1. Nothing runs until requested. The future UI will live under
   Better Data > Math > existing Gemini/Champion section.
2. A Search More action must advance into **previously unexamined grammar
   and/or previously unoffered formulas**, preserving earlier best fits and
   not returning the same formula ID twice in the same unchanged session.
3. The user may accept an offered formula. A confirmed, pre-result choice is
   immutable for its exact AM/PM target. Never retroactively improve a locked
   choice after the result is known.
4. Compare an approved locked output with the **confirmed** result after the
   Yangon cutoff. Store hit/miss/abstention explicitly. Do not interpret a
   retroactively found streak as verified prospective accuracy.
5. The current DOC/Gemini engines, Rules Library, Research Atlas, and app UI
   must remain unchanged throughout this engine-only development phase.
6. Search must remain finite, cancellable, memory-bounded and off Main;
   don't mistake unlimited formula fitting for useful discovery.

## Bounded history and performance contract

- **Normal discovery:** evaluate at most 200 newest completed slots, with
  three additional previous slots only for expression context (203 total).
- **Research audit:** by default sample 80 target slots across the preceding
  two years; optionally three years. Each target uses **only pre-target data**
  and a maximum 203-slot context. Archive history remains untouched.
- Normalizer uses O(203) extra buffering, but still scans O(N) supplied input.
  Integrating a bounded, precedence-correct database query is **pending**.
- Short-form candidate state stores only lightweight formula IDs, one current
  candidate and the best historical fit. The new alternative scanner is
  position-based over four slots per expression and retains no giant list.
- Cancellations must leave the prior immutable cursor unmodified.

## Implemented experimental components, not yet certified as complete

- `CherryFormulaDiscoveryEngine.nextRound`: four finite grammar stages,
  207 expression shapes, original 24-slot representative leaderboard.
- `nextAlternative` and `CherryDiscoverySearchMore`: scan each viable
  expression/offset candidate lazily, never repeatedly offer an ID, and
  preserve a bounded-memory position and best fit.
- `CherryDiscoveryBackgroundRunner.searchMore`: Dispatchers.Default
  with cooperative job cancellation.
- `CherryDiscoveryWalkForward`: bounded retrospective as-of/held-out
  checks. Research sampling is adjusted to include the newest target.
- `CherryProspectiveTrialJournal`: immutable per-target local trial
  snapshots, category-specific outcomes and a selection entry point for
  currently offered Search More alternatives.
- `CherryProspectiveTrialStore`: versioned **local** snapshots that reject
  replacement/deletion of previous locks and outcomes; this is NOT a trusted
  timestamp nor tamper-proof server audit. Explicit persistence is still
  required from a future integration caller.
- Targeted tests: 10,000-record archive stability, source-history changes,
  old/active holiday corrections, cursor nonrepeat/cancellation, trial
  persistence, and pre-result lock invariants.

## Explicit remaining acceptance gates

1. Latest draft PR CI must pass tests, lint and APK compilation. Passing a
   previous SHA does not validate later commits.
2. Confirm all 207 shapes, candidate enumeration order, offset uniqueness,
   stage progress and search exhaustion with adversarial fixtures and
   non-constant historical sequences. Fix any regression.
3. Finish the newest-203-slot repository read path with priority-aware
   resolution and missing-draw validation, without deleting archive data.
4. Confirm state persistence and honest error recovery for UI-free search
   sessions, not just the local prospective trial journal.
5. Profile performance and cancellation on a real Android handset,
   including repeated search, rotation/background and RAM pressure.
6. Compare held-out and genuinely locked future trials with category-specific
   chance baselines and existing formulas. A consistent predictive advantage
   has **not** been demonstrated in cross-year historical audits.
7. Only after engine review/approval, integrate the previously agreed
   user-triggered UI. No automatic Champion replacement and no main merge.

## User-facing status discipline

Distinguish: (a) code committed, (b) passing CI on **that exact commit**,
(c) real-device verified, (d) empirically validated future outcomes. Never
call the engine complete because GitHub produced another successful build.
