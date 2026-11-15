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
3. Verify the experimental newest-203-slot adapter's priority-aware Room
   queries and missing-draw/finality rules. It reads at most 256 rows per
   input source and passes at most 203 slots to formula evaluation.
4. Verify the engine-only cursor checkpoint and database-bounded adapter with
   Robolectric and a restart/reload integration test; a successful build of
   this exact branch commit is required.
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

## Engine-only completion candidate (requires CI on this commit)

- Added bounded Room queries to each of four result/tombstone sources, preserving
  existing observers, read/write behavior and full historical archive.
- Added a deterministic 203-slot projection through the existing verified
  winner/daily/history merge, with closed-day/withdrawn-winner handling.
- Added `CherryDiscoveryCheckpoint` and `CherryDiscoveryCursorStore`:
  resume last offered formula, best fit and exact grammar scan position after
  a process restart; reject stale overwrites; discard corrected history only
  for the search cursor, never for locked future trials. The leaderboard is
  reconstructed instead of redundantly persisted.
- Added `CherryDiscoveryEngineService`: explicit search, explicit lock, and
  explicit confirmed-result recording, with bounded IO reads and Default
  coroutine computation. No automatic background searching or Compose work.
- Added Room/Robolectric integration tests for 350-row archive equivalence,
  winner precedence, deleted winner tombstones, current-day cutoff, search
  restart/continuation, stale cursor protection and pre-result lock settlement.
- Search remains a finite 207-shape grammar plus result-derived offsets. Broad
  unconstrained formula search is not certified as useful: adding thousands
  of formulas without independent validation would greatly increase data
  mining/overfitting. Extending the grammar is a separate researched option,
  not proof of a higher future hit rate.
- Before user-facing integration: last CI run must be successful for this
  exact commit; on-phone speed/memory tests and source-finality integration
  remain explicit limits. Local timestamps remain unverified and cannot be
  presented as audited real-time evidence.

## Post-#1633 audit fixes — selection and semantic dedup (CI required)

- Search More now hashes the **whole bounded historical prediction trace plus
  next-session digit**. A formula ID change alone does not make an alternative
  truly new. For WAM, predictions differing by five are behaviorally identical.
  This is evidence-window behavioral equivalence, not a proof that two symbolic
  formulas are algebraically identical for every possible input.
- The search cursor retains ID-to-grammar-position and ID-to-behavior-hash
  maps, bounded at 207*4 theoretical slots. A cheap, versioned local progress
  snapshot preserves these maps across restarts. Semantic duplicates are skipped
  without keeping hundreds of full matched-draw lists in RAM.
- Previously offered non-best formulas may be selected. The exact original
  candidate is reconstructed from its recorded grammar slot and checked
  against the same pre-result history fingerprint and behavior hash before
  any formula is locked. Previously locked trials are still immutable.
- Negative controls: constant-data equivalent-expression names are deduplicated,
  distinct behavior is retained, end-of-grammar reports exhaustion, old
  non-best selections survive process restart, and forged checkpoint rewrites
  are rejected. The newest CI on the exact code commit is the acceptance gate.
- Formula quality caveat remains: finite symbolic search (207 expressions)
  with up to 4 result-derived offsets per expression; no guarantee of future
  prediction advantage. Physical-device latency/RAM testing and independent
  prospective timestamp attestation are not established by desktop CI.

## Better Data Math — isolated UI integration (first implementation, 2026-11-15)

- The existing Better Data Math frame keeps its original selected math pairs,
  Champion status, eight original DOC formulas, and 210 Gemini research variants.
  `CherryDiscoveryPanel` is a NEW separate item after the existing Math frame.
- The UI restores stored search cursors and local locked trials on entry. **It
  never starts discovery or advances a grammar stage on composition or tab
  switching.** Search More happens only after tapping its explicit control.
- The panel allows LONE, WAM and BREAK to be searched separately, shows the
  actual finite scan position (0–828) and grammar stages (0–4), and uses a
  green indeterminate spinner while background search is running. Its cancel
  action cancels the coroutine without mutating prior persisted checkpoints.
- The user may select any previously offered formula and inspect its
  reconstructed expression/offset/predicted category digit. The preview
  service rechecks grammar position, current bounded history fingerprint, and
  semantic behavior hash. User selection and explicit abstention are distinct.
- One confirmation locks all three category choices (including abstentions)
  to the exact future AM/PM target, only before the cutoff. Saved selections
  in existing Better Data are a separate unrelated feature.
- The explicit 'Check confirmed results' button reconciles pending local
  trials from bounded saved Winner/Daily sources through the same precedence,
  post-cutoff and manual-deletion policy as historical analytics. It does not
  search formulas, accept imported history as a final result, or rewrite a
  settled trial. This phase does NOT claim a persistent background result
  listener outside the Discovery panel; explicit button/revisit is supported.
- The existing Analysis, Research Atlas, Rules Library, LIVE, Ledger, Current,
  Born, Summary, Gemini and Champion source files are unchanged. No automatic
  promotion of Discovery results to Champion or Summary, and no use of
  prospective results in operational winner writes.
- Release gating: compile/tests/lint on the exact UI commit; real handset
  screen-width, lifecycle, speed/RAM and actual post-result testing are not
  satisfied by CI. Local system time is not independently attested.

## Better Data Math — Discovery UI correction audit (2026-11-15)

- A newly found Search More candidate becomes the **displayed** formula,
  regardless of any prior user-selected formula. It is **not** automatically
  selected for Lock. The prior explicitly selected formula stays unchanged
  until the user chooses another one or abstains.
- Category request and response are bound to the same LONE/WAM/BREAK kind.
  Category chips are disabled while a request is active; a domain-free
  presentation reducer rejects wrong-category results. It never touches
  the 2D Analysis screens or their separate truncation/new-old tests.
- The green progress bar reports actual inspected positions in the finite
  207*4 grammar cursor (rather than a fake timer or an unchanged checkpoint).
  A conflated progress channel is delivered onto Compose's owning coroutine;
  cancellation preserves the last committed cursor.
- Formula detail verifies the selected candidate by history signature, grammar
  position and behavior hash, and shows actual matched historical 2D pairs,
  rather than only dates. Old imports are labeled **historical**, not claimed
  to be newly verified winner records.
- Lock confirmation first revalidates each chosen formula and then shows its
  exact expression, offset, predicted category digit, historical streak and
  target AM/PM. Explicit abstentions are visible. Save selection and local
  pre-result Lock remain completely separate.
- Add targeted presentation-state tests (new search displayed, old selection
  retained, categories isolated, invalid selection rejected) plus end-to-end
  service tests verifying evidence pairs and monotonic real search progress.
- CI passing is only proof of compile/lint/automated tests on that SHA.
  Device-level screen rendering, cancellation responsiveness, memory and
  measured timing still require physical-device QA before public release.
