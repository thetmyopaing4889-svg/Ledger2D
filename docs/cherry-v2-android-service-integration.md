# Cherry V2 — On-demand Android Service & Checkpoint Integration

**Implementation status:** Engine-only adapter committed to the isolated `feature/cherry-discovery-v2-from-build1641` branch. **Not connected to Compose UI or Champion.** All claims remain conditional on CI and later real-device QA.

## Entry point

`CherryV2DiscoveryService(context, database)` has **no constructor side effects**. A caller must explicitly invoke `suspend fun search(target, kind, asOf, timeBudgetNanos)`. Its algorithm:

1. Read bounded confirmed-source history with existing winner/daily/tombstone precedence, **200 scored + 3 possible lag slots = 203 total** per the original Master Plan. The V2 history fingerprint includes a new policy marker, preventing old 200+12 V2 checkpoints from resuming as if they were calculated under this policy. Existing legacy discovery also defaults to 203 slots; its UI/engine behavior is unchanged.
2. Normalize and verify contiguous AM/PM history without target leakage on `Dispatchers.Default`.
3. Restore **only** the corresponding target, category, history hash and versioned V2 search state from isolated local `SharedPreferences` (I/O).
4. Execute `CherryV2TimedSearch.run` off Main. A normal session uses a **30-second computation budget**, measured monotonically **after data loading**. Coroutine cancellation uses `ensureActive` checks while generating and scoring; the search itself starts **no background process**.
5. Persist complete checkpoints at throttled intervals, on timeout, and when cancelled. Reopening the app and invoking a new explicit Search resumes at the last completed expression schedule slot. Return a receipt with counts, retained candidates and largest **historical** streak.

`suspend fun saved(...)` performs read-only recovery. It **does not start a formula search**.

## Protection rules

- No changes to original DOC/Gemini formulas, Research, 2D Analysis, existing UI flows, Champion, prospective trial journal, or `main`.
- First distinct formula for each category/exact historical streak is preserved across Continue. Category/target and corrected-history hash isolate results.
- Damaged checkpoints throw rather than silently erase previous discoveries. A stale writer cannot move a same-history cursor backwards or replace retained streak entries.
- Schedule VERSION 2 rejects older, incompatible schedule cursors. Different versions do not mix formula index IDs.
- Search memory remains bounded by normalizer limits and recent semantic dedup window; no list of an infinite formula space is allocated.
- Computation uses `Dispatchers.Default`, database reads use `Dispatchers.IO`. Synchronous `SharedPreferences.commit` is invoked from the worker thread, never Compose/Main.
- A user cancellation stops work and keeps the last *completed* candidate; it may not preserve a partially evaluated expression.

## Tests and limits

`CherryV2DiscoveryServiceTest` runs Room-in-memory + Robolectric checks for on-demand initiation, persisted second-session cursor advancement, first-found retention, corrected-history invalidation, category isolation and corrupted state fail-closed behavior. The prior bounded-source test additionally checks that legacy 203 slots stay unchanged while V2 can load 212 slots.

**Still required before exposing to users:**
- Pass **latest head-SHA** CI, plus real Android device testing with 30-second runs (CPU, memory, thermal, responsiveness, cancellation latency, process recreation).
- Compare guided vs sequential search over reproducible identical histories and fixed time budgets, with held-out outcomes and chance baselines; synthetic 10+ histories are not evidence of future predictive advantage.
- Agree and implement time-bar/Cancel/Continue UI semantics, including data-preparation phase and stored-state presentation, after user review.
- Explicit user approval of selected Formula -> original Math/Champion mapping/scoring and persistence; selection must not silently crown a Champion.
