# Cherry 2D Discovery — Long-Term Performance Contract

Status: Experimental domain-only implementation, 2026-11-15.
Working branch: `feature/2d-rules-dual-view`.

## Agreed policy: two separate workloads

| Mode | Input/target policy | Intended use |
| --- | --- | --- |
| **Normal discovery** | Score newest **200 completed draws**, with up to **3 extra prior input draws** for lagged formula expressions | Fast user-triggered formula exploration, one new grammar stage per click |
| **Research audit (default)** | Select up to **80 historical targets distributed across the previous 2 years**; each target searches only 200 prior draws + 3 context | Method comparison and held-out performance measurements |
| **Research audit (optional)** | Same bounded sample workflow over **3 years**, explicitly requested | Broader historical stability check |
| **Archive** | Keep older history on disk/database; **never delete prior results** just to speed discovery | Existing history, manual verification, reproducibility |

- The two/three-year research mode does **not** mean all 2–3 years of rows are processed for every formula. Each audited target is evaluated strictly as-of that target and given only its preceding 203 eligible slots.
- The default 80-target backtest is an **evenly spread sample**, not every single AM/PM draw over two years. The audit returns both total eligible targets and actual sample count; do not label 80 checks as an exhaustive two-year replay.
- 200/80 are pragmatic *engineering starting values*, not mathematically proven best sample sizes or prediction guarantees. The original goal of finding ten consecutive retrospective matches remains; an actual future ten-hit streak requires separately time-locked observations.
- Even a precise ten-fit history does not imply a reliable ten-future-pick formula. Negative controls have found ten-fit streaks after shuffling historical data.

## Engineering changes

1. `CherryDiscoveryHistoryNormalizer.newestBefore` uses an ordered map capped at **203 distinct date/session slots**. It scans caller input once, using **O(N log 203) CPU time and O(203) additional buffer memory**, regardless of how many years exist. Verified winner > daily > history precedence is unchanged.
2. `CherryFormulaDiscoveryEngine.nextRound` scores **no more than 200 frames**. Up to 3 earlier rows are context only. For 200 input rows, a one-lag expression can match at most 199; with 203+ contiguous rows the max is 200.
3. Signatures and saved `matchedDraws` operate only on the window. A correction to ancient, out-of-window input does not unnecessarily invalidate the current completed search; a recent correction does.
4. Candidate evaluation counts consecutive matches first and only builds the `matchedDraws` list when a formula actually occupies a retained slot. This eliminates repeated temporary identity/list allocation for candidates that fail early or lose to an existing formula.
5. Research backtesting slices the previously normalized history by index before each target, instead of filtering and rescanning years of earlier draws on each of four grammar stages.
6. `CherryDiscoveryBackgroundRunner` supplies an **opt-in coroutine wrapper** using `Dispatchers.Default` and cancellation checks. It is **not connected to the UI yet**; UI integration must use this background path rather than invoking heavy discovery synchronously in Compose.
7. Work is cancellable in the input scan, between expressions, and in the 200-frame streak scan. No background global job is started.

## Benchmark contract (not phone results)

The regression benchmark `CherryDiscoveryBoundedHistoryTest` tests **200, 500, 1,000, and 3,000** synthetic draws, all set to 00 to maximize worst-case contiguous matches. Each scenario searches all four stages, reports elapsed GitHub/JVM milliseconds and asserts:

- **207** structural expressions total and no more than **24** stored formula slots.
- Bounded frame count and stable results across **500 / 1,000 / 3,000** older-history sizes.
- 200-draw input is allowed a 199-match one-lag streak, while 500+ supports at most 200 scored matches.
- Search signatures do not grow with old result count.
- Older prefix corrections do not restart the search, while a recent correction does.
- Out-of-order inputs, source-priority overrides and cancellation behave predictably.

Do not set brittle absolute latency thresholds on shared GitHub runners. Report observed timings **only after CI actually prints them**.

## Observed first GitHub JVM benchmark (#1601)

The 2026-11-15 CI run [#1601](https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/43672804984) passed all unit tests, lint, debug and release builds. The deliberately all-00 worst-case fixture measured these **single-run** times (four stages / 207 expression shapes):

| Input historical draws | Draws scored | GitHub runner elapsed |
| --- | ---: | ---: |
| 200 | 199 (one lag needed) | 117 ms |
| 500 | 200 | 63 ms |
| 1,000 | 200 | 70 ms |
| 3,000 | 200 | 100 ms |

- Signatures remained bounded at 4,407–4,473 characters; the same retained formulas were produced for 500, 1,000 and 3,000 input sizes.
- The separate synthetic research fixture had 1,044 eligible draws in its two-year period and 1,566 in its three-year period, **sampling 16 temporally spread targets for the unit test** (the actual research-mode default is 80).
- These timings are **not a mobile phone benchmark**, do not constitute a latency guarantee, and are not a controlled median across multiple warm runs.
- The subsequent optimization to allocate matched-draw lists **only for retained formulas** was committed *after* #1601, so any newer timing requires its own verified run.

## Remaining device and future-usage release gates

- **Android phone tests**: low-end / 2–4GB RAM device, performance under UI navigation, app background/foreground, quick repeated clicks, cancellation, rotation, battery saver and database imports. No Android profiling has yet proven the UI will never stutter.
- **Database queries**: when UI integration is authorized, query only **latest 203 settled slots** with indexed date/session sorting for normal discovery. The current domain caller may still hand the engine a large list, which requires O(N) scanning even though memory and formula evaluation are bounded.
- **Caching**: cache results by target + recent history + closed dates + grammar version; invalidate on real results corrections. Avoid storing or recomputing large duplicates.
- **Research audit**: always run off main thread, limit sample count, expose actual sample coverage, and cancel when the requesting screen leaves.
- **Source quality**: known duplicated, absent and weekend-dated rows must remain separately auditable. Unknown gaps are not automatically market closures.
- **Scalability**: run synthetic 5k–10k and real-device tests before broader release; 3,000 passed only when current GitHub CI benchmark confirms it.

No DOC formula, Gemini research variant, current UI screen or operational winner/ledger record was changed in this workstream.
