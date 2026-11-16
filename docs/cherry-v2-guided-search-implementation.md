# Cherry Discovery V2 — Guided Schedule, Current V3 Eligibility Policy

**Branch:** `feature/cherry-discovery-v2-from-build1641`. **Engine only.** No changes to original Research, Analysis, Math/Champion UI, DOC/Gemini or `main`.

The original V2 interleaved schedule (archived in commit history) contained lag-4 to lag-6 expressions. The Master Plan now uses **200 scored + 3 lag = 203 input slots**. The V2 normal scorer rejected those longer-lag candidates, spending a portion of the explicit 30-second search window on known ineligible expressions.

## Version 3 schedule (current)

`CherryV2GuidedSchedule.VERSION = 3`; the first nine slots remain the original top/tail/break features for lags 1, 2 and 3. Thereafter the schedule lazily interleaves three lanes every three slots:

- **SIMPLE:** `CherryV2EligibleGrammar.expressionAt(i)`, a seekable infinite-in-principle enumeration of finite nested unary/binary trees over nine lag-1..3 features. A Cantor pairing maps each finite child-index pair to one index, so **there is no count cap of 207** and no finite eager expression list.
- **CROSS_LAG:** two-feature arithmetic combinations, cycling only through lags 1..3; after the first 9×9×5 shallow combinations, deeper layers embed eligible nested formulas.
- **NESTED:** three-input arithmetic/nonlinear formulas, cycling only through lags 1..3 and expanding to deeper eligible subexpressions.

Examples: slot 0 `top1`, slot 9 `(top1+top1)`, slot 10 `(top1+top2)`, slot 11 `((top1+tail1)+top2)`, slot 280 `(top1+break2)`. Lag 4+ never appears in the **V3 search schedule**, although the separate raw diagnostic `CherryV2ExpandableGrammar` retains its old unrestricted meaning for comparison.

## Preservation and safety

- The **schedule meaning changed** for slots 9 onward: V3 increments the persisted grammar version, and the integrity-checked checkpoint decoder rejects V2 schedule cursors rather than quietly mapping old formula IDs to new formulas.
- V3 progress uses a **new `SharedPreferences` namespace**, `cherry_v2_discovery_cursors_v3`. Existing saved `..._v2` bytes are left untouched. Compatible V3 sessions still resume normally within target/category/203-slot history fingerprint; the old V2 cursor is not incorrectly continued.
- Search remains **button-only**, 30-second monotonic computation budget, cooperative cancellation, on-worker execution, throttled checkpoints and first-found 3–10+ milestones; **not** a forecast guarantee.
- Test gates: `CherryV2EligibleGrammarTest`, `CherryV2GuidedScheduleTest`, `CherryV2EngineContractTest`, `CherryV2CheckpointCodecTest` and `CherryV2CheckpointStoreTest`; exact-head GitHub CI must pass. A real device is still necessary for CPU/RAM/thermal and cancellation-latency claims.

The change improves *eligibility of generated candidates* and removes structurally forbidden lag-depth work. It has **not** established higher prediction accuracy, a phone performance gain, or a new 10-streak forecasting edge.
