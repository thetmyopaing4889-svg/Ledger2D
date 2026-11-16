# Cherry 2D — Original V2 Discovery Engine Contract

**Branch:** `feature/cherry-discovery-v2-from-build1641`, based on approved #1641.  
**Scope:** V2 engine + diagnostic tests only, not a production/UI release. Research, existing DOC/Gemini 210, Analysis, approved Math UI, Champion, and `main` remain unchanged.

## What the original agreement means

1. **Only explicit search starts computation.** Constructing `CherryV2DiscoveryService`, opening the app, or reading saved results does not search. Its optional `auditSearchOrder` is also an explicit developer-only operation.
2. **Choose exactly one category:** လုံးပိုင် (`LONE`), ဝမ်းချိန်း (`WAM`), or ဘရိတ် (`BREAK`). Category-specific saved cursors never exchange candidates.
3. **Unlimited candidate *syntax*:** `CherryV2GuidedSchedule` interleaves raw `CherryV2ExpandableGrammar` enumeration with cross-lag and nested formulas. Formula slots pass 207 without a finite end sentinel. This does **not** mean infinitely many distinct digit outcomes.
4. **First-found recent streak ladder:** inspect prior settled history strictly before the target. Below **3** consecutive matches, discard. First **3**-streak is saved; the next formula with a **3**-streak is ignored. First **4**-streak is saved, likewise 5,6,7,8,9. The first formula whose historical match is **10 or more** goes into **10+**; later 10+ formulas are rejected. A formula can jump straight from a shorter level to 10+; no artificial ladder is required.
5. **Never overwhelm user with formulas:** at most **eight** stored *new* and visible milestone results (3,4,5,6,7,8,9,10+). A pre-existing pre-cap V2 checkpoint may still physically contain old 11+ exact-length fields: keep them untouched to avoid losing user's completed work, and summarize them as the earliest 10+ when displaying. Existing format/version remains compatible.
6. **No history lookahead or gaps:** target/after-target results are not formula inputs. Source corrections and known closure dates feed the history signature; unknown missing working sessions stop consecutive streaks, not automatically count as `Off`.
7. **One deliberate 30-second run:** search uses a monotonic elapsed budget **after** bounded Room data loading. Each finished expression/offset group advances one cursor position. No 100,000-formula count cap. A time limit in mid-expression retries that expression on Continue.
8. **Cancel and Continue:** cancellation is checked inside decoder/evaluator, preserves fully completed positions and the recent bounded semantic-dedup window, persists a checksum-checked checkpoint, then propagates cancellation. The next button-triggered search restores exactly that target/category/history/version cursor, never resets its first-found match. Corrections invalidate a previous history signature.
9. **Off Main / bounded resources:** database uses IO dispatch; formula evaluation uses Default; a bounded history of **200 score + 12 lag slots**, a **512**-behavior recent signature window and throttled checkpoint writes avoid infinite collections and excessive disk I/O.
10. **Recommendation is evidence, not a guarantee.** Historical 10+ fit is not proof of accurate future draws. The V2 service does not automatically change Champion or a user's already approved formulas.

## Verification scope

- `CherryV2StreakRegistryTest` verifies duplicate-3 rejection, first-4 saved, direct 10+, later same-tier discard, eight visible tiers, and compatibility with old 11+ checkpoint records.
- `CherryV2EngineContractTest` exercises the **actual** guided expression generator, evaluator, time-limited controller, checksum codec, cancellation, saved/resumed progress, distinct category outputs, and scan progression beyond legacy **207** slots. Its clock-based 30-second test is explicitly **simulated** and fast; it does **not** imply a physical phone stayed responsive for 30 real seconds.
- `CherryV2DiscoveryServiceTest` runs on Room-in-memory/Robolectric: no constructor pre-run, explicit search and saved/read-only call, same category resume after a recreated service, history-correction invalidation, corrupt data fail-closed, and at most eight results exposed through an Android receipt.
- CI prints `CHERRY_V2_CORE_GATE` lines plus all Unit Tests/Lint/Debug/Release build outcomes. Only a CI run for the **exact head SHA** confirms this change.

## Mandatory real-device acceptance before any UI changes

A real Android phone must verify button-triggered start; opening Welcome/Home/Math without tapping must start zero V2 scans; **30 actual seconds** plus separate data-loading time; progress indicator and Cancel responsiveness; memory/CPU/heat; app navigation while searching; repeated Continue across process death; and persisted first-found results, using all 3 categories and both sparse/corrected and complete histories.

**Status:** Automated acceptance in progress, real-device measurements still **not performed**. No reliable future-prediction claim is made. Benchmarking based solely on historical replays must not displace these core acceptance checks.
