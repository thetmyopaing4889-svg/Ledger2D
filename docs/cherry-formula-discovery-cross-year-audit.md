# Cherry Formula Discovery — Cross-Year Engineering Audit

Date: 2026-11-15. Status: **experimental only**. No changes to existing DOC 8, Gemini 210, UI or production shortlist.

## Test design

The grammar and longest-streak rule were held fixed for all four years. Pinned public 2D_history fixtures were replayed with each target result removed from discovery inputs. Each row below summarizes the **last 80 observed draws of that calendar year**, under the exact category-specific digit hit definitions (not exact 2D pair hits).

| Calendar period | Draws available | Closed dates explicitly recorded | LONE (hit/selected) | WAM (hit/selected) | BREAK (hit/selected) | Longest retrospective LONE/WAM/BREAK |
|---|---:|---:|---:|---:|---:|---|
| 2023 | 486 | 17 | 19/76 | 29/80 | 10/64 | 7 / 13 / 5 |
| 2024 | 480 | 5 | 19/68 | 19/68 | 8/52 | 8 / 8 / 7 |
| 2025 | 481 | 17 | 7/80 | 32/80 | 1/64 | 6 / 13 / 4 |
| 2026 (through Nov 13) | 420 | 17 | 9/80 | 24/80 | 4/65 | 6 / 10 / 5 |
| Combined sampled targets | — | — | 54/304 | 104/308 | 23/245 | Not combinable |

All four rows are now confirmed by Kotlin GitHub CI: 2023/2026 at #1510, 2024/2025 at #1514. Each was independently computed separately before CI confirmation. These rows use explicit closure records in the upstream feed; additional official-holiday supplementation is a separate sensitivity test, not included here.

For idealized independent uniform 00–99 draws, chance digit-category matches are approximately LONE 19%, WAM 36%, BREAK 10%. Combined selected-only rates here are LONE 17.8%, WAM 33.8%, BREAK 9.4%. **These results do not show a stable predictive edge.** Historical fitting is exploratory, not prospective win-rate evidence.

## Source-quality flags

1. **2025-06-10 occurs twice:** one row claims `off/off`, another gives actual AM 55 / PM 60. The isolated 2025 audit fixture explicitly picks the completed record. That exposed a production history ingestion risk: the old `HistorySync.parse2DHistory` kept the first date entry, including `off`. This has now been narrowly fixed to prefer the source row with more valid 2D session values. A dedicated Robolectric duplicate-day regression test was added; this does not edit winners, bets or UI and affects only future history sync imports.
2. **2024-05-21 occurs twice** with identical results; duplicated source records must be deduplicated.
3. **2025-03-28** has AM 30 but PM `xx`. Never fabricate a PM result; never mark the whole day closed.
4. **2024 has 18 weekdays absent entirely from the downloaded history.** Checking the official [Stock Exchange of Thailand 2024 holiday calendar](https://www.set.or.th/en/about/event-calendar/holiday?year=2024) identifies **13** of these as confirmed exchange holidays. The other five missing weekdays (2024-03-21, 2024-04-26, 2024-08-02, 2024-09-20, 2024-11-25) remain **unverified source gaps**. Do not silently assume they were closed.
5. **2025 has three absent weekdays:** 2025-01-01 and 2025-07-10 are corroborated as holidays by the [Thai SEC 2025 securities calendar](https://www.sec.or.th/EN/Pages/News_Detail.aspx?SECID=11077), while 2025-03-21 remains an unknown gap. A separate test evaluates supplementation with *only* officially confirmed holidays, without adding fabricated draws.
6. Rows marked `off` on weekdays are excluded from expected draw-slot lookup via explicit closure dates.
7. Walk-forward uses the best currently available historical feed, not a snapshot of corrections as known at the original time. This limitation is disclosed.

## Overfitting and comparison

- More expression stages find more in-sample streaks, but that by itself is not an accuracy improvement. A fixed offset is inferred from already revealed results, which explicitly biases toward retrospective fits.
- WAM, LONE and BREAK cannot be compared by streak length or raw category hit rate.
- Maintain *source* and *evidence type* labels: original DOC, Gemini research, newly discovered historical fit, and independently logged forward validation.
- Do not award 5-star predictive confidence based on a ten-draw fitted streak.
- Only select one representative formula per category and exact streak bucket; treat 10+ as a bucket that may contain longer true streaks. Keep first simple representative for ties.

## Runtime reliability

- 4 finite grammar levels with 9+60+60+78 = 207 expression shapes; derived offsets vary with observed outcomes.
- Completed search state is keyed to the target and sorted history; results change only when history/target changes or an additional unused stage runs.
- A 1000-all-equal-draw stress test finished all four levels on GitHub runner in 144 ms (#1510). This is **not an Android device benchmark**.
- **Source anomaly:** the upstream 2D_history also contains a valid-looking 2024-11-23 **Saturday** (AM 91, PM 18). The discovery engine and independent walk-forward replay now quarantine non-working-day rows rather than counting them as ordinary AM/PM rounds. Consequently, the initial 2024 row of this table is a **pre-quarantine historical snapshot**; re-evaluated CI metrics will supersede it. Source history was not deleted.
- Engine has been augmented with an optional cooperative cancellation callback; UI must later invoke this with its coroutine cancellation check, from background CPU execution, and test on representative Android devices.
- Never promise infinite formula generation or guarantee a ten-streak formula.

## Next decision gate

Do not connect the experimental engine to live user-facing picks until data-quality repair is planned, cross-year reports are confirmed, candidate complexity/deduplication is reviewed, and real-device cancellation/performance is tested. A genuinely independent prospective paper-trial is the correct next measure of whether discovery adds any usefulness compared with the unchanged original formulas.
