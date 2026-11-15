# Cherry Discovery Engine V2 — Search-Order & Forward-Validation Gate

**Status:** Experimental core + opt-in Android diagnostic; no user-facing UI or Champion changes.  
**Current working branch:** `feature/cherry-discovery-v2-from-build1641`  
**Last earlier verified green SHA:** `c511d7bcb069c8dc6e309b447ad39fdf509124bb` / [CI](https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/43729358378).

## 1. Apples-to-apples search-order comparison

`CherryV2OrderBenchmark.compare(context, category, candidatesPerMode, budgetPerModeNanos)` provides **RAW** (original open-ended syntax index order) and **GUIDED** (interleaved structural priorities) summaries using the **same immutable before-target history**, category, initial candidate-count limit and independent monotonic time budget. It never writes cursors, locks formulas or runs automatically.

- Reports expressions inspected; scorable expressions; distinct recent behavioral traces; duplicate traces; first-found exact historical streaks; first observed 10+ streak; and measured elapsed time.
- Compares **one equal-budget session** per mode, never extrapolates from a synthetic fixture to all actual draws.
- Hard diagnostic cap: 2,048 candidate slots per mode, or a shorter time budget. Formula evaluation checks deadline cooperatively so a huge expression cannot silently ignore the audit clock.
- `CherryV2DiscoveryService.auditSearchOrder(...)` is an explicitly called, read-only, off-Main Android adapter over the existing bounded Room source. Merely opening the app cannot start this audit.
- For real-device testing, repeat with matched 30-second budgets, more than one target/category, the same data snapshot, and note CPU, peak RAM, thermal and navigation latency. **No real-device result has yet been measured.**

## 2. Selected-formula time-ordered holdout

`CherryV2ForwardValidation.replay(frozenFormula, fullArchive, laterActuals)` uses ONE fixed formula tree, offset and category chosen at an **earlier** training target. Each later target is predicted solely from results available before its draw; the actual target pair is used only for scoring. Missing immediate pre-target data yields **ABSTAIN**, never a fabricated winning streak. The audit is bounded to 256 target outcomes, read-only and cancellable.

Report fields: checked/hits/misses/abstentions, each individual historical pair and fixed-digit prediction, hit rate, plus an **idealized uniform-random** reference (LONE 19%, WAM 36%, BREAK 10%). These references assume independent uniformly distributed 2D digits and cannot by themselves prove a predictive advantage.

The evidence label is **HOLDOUT_REPLAY_NOT_PROSPECTIVE**, meaning replay of known outcomes. It is **not** a truly timestamp-locked prediction made before publication. Strong historical fit discovered from many formulas can still be chance/overfitting.

## 3. Unit test scope

- Nonconstant synthetic recurrence: a two-lag guided formula can directly match 10+ historical draws; comparisons preserve equal candidate/time budgets.
- Future result at the candidate target does not affect the search-order score or its history signature.
- Previously frozen formula evaluated on later targets can neither retune itself nor use the target's own actual result as an input.
- Missing expected AM/PM slot abstains; earlier/repeated/invalid targets fail closed.
- Diagnostic does not create, overwrite or remove normal search checkpoints or Champion choices.

## 4. Acceptance still outstanding

1. **CI** must pass at the exact final head SHA. Intermediate failed CI is not a green build.
2. Actual archive comparison for representative target windows and all three categories must be run and documented; no real winners/baseline advantage claims from synthetic-only tests.
3. Real Android 30-second performance, CPU/RAM/heat, navigation responsiveness, cancellation and process restart must be tested on a weaker device. Automated JVM/Robolectric tests do not certify this.
4. Only after engine acceptance should the Cherry-red Burmese UI be reviewed and wired. Selected formula → DOC/Gemini/Champion mapping still needs separate approval.
