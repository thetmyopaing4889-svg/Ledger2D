# Cherry V2 — Historical Seed Audit (80 × 3 categories)

**Status:** CI-backed historical-data audit fixture; **not** a live prospective trial or user-facing feature. Execute only through the dedicated JVM test or an explicit developer action.

## Provenance and availability

- Input: original app asset `app/src/main/assets/history_seed.json` (read-only; no replacement or modifications).
- Snapshot last date: **2026-09-29**. This is **not current** for November 2026; no later archived outcome is invented.
- Source contains **959** dated entries spanning **2023-01-02–2026-09-29**.
- **904** entries have a valid 2-digit morning and evening pair; **54** explicit `Off` markers exist. One day has a partial valid pair. Weekend rows are excluded. Counts are verified from the checked-in JSON, not a live feed.
- Imported historical results are labeled `HISTORY` and are **not** verified operational `WINNER` or truly timestamp-locked outcomes. The test never writes to Room, Research, saved cursors or Champions.

## Selection and prevention of data leakage

- Select **80 evenly distributed, time-ordered target draw slots over the last two years relative to this file's newest date**.
- For each target, build only the strict **pre-target AM/PM context**, using confirmed-before-target position checks. Keep a maximum of 200 scored slots + 12 lag inputs. A missing weekday draw stops continuity; known `Off` days can be skipped, but a missing date is **not automatically assumed closed**.
- Evaluate each of `LONE`, `WAM`, `BREAK`; inspect up to **64 formulas for each of two orders (RAW and GUIDED)**, with equal per-order 350 ms monotonic search budget. A budget may stop one or both before formula #64. This is a repeatable functional audit, **not** a scientifically precise device-performance benchmark.
- Pick the **longest first-found historical-streak candidate** from each mode using only the training-target history. Freeze its formula tree, category and offset; evaluate on the same **next four already-recorded draws**. Scored actual pairs do not participate in formula/offset choice.
- Print one `CHERRY_V2_SEED_AUDIT` line per category: RAW/GUIDED slots inspected, unique behaviors, number of targets with 10+ historical streak, each mode's selected targets, held-out hits/checked/abstentions, observed hit rate and uniform-random chance reference.

## Interpretation

- A 3–10+ consecutive match selected from many candidate formulas is **in-sample selection**, with overfitting risk. It does **not** predict future 2D outcomes reliably.
- Holdout here means *retrospective, training-frozen replay*, not verified pre-publication prospective trials. It must always be labeled `IMPORTED_SEED_HISTORICAL_REPLAY_NOT_PROSPECTIVE`.
- LONE/ဝမ်းချိန်း/BREAK references of 19%/36%/10% are idealized uniform-random 2D baselines under current matching semantics, not proof of a betting edge. Trial observations across categories share actual draw results and are not statistically independent.
- No result is considered `Champion` or automatically selected by this audit. No Android UI starts the audit.

## Remaining acceptance gates

1. Latest-head Android CI must pass **unit test + lint + debug/release APK**. The `Show formula-discovery historical audit` workflow step prints actual audit results from the JUnit XML, making tests inspectable rather than treating code as evidence.
2. Review both **RAW** and **GUIDED** held-out rates and sample sizes; identify whether the supposed search ordering improves *only retrospective fit* or later replay as well.
3. Confirm on an actual Android phone with **30-second button-triggered search** and inspect CPU/RAM, thermal, navigation, cancellation, checkpoint-restoration performance. This historical seed audit does not measure them.
4. Obtain fresh verified outcomes for any 2026-09-30 onward comparison; the embedded seed stops on 2026-09-29.
5. UI/Champion linking waits for explicit design approval and does not silently change the previously approved DOC/Gemini/Research areas.
