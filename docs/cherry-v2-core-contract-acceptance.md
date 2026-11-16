# Cherry 2D — Original V2 Discovery Engine Contract

**Branch:** `feature/cherry-discovery-v2-from-build1641`, based on approved #1641.  
**Scope:** V2 engine + diagnostic tests only, not a production/UI release. Research, existing DOC/Gemini 210, Analysis, approved Math UI, Champion, and `main` remain unchanged.

## What the original agreement means

1. **Only explicit search starts computation.** Constructing `CherryV2DiscoveryService`, opening the app, or reading saved results does not search. Its optional `auditSearchOrder` is also an explicit developer-only operation.
2. **Choose exactly one category:** လုံးပိုင် (`LONE`), ဝမ်းချိန်း (`WAM`), or ဘရိတ် (`BREAK`). Category-specific saved cursors never exchange candidates.
3. **Unlimited candidate *syntax*:** `CherryV2GuidedSchedule` version **3** interleaves lazy `CherryV2EligibleGrammar` expression trees (lag 1–3 only) with lag-1..3 cross/nested formulas. The older unrestricted RAW grammar is preserved separately for research, not normal V3 search. Formula slots pass 207 without a finite end sentinel. This does **not** mean infinitely many distinct digit outcomes.
4. **First-found recent streak ladder:** inspect prior settled history strictly before the target. Below **3** consecutive matches, discard. First **3**-streak is saved; the next formula with a **3**-streak is ignored. First **4**-streak is saved, likewise 5,6,7,8,9. The first formula whose historical match is **10 or more** goes into **10+**; later 10+ formulas are rejected. A formula can jump straight from a shorter level to 10+; no artificial ladder is required.
5. **Never overwhelm user with formulas:** at most **eight** stored *new* and visible milestone results (3,4,5,6,7,8,9,10+). A pre-existing pre-cap V2 checkpoint may still physically contain old 11+ exact-length fields: keep them untouched to avoid losing user's completed work, and summarize them as the earliest 10+ when displaying. The checkpoint *format* remains compatible, but V2 and V3 schedule indices are deliberately **incompatible**; their local storage namespaces are separate so old saved bytes are not erased by V3.
6. **No history lookahead or gaps:** target/after-target results are not formula inputs. Source corrections and known closure dates feed the history signature; unknown missing working sessions stop consecutive streaks, not automatically count as `Off`.
7. **One deliberate 30-second run:** search uses a monotonic elapsed budget **after** bounded Room data loading. Each finished expression/offset group advances one cursor position. No 100,000-formula count cap. A time limit in mid-expression retries that expression on Continue.
8. **Cancel and Continue:** cancellation is checked inside decoder/evaluator, preserves fully completed positions and the recent bounded semantic-dedup window, persists a checksum-checked checkpoint, then propagates cancellation. The next button-triggered search restores exactly that target/category/history/version cursor, never resets its first-found match. Corrections invalidate a previous history signature.
9. **Off Main / bounded resources:** database uses IO dispatch; formula evaluation uses Default; the Master Plan's normal **200 scored + 3 lag = 203 total draw slots** is enforced by `CherryV2HistoricalEvaluator.CONTEXT_LIMIT`. The normal V3 generator only generates lag-1..3 features, avoiding the former waste of producing lag-4+ expressions that the evaluator would reject. The legacy RAW diagnostic generator is unchanged; any lag-depth expansion requires review. The **512**-behavior recent signature window and throttled checkpoint writes avoid infinite collections and excessive disk I/O. The `V2-203-LAG3` signature marker and **V3 schedule-version check** forbid accidental reuse of incompatible V2 cursors. New search progress writes to `cherry_v2_discovery_cursors_v3`; old `..._v2` progress remains physically untouched.
10. **Recommendation is evidence, not a guarantee.** Historical 10+ fit is not proof of accurate future draws. The V2 service does not automatically change Champion or a user's already approved formulas.

## Master Plan alignment and unresolved gates (2026-11-16)

The user's acceptance of the review direction is applied as **one first-found 10+ milestone** and **203-slot baseline policy**, without changing the original Master Plan or non-V2 screens. This document is the requirements-to-tests status page; a passing CI is not equivalent to device certification.

| Master phase | Current demonstrated state | Remaining gate |
| --- | --- | --- |
| 1. Baseline and change control | Isolated V2 branch descended from Build #1641, unchanged legacy Math/Research/UI | Keep exact-head CI and no main merge |
| 2. Open-ended symbolic grammar | Lazy lag-1..3 V3 generator, >207 syntax tests | Only tested bounded subsets are validated; no infinite compute promise |
| 3. Quality-guided diversity | Deterministic SIMPLE/CROSS-LAG/NESTED guided schedule and synthetic direct-10+ fixture | Adaptivity/held-out advantage not established |
| 4. First-found and dedup | 3..9 and earliest 10+ tier, eight max, recent behavior dedup, resume tests | User-facing detail/evidence presentation reviewed only in future UI phase |
| 5. Timed search/checkpoint | 30s monotonic simulated clock, cancel/continue, saved-state, V2/V3 version isolation and stale policy invalidation | **Physical** 30 seconds, battery/thermal and process-lifecycle checks |
| 6. Evidence/validation | Year-sliced historical and held-out replay marked non-prospective | Verified new results and truly pre-result locked prospective trials |
| 7. Android device stress | Off-Main IO/Default, bounded 203 history, local checkpoint tests | Actual CPU/RAM/thermal/rotation/rapid cancel/start on phone |
| 8. Timed-batch UI | **Not connected** | User-approved interaction and device review |
| 9. Math/Champion | **Not connected** | Separate approval, scoring/persistence/rollback tests |

**Do not restart unrelated RAW/GUIDED historical benchmark loops as a substitute for phases 4, 5 and 7.** Existing earlier reports remain archival diagnostics. Discovery has no demonstrated future predictive advantage.

## Verification scope

- `CherryV2StreakRegistryTest` verifies duplicate-3 rejection, first-4 saved, direct 10+, later same-tier discard, eight visible tiers, and compatibility with old 11+ checkpoint records.
- `CherryV2EngineContractTest` exercises the **actual** guided expression generator, evaluator, time-limited controller, checksum codec, cancellation, saved/resumed progress, distinct category outputs, and scan progression beyond legacy **207** slots. Its clock-based 30-second test is explicitly **simulated** and fast; it does **not** imply a physical phone stayed responsive for 30 real seconds.
- `CherryV2DiscoveryServiceTest` runs on Room-in-memory/Robolectric: no constructor pre-run, explicit search and saved/read-only call, same category resume after a recreated service, history-correction invalidation, corrupt data fail-closed, and at most eight results exposed through an Android receipt.
- CI prints `CHERRY_V2_CORE_GATE` lines plus all Unit Tests/Lint/Debug/Release build outcomes. Only a CI run for the **exact head SHA** confirms this change.

## Mandatory real-device acceptance before any UI changes

A real Android phone must verify button-triggered start; opening Welcome/Home/Math without tapping must start zero V2 scans; **30 actual seconds** plus separate data-loading time; progress indicator and Cancel responsiveness; memory/CPU/heat; app navigation while searching; repeated Continue across process death; and persisted first-found results, using all 3 categories and both sparse/corrected and complete histories.

**Status:** Automated acceptance in progress, real-device measurements still **not performed**. No reliable future-prediction claim is made. Benchmarking based solely on historical replays must not displace these core acceptance checks.
