# Cherry Formula Discovery — 2026 Historical Audit

Date: **2026-11-15**. Status: **experimental, not connected to the UI or production picks**.

## Dataset and verification method

- Public upstream fixture: https://github.com/2d3dthailand/2d3dthailand/blob/main/2D_history
- Source snapshot through **2026-11-13**; 227 recorded weekdays in 2026, 210 trading dates with both AM and PM results (**420 draws**), and 17 dates marked **off/closed**.
- Pinned 2026 CSV in CherryDiscovery2026CoverageTest.kt, so future changes in the public feed cannot silently change test results.
- Replay only the **last 80** observed draws. At each target T, discover exclusively from results **before** T, choose one formula per category by longest historical streak, freeze its output, then compare against actual T.
- Closed weekdays are explicit; no silent bridging across missing draws. Distinguish “no formula” from a miss.
- Test first-stage grammar separately from all four stages (9 vs 207 structural expressions).
- Categories LONE, WAM and BREAK are not exact 2-digit winning pairs.

## Independently recomputed metrics (pending CI confirmation when first authored)

| Category | Stage 1 selected / held-out hits | Four stages selected / held-out hits | Longest four-stage historical fit |
| --- | ---: | ---: | ---: |
| LONE | 35 / 6 | 80 / 9 | 6 draws |
| WAM | 61 / 21 | 80 / 24 | 10 draws |
| BREAK | 5 / 0 | 65 / 4 | 5 draws |

- Denominator 80 = number of held-out targets considered per category.
- Selected-only rates across four stages: LONE 9/80 = 11.25%, WAM 24/80 = 30%, BREAK 4/65 = 6.15%.
- BREAK had no selection on 15 of the 80 targets.
- A ten-draw retrospective WAM fit is **not** ten forward predictions.
- These data are not guarantees, measured future winning probabilities, or proof of forecasting power.
- For scale, the idealized uniform independent 00–99 chance baselines are LONE **19%**, WAM **36%**, BREAK **10%**. Results do **not** demonstrate improvement over these baselines. Different categories must not be ranked by raw streak length.

## Safety, engineering and limitations

- Finite grammar: 9 + 60 + 60 + 78 = **207 structural expressions**, with historical result-derived fixed offsets in four continuation stages. This is not limitless mathematical discovery.
- First matching formula within each category and streak bucket is retained, at most 24 total.
- A stress unit test replays **1,000 synthetic identical draws** to maximize long fits, checking all 207 expressions terminate, at most 24 saved formulas remain, and repeated search after completion makes no change. GitHub runner speed is not Android device speed.
- App/UI integration remains blocked until real device main-thread and cancellation testing, stage-level memory/CPU benchmarking, and user-facing evidence labels.
- **Key finding:** more stages increase historical fit availability, but this can reflect overfitting, not increased held-out predictive usefulness. Do not auto-promote a Champion based only on in-sample streak.

## Next checks

1. Confirm Kotlin test measurements in CI; regression tests assert pinned 2026 hit counts independently computed from the public data.
2. Evaluate temporally separated 2023, 2024 and 2025 periods with the same fixed grammar.
3. Compare staged selections against simpler baselines without tuning to held-out results.
4. Measure device performance and cancellation before UI integration.

Original DOC formulas (8), Gemini research variants (210), and existing Better Data UI remain unchanged.
