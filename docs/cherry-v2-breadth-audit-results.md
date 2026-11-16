# Cherry V2 — 64 vs 256 Formula Breadth: Verified Historical Audit

## Reproducible source and run

- **Verified green results:** Android CI [#43789868174](https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/43789868174) (initial RAW-first 64/256 audit) and CI [#43790322131](https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/43790322131), commit `5762f1be19ad` (balanced-order 64/256 audit). **Both runs reported identical historical hits/denominators and 10-streak counts.** Both bands reached their formula-count cap on all 78 usable target contexts, with **0 scan timeouts**.
- GitHub-checked-in asset: `app/src/main/assets/history_seed.json`; **959** records through **2026-09-29**, normalized to **1,803** usable historical draw slots after one conflicting `Off`/numbers date was quarantined and repeated identical records deduplicated. Source is not a fresh live winner feed.
- **80** fixed, time-distributed target slots over the latest two years relative to the seed; **78** usable target histories per category.
- Per target/category, RAW and GUIDED independently scan **64** or **256** formula slots on the same *strictly pre-target* input. Freeze the longest first-found recent fit (same unchanged offset); replay its output on up to **four later archived results**. Those later pairs are used only for scoring, **never formula selection**.
- Audit label: `IMPORTED_SEED_HISTORICAL_REPLAY_NOT_PROSPECTIVE`. This is retrospective evaluation of known results, not a genuine timestamp-locked forecast or a guarantee of winnings.

## Observed next-four-draw historical replay

| Category | Chance reference | RAW 64 (hits / checked) | GUIDED 64 | RAW 256 | GUIDED 256 |
| --- | ---: | ---: | ---: | ---: | ---: |
| LONE / လုံးပိုင် | 19.00% | 50/287 (17.42%) | 52/280 (18.57%) | 55/302 (18.21%) | 44/306 (14.38%) |
| WAM / ဝမ်းချိန်း | 36.00% | 105/301 (34.88%) | 108/306 (35.29%) | 111/306 (36.27%) | 115/306 (37.58%) |
| BREAK / ဘရိတ် | 10.00% | 12/110 (10.91%) | 11/110 (10.00%) | 21/232 (9.05%) | 26/231 (11.26%) |

Rates are conditional on a frozen formula being found and a scorable later result. The denominators differ: **a larger reported rate is not automatically a superior prediction policy**. Missing-history abstentions and failure-to-select cases need to be judged alongside these rates.

### Other concrete outcomes

- **RAW vs GUIDED distinct historical behavior traces:** at 64 candidates LONE 3,577 vs 3,885, WAM 3,619 vs 3,951, BREAK 3,574 vs 3,871; at 256 candidates LONE 11,816 vs 12,861, WAM 12,091 vs 13,319, BREAK 11,723 vs 12,721. Guided examined a more diverse candidate set under the same cap.
- **10+ consecutive in-sample fits per 80 samples:** only WAM yielded any: RAW 64 = **2**, GUIDED 64 = **3**, RAW 256 = **3**, GUIDED 256 = **3**. LONE and BREAK = **0** under both caps and scan orders. These are retrospective fits only.
- Guided LONE later-draw hit rate **fell** from 18.57% to 14.38% as search breadth increased; WAM and BREAK increased slightly. No tested configuration demonstrates a robust, repeatable advantage over the category-specific chance references.
- The initial 64/256 run executed RAW before GUIDED. Its follow-up **balanced-order** run alternated which scanner ran first by target and **passed CI with the same hit totals**, reducing JVM warm-up/order confounding. Because **all candidate-count caps were reached**, the observed hit differences reflect enumeration breadth rather than an elapsed-time cutoff. Benchmark JVM elapsed milliseconds are not Android CPU/RAM/thermal measurements.

## Practical decision

**Do not enable automatic Champion, promote a historical 10-streak as a forecast, or change the approved production search schedule based on these results.** There is no evidence of a reliable prospective edge. Keep the V2 engine isolated until further independent validation.

Further acceptance gates: (1) truly separated/newer time-window replay and ideally timestamp-locked prospective records, (2) 30-second real-phone responsiveness/heat/CPU/RAM/cancel/resume QA, and (3) explicit UI/Champion behavior approval. Balanced-order CI has now been verified green.

The tested formula space is larger than the legacy finite 207 shapes, but **more searched formulas do not by themselves produce better predictions**.
