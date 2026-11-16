# Cherry V2 — 2023–2026 Year-sliced Historical Replay Audit

**Verified CI:** [Android CI #43798451096](https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/43798451096), commit `7af9923c993062d5bdb64772b050de172dd6bf2f`; all unit tests, lint, debug/release builds and artifact uploads passed.

**Source:** unchanged `app/src/main/assets/history_seed.json`, original 959 records, final date 2026-09-29. The read-only audit quarantines an inconsistent 2025-06-10 `Off` versus `55/60` record, and deduplicates identical records, resulting in 1,803 unique usable slots. The import is historical, **not** verified up-to-date live outcomes.

## Fixed experiment

For each calendar year, preselect 24 equally spaced target AM/PM sessions (96 targets total), with four later recorded outcomes reserved per target. Only data **before** each target is used for discovery. Select the longest first-found historical streak from either RAW or GUIDED at a predefined 64/256-candidate breadth with equal per-mode time budgets. Freeze the expression and digit offset, then replay against four later archived draws. Do not bridge missing unverified working sessions or mark them closed without a source `Off` record. Alternate RAW-first and GUIDED-first execution by target to reduce JVM order bias.

The rates below are **hits / checkable later outcomes conditional on a formula selection**. They are not an overall betting-success rate. The selected/24 columns reveal non-selection and missing-data coverage; the denominators can differ across policies. `10+` means **in-sample retrospective fit**, not 10 future wins.

The source archive and earlier pooled analyses have already been inspected, so this is **EXPLORATORY_YEAR_SLICE_NOT_PROSPECTIVE** — not an untouched confirmatory holdout, not a pre-published forecast, and not evidence of a reliable predictive edge.

## 2023 · usable 23/24 (95.8%)

| Category | Formulas scanned | RAW held-out hits | GUIDED held-out hits | RAW selected / 24 | GUIDED selected / 24 | 10+ in-sample (RAW / GUIDED) |
|---|---:|---:|---:|---:|---:|---:|
| LONE | 64 | 13/88 (14.77%) | 15/92 (16.30%) | 22/24 | 23/24 | 0 / 0 |
| LONE | 256 | 4/92 (4.35%) | 13/92 (14.13%) | 23/24 | 23/24 | 0 / 0 |
| WAM | 64 | 28/92 (30.43%) | 27/92 (29.35%) | 23/24 | 23/24 | 0 / 0 |
| WAM | 256 | 36/92 (39.13%) | 29/92 (31.52%) | 23/24 | 23/24 | 1 / 1 |
| BREAK | 64 | 6/48 (12.50%) | 2/40 (5.00%) | 12/24 | 10/24 | 0 / 0 |
| BREAK | 256 | 9/92 (9.78%) | 9/84 (10.71%) | 23/24 | 21/24 | 0 / 0 |

## 2024 · usable 19/24 (79.2%) — SPARSE HISTORY, interpret cautiously

| Category | Formulas scanned | RAW held-out hits | GUIDED held-out hits | RAW selected / 24 | GUIDED selected / 24 | 10+ in-sample (RAW / GUIDED) |
|---|---:|---:|---:|---:|---:|---:|
| LONE | 64 | 10/71 (14.08%) | 13/70 (18.57%) | 18/24 | 18/24 | 0 / 0 |
| LONE | 256 | 11/73 (15.07%) | 12/72 (16.67%) | 19/24 | 19/24 | 0 / 0 |
| WAM | 64 | 25/73 (34.25%) | 25/74 (33.78%) | 19/24 | 19/24 | 0 / 0 |
| WAM | 256 | 26/72 (36.11%) | 22/74 (29.73%) | 19/24 | 19/24 | 1 / 0 |
| BREAK | 64 | 5/34 (14.71%) | 4/38 (10.53%) | 9/24 | 10/24 | 0 / 0 |
| BREAK | 256 | 5/54 (9.26%) | 9/50 (18.00%) | 14/24 | 13/24 | 0 / 0 |

## 2025 · usable 21/24 (87.5%)

| Category | Formulas scanned | RAW held-out hits | GUIDED held-out hits | RAW selected / 24 | GUIDED selected / 24 | 10+ in-sample (RAW / GUIDED) |
|---|---:|---:|---:|---:|---:|---:|
| LONE | 64 | 10/80 (12.50%) | 11/76 (14.47%) | 20/24 | 19/24 | 0 / 0 |
| LONE | 256 | 13/84 (15.48%) | 15/84 (17.86%) | 21/24 | 21/24 | 0 / 0 |
| WAM | 64 | 32/84 (38.10%) | 34/84 (40.48%) | 21/24 | 21/24 | 0 / 0 |
| WAM | 256 | 29/84 (34.52%) | 32/84 (38.10%) | 21/24 | 21/24 | 1 / 0 |
| BREAK | 64 | 2/40 (5.00%) | 2/36 (5.56%) | 10/24 | 9/24 | 0 / 0 |
| BREAK | 256 | 5/52 (9.62%) | 5/60 (8.33%) | 13/24 | 15/24 | 0 / 0 |

## 2026 · usable 24/24 (100.0%)

| Category | Formulas scanned | RAW held-out hits | GUIDED held-out hits | RAW selected / 24 | GUIDED selected / 24 | 10+ in-sample (RAW / GUIDED) |
|---|---:|---:|---:|---:|---:|---:|
| LONE | 64 | 14/88 (15.91%) | 14/88 (15.91%) | 22/24 | 22/24 | 0 / 0 |
| LONE | 256 | 18/92 (19.57%) | 15/96 (15.63%) | 23/24 | 24/24 | 0 / 0 |
| WAM | 64 | 36/96 (37.50%) | 26/96 (27.08%) | 24/24 | 24/24 | 0 / 0 |
| WAM | 256 | 33/96 (34.38%) | 35/96 (36.46%) | 24/24 | 24/24 | 0 / 0 |
| BREAK | 64 | 3/32 (9.38%) | 4/44 (9.09%) | 8/24 | 11/24 | 0 / 0 |
| BREAK | 256 | 7/80 (8.75%) | 11/76 (14.47%) | 20/24 | 19/24 | 0 / 0 |


## Observations and decision

- Year-to-year quality is **not stable**. For example GUIDED WAM at 64 candidates is 27/92 (29.35%) in 2023, 25/74 (33.78%) in data-sparse 2024, 34/84 (40.48%) in 2025, and 26/96 (27.08%) in 2026. Apparent 2025 strength does **not** persist.
- The 2024 archive is especially sparse: only **19 of 24** sampled targets have sufficient contiguous pre-target history. The remaining five are not counted as wins/losses and are separately reported as `shortHistory`.
- Wider search alone cannot establish accuracy. GUIDED LONE at 256 candidates in 2026 reaches 15/96 (15.63%), while RAW 256 is 18/92 (19.57%); the idealized uniform chance reference for a single matching LONE digit is 19%.
- WAM has an idealized independent-uniform chance reference around 36%, LONE around 19%, BREAK around 10%. Overlapping four-result windows and conditional formula selection mean these numbers cannot be interpreted as independent Bernoulli samples or used for naive significance claims.
- **Do not automatically promote discovered formulas to Champion or change the current approved production search schedule on these results.** Maintain the isolated V2 experimental engine. A historical 10-streak does not imply a prospective edge.
- Next gates: obtain independently verified outcomes *after 2026-09-29* and eventually genuinely timestamp-locked pre-publication forecasts; examine alternative policies only on data not used to choose them; conduct actual Android 30-second cancel/resume, CPU/RAM/thermal and navigation tests. UI and Champion remain untouched until independently approved.

This document records the **original GitHub CI output**, not generated/synthetic lottery outcomes. The attached denominators and selection coverage are essential when interpreting the percentages.
