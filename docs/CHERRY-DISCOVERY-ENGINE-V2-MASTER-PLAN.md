# Cherry 2D — Hybrid Symbolic Discovery Engine V2 Master Plan

**Date:** 2026-11-15  
**Status:** User-directed master plan, recorded for implementation; detailed UI/Champion integration rules still require explicit user review. **This document is not a claim that V2 code is implemented.**  
**Repository:** `thetmyopaing4889-svg/Ledger2D`  
**Last passing baseline:** Android CI **#1641**, commit `d9fdb5837aa7af72835aaa6b7bf9072669697eb7`, on `fix/discovery-sampling-and-10k-audit-20261115`. Unit tests, lint and debug APK passed on this exact SHA; real-device performance is not certified.  
**V2 working branch:** `feature/cherry-discovery-v2-from-build1641`, branched from the exact passing commit above. Do not silently substitute older `main` or another branch.  
**Reference:** [CI #1641](https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/43708608913) · [Draft PR #32](https://github.com/thetmyopaing4889-svg/Ledger2D/pull/32)

## 1. Product intent and non-negotiables

User intent: **time-bounded batch formula discovery**, not one-formula-per-tap discovery. A user chooses **LONE / WAM / BREAK**, taps Search, observes a real time bar while the engine searches, then receives a **collection of first-found formulas at distinct historical contiguous-streak lengths** (3, 4, 8, 10, etc.). Show the highest historical streak as **Recommended**, without implying future predictive certainty. The user may **Continue Search** for another time window without recycling previously examined/offered formulas or erasing earlier saved discoveries. After review, the user may choose a formula to participate in the original Math / Champion workflow; **selection and Champion promotion are not the same operation**.

Approved design direction:
- **30 seconds** as the initial default search *time budget* (original 60-second idea may be reconsidered after real-device benchmarking); timer indicates **elapsed real time**, not fake percentage of formula-space completion.
- Search stops at the time budget or a user cancellation, and never continues secretly after stopping. State and results survive reopening/restart. If the generator truly cannot advance, report the cause instead of fabricating progress.
- Formula-space generation is **expandable beyond the existing 207 expression shapes**, rather than ending at 207/828 positions. "Infinity" means **no fixed prewritten upper count on the expression grammar**; each individual search is constrained by time, CPU, RAM, feasible grammar depth and mathematical distinctness. No promise of infinitely many behaviorally different formulas.
- A first discovered candidate may directly have a 10+ historical streak; no requirement to discover lengths 3, 4, 5 ... sequentially first.
- For a given target, category and **exact historical consecutive-streak count**, store/display the first distinct qualifying formula once; do not replace it with another same-length formula by default. Different lengths may be discovered in any order. Keep previously found results across Continue requests. Final 10+ display / exact-length semantics still need an explicit acceptance fixture and user review (see Open decisions).
- Separate LONE, WAM and BREAK evaluations, storage and results. Category-digit hits must never be mislabeled exact two-digit-pair hits.
- Do **not** modify the original DOC 8, Gemini 210, existing Champion selection, 2D Analysis, LIVE, ledger or other app behavior during **engine-only** stages. Do not change UI flow/Champion logic without user approval.
- Preserve confirmed draw precedence, missing-session/holiday safeguards, AM/PM target identity and source finality. Historical fits, held-out replay and actual pre-result locks must be labeled as different kinds of evidence.

## 2. Starting point and discrepancy to repair

The existing experimental code contains `CherryFormulaDiscoveryEngine` (4 grammar stages, **207** shapes; 9 + 60 + 60 + 78), `CherryDiscoverySearchMore` (finite **up to 828** expression/offset positions per category), history windowing and trial/cursor infrastructure. It has a minimum streak of 3 and a research target of 10. The currently wired UI returns one fresh behavioral alternative per Search More action, unlike the approved **timed batch** model. The `nextRound` leaderboard retains one representative per category/streak bucket, but the UI's `SearchMore` does **not** enforce one newly offered candidate per streak length. The fixed grammar cannot expand itself. Current Discovery selections are separate from the existing Champion.

**Do not treat a green CI build or existing finite Search More as proof that the new product behavior has been implemented.**

Reference files:
- `app/src/main/java/com/myanmar/ledger2d/core/domain/CherryFormulaDiscoveryEngine.kt`
- `app/src/main/java/com/myanmar/ledger2d/core/domain/CherryDiscoverySearchMore.kt`
- `app/src/main/java/com/myanmar/ledger2d/feature/main/CherryDiscoveryPanel.kt`
- `docs/cherry-discovery-engine-agreed-contract.md`
- `docs/cherry-discovery-long-term-performance.md`

## 3. Proposed architecture (engine before UI)

```text
Target AM/PM + category + confirmed pre-target results
                 |
        Bounded history adapter
                 |
      Timed Search Session Controller
         (pause/cancel/resume)
                 |
     Expandable Symbolic Generator
    /               |                \
Legacy 207     Complexity/Lag       New operators
grammar         expansion            / mutations
    \               |                /
        Candidate scheduling
                 |
   Canonicalization + semantic dedup
                 |
    Historical streak evaluation
                 |
   Category + exact streak registry
                 |
    Search checkpoint + persistence
                 |
    Batch results + largest-streak
      historical recommendation
                 |
    Explicit user-selected formula
                 |
    [separate, user-approved future
          Champion integration]
```

Implement as an isolated Kotlin domain engine with minimal adapters; **no heavy search on Compose/Main**. Keep old engine and legacy features unchanged until feature-specific regression tests pass and user authorizes integration.

## 4. Delivery phases and acceptance gates

### Phase 1 — Baseline protection and requirements traceability
- Record exact passing baseline commit and verify no hidden UI/Champion changes will be needed for V2 core.
- Maintain a requirements-to-tests checklist; separate **agreed**, **implemented**, **CI passed**, **device verified**, and **user approved**.
- Add focused engine-only changes on V2 branch. Do not merge into `main` automatically.
- **Gate:** the existing successful build remains reproducible; original Math/Champion/Analysis/LIVE behavior is protected.

### Phase 2 — Expandable symbolic formula generator
- Keep the 207 grammar as the verified starting repertoire, not the end of searching.
- Define a typed grammar over lagged top, tail, break, arithmetic operators, modulo-10 normalization, and bounded domain-safe transformations. Expand by increasing complexity and permitted lag depth.
- Generate **lazily**; never allocate all possible formulas, trees, traces or matched-draw lists in RAM.
- Give expressions stable canonical IDs, versioned grammar rules and deterministic continuation points.
- Reject undefined calculations (e.g., divide-by-zero if division is ever proposed); only add new operators after correctness/benchmark review.
- **Gate:** tests demonstrate more than 207 structurally distinct valid candidates, unbounded-in-principle grammar growth under increasing complexity, and finite memory within one search budget. More formulas **does not** itself establish forecasting quality.

### Phase 3 — Quality-guided, diversity-preserving search
- Evaluate a candidate's full **current** historical consecutive-streak length directly; a 10+ result must be accepted immediately even if a lower length was never saved.
- Search both straightforward and novel expressions using a bounded, deterministic scheduling policy. Reserve some exploration budget so promising-looking patterns do not monopolize the search.
- Use cost/complexity and distinct behavior to prioritize work; **do not overfit prioritization to observed streak length alone**.
- Optionally explore local mutations/crossovers after benchmarks justify added overhead; do not require a heavy GP/PySR runtime on the phone.
- **Gate:** nonconstant synthetic fixtures prove that direct 10+ discovery and simple/complex grammar continuation both work; no future target outcomes leak into candidate selection.

### Phase 4 — One-per-streak registry and deduplication
- Independent keyed registries: `(target, category, actualStreakLength)` -> **first found** formula, with formula ID, expression, offset, evidence, input signature, scan provenance.
- A second distinct formula with the same streak count is not inserted or newly presented; different streak lengths may be retained in any discovery order.
- Dedup **identical structural IDs** and **identical bounded historical + next-target output behavior**; canonicalize WAM equivalence. Behavioral equality on observed inputs is not proof of algebraic equivalence for all inputs.
- Maintain a reproducible search frontier / generation cursor to avoid rescanning work across Continue; do not assume an ever-growing in-memory set is acceptable.
- **Gate:** exact tests for 3->3 reject, 3->4 accept, 10 first accept, 10->10 reject, 4 found after 10 accept, restart and continue without repeats, cross-category isolation, and correction-triggered invalidation.

### Phase 5 — 30-second session controller and persistence
- Start work only when user explicitly requests it; one worker per active session/category initially.
- Use monotonic elapsed time and a deadline, cooperative cancellation, small CPU batches and background coroutine dispatcher; no work on the main thread. **Time budget is a limit, not a promise of a specific number of formulas found.**
- Persist first-found streak registry, exact next generator/frontier position, grammar version, target, category and bounded history fingerprint atomically.
- Stop work at timeout/cancel; prevent later writes from cancelled work. Continue from the last committed checkpoint with another 30-second window.
- If battery/thermal pressure or device responsiveness requires throttling, reduce work rather than consuming all CPU. Very long atomic operations must be split.
- **Gate:** timeout/cancel/restart, repeated Continue, storage corruption and history-correction tests demonstrate no lost previous result, duplicate re-offer or accidental silent background search.

### Phase 6 — Evidence quality and validation
- Keep **in-sample historical streak** separate from **time-ordered held-out checks** and **genuinely pre-result locked trials**.
- Use only data available *before* each replay target. Compare LONE/WAM/BREAK individually with chance baselines and fixed older formulas over multiple years.
- Reject tests that merely prove a candidate can be constructed after seeing the answer; actively test shuffled data and overfitting from broad formula search.
- A Recommended label means **largest observed historical streak among retained formulas**, not verified future accuracy.
- **Gate:** reproducible reports, coverage counts and explicit no-selection cases; no claim of predictive advantage without independent evidence.

### Phase 7 — Android benchmark and stress gate
- Compare original 207 baseline against V2 on real Android devices, including lower-end devices: CPU, RAM, thermal behavior, UI responsiveness, battery, rotation, background/resume and rapid stop/start.
- Stress 200+3 normal history context and 5k/10k *archive* input cases; database reads should be bounded, with archive preserved.
- Verify actual search attempts/sec, distinct tested behaviors/sec, retained streaks, duplicates avoided, pause latency, peak RAM and responsiveness; tune batch/yield/priority budget from measured outcomes.
- **Gate:** documented real-device acceptance before user-facing integration. GitHub CI alone is insufficient; a successful build is not a speed guarantee.

### Phase 8 — User-reviewed timed-batch UI integration
- Proposed flow (requires final layout approval): choose **လုံးပိုင် / ဝမ်းချိန်း / ဘရိတ်**, tap **ရှာဖွေမည်**, show 30-second elapsed-time bar and cancel, then show all retained distinct-streak formulas, with highest historical streak **Recommended**.
- **ဆက်ရှာမည်** runs another time budget with no newly presented duplicates, while earlier results remain selectable; selecting a formula is explicit and does not silently lock or replace Champion.
- Do not display internal 0–828 limits for V2 as though they were the whole formula space. Do not fake a progress percentage of infinity.
- **Gate:** user approves screens/interaction rules first; UI-level tests and real phone review follow.

### Phase 9 — Separate, user-approved Math/Champion integration
- A user-picked formula may enter the original system **only after** the rules for category mapping, scoring/stars, selection precedence, data finality, persistence, rollback and Champion competition are agreed.
- Preserve old DOC 8/Gemini 210, prior selections and operational winners; no auto-Champion promotion, automatic result writes or quiet replacement.
- **Gate:** user signs off integration semantics, tests verify no original feature regressions, and the installed APK is checked on phone.

## 5. Historical-data and resource policy

- Normal discovery starts with **200 newest completed draw slots + up to 3 lag-context slots** as the currently agreed performance starting point, not a magical proof of optimal predictive skill.
- Research audit: **80 sampled targets over 2 years** by default, optionally **3 years**, with each checked target using pre-target context only. Keep full archive; do not delete old data to make searches fast.
- Additional lag depth requires deliberately increased context, which must be bounded per session and reviewed for performance and validity.
- Missing AM/PM result slots are not silently bridged as if confirmed; respect weekends/holidays, duplicates, corrected results and manual winner precedence.

## 6. Open design decisions — DO NOT ASSUME APPROVAL

1. **10+ streak representation:** keep one formula for each *exact* streak (10, 11, 12, ...), or retain a 10+ bucket that shows longest count? User intent favors distinct lengths, but the old code uses a capped 10+ bucket. Confirm before UI/registry lock-in.
2. **Same-length better candidate:** initial rule says first found wins; any later replacement based on held-out evidence or user action requires explicit approval.
3. **Formula operations/depth/lags:** specify allowed operators, search complexity growth and a fair deterministic scheduling policy after tests; avoid expanding into meaningless expression variants.
4. **User-selected formula -> Champion:** exact entry point, star/grade criteria, how long a choice remains active, conflict policy, and what happens after historical correction all await detailed consultation.
5. **30 vs 60 seconds / adaptive budget:** use 30 seconds initially; test on phone before final settings and UI design.
6. **Search result presentation:** sort by streak length; confirm how to display evidence pairs, offsets, previous rounds and empty-result sessions.

## 7. Change control and definition of done

Every phase must produce: (a) files changed, (b) tests covering the relevant numbered requirements, (c) exact commit SHA, (d) CI status on that SHA, (e) device results where relevant, and (f) explicit user review for any changed product behavior. **Never say a phase is complete merely because a commit exists or CI is green.**

**Current delivery:** only this Master Plan and an isolated V2 work branch based on passing Build #1641. Generator V2, timed batch, V2 UI and Champion integration are **not implemented by this documentation commit**.
