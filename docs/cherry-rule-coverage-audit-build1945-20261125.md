# Better Data source coverage audit — Build 1945

Baseline 3326064b554bd9317a93eac25f9763a6a8e81f3a. Compared 98 canonical entries in two_d_rules_reader.json with CherryBetterDataEngine.kt and legacy two_d_rules_source.txt. Added image entries are editorial summaries; original images were not re-inspected in this audit. Cross-listed entries counted once. This is a static audit, not a runtime or predictive validation. Status R = represented in engine (not certified source-complete or predictive); P = partly represented / known interpretation, horizon, or identity gap; M = missing standalone calculation; D = descriptive reference, not a computable rule as written. Existing scoring bridge is a separate concern; never equate mapping with full coverage. Counts: M=39, R=7, P=50, D=2.

| ID | Reader title | Status | Findings / action |
|---|---|---|---|
| cherry-001 | ထိပ်တူ / ပိတ်တူမူ | M | No AM/PM equal head/tail chain; clarify session/horizon and fallback transition. |
| cherry-002 | ဘရိတ်တူမူ | M | No AM/PM equal-break chain; clarify next phase, reverse semantics. |
| cherry-003 | ဘေးတစ်စောင်း ဘရိတ်တူမူ | M | No diagonal-break condition; define exact cross-session comparison. |
| cherry-004 | Modern / Internet ထိပ်တူ၊ ပိတ်တူမူ | R | s1-930; morning 9:30 data, zero/doubles. |
| cherry-005 | Modern / Internet ဘရိတ်တူမူ | P | s1-930bk; verify ပြန်ကျ meaning and actual input origin; currently reverses Modern/Internet. |
| cherry-006 | အလယ်သတ်ကွင်း အဓိပ္ပာယ် | D | Definition of middle four digits; no specified output. |
| cherry-007 | အလယ်အထက်/အောက် ဘရိတ်တူလက္ခဏာ | D | Descriptive resemblance, no explicit output; requires middle data. |
| cherry-008 | ဂဏန်း 0–9 အလိုက် ဇယား | P | s2-p/h/f, +7 cut, s2-8head; missing vanished-return column and 6-ending four-draw follow; confirm absence threshold and grouped heads. |
| cherry-009 | ပါဝါထဲမှ (16 - 94 - 83) ကျခဲ့ပါက | R | s3-ten; explicit triggers and ten-sum group. |
| cherry-010 | ပါဝါထဲမှ (50 - 27) ကျခဲ့ပါက | P | s3-sib shared with 015; distinguish source identity. |
| cherry-011 | ညီအစ်ကိုထဲမှ (12 - 65 - 32) ကျခဲ့ပါက | P | s3-power emits only power; missing ten-sum branch. |
| cherry-012 | ညီအစ်ကိုထဲမှ (34 - 45 - 67 - 78 - 89) ကျခဲ့ပါက | P | Generic s3-astro covers listed siblings but lacks separate source ID. |
| cherry-013 | နက္ခတ်ထဲမှ (35 - 81) ကျခဲ့ပါက | P | s3-double shared with 014. |
| cherry-014 | အပူးအလိုက် | P | s3-double shared with 013. |
| cherry-015 | ညီအစ်ကိုအလိုက် | P | s3-sib shared with 010. |
| cherry-016 | နက္ခတ်အလိုက် | P | s3-astro; audit direct/reverse scope and overlap with 012. |
| cherry-017 | ၉ ဘရိတ် နှင့် ၇ ဘရိတ် အလိုက် | P | s3-9to7, s3-7to4; verify listed direct/reverse and special 22/77 coverage. |
| cherry-018 | နောက်ဆက်တွဲ အလိုက်ဂဏန်းများ | M | No follow-up group signal; specify exact preceding group membership. |
| cherry-019 | ၆ ကွက် ဆတိုးအုပ်စုမူ | P | s3-six weekly remaining group; original 2–3 distinct follow-up hits not modeled. |
| cherry-020 | အပတ်စဉ် အထွက်အများဆုံး ဂဏန်းများ | P | s3-week; weekly set represented but whole-week evaluation absent. |
| cherry-021 | အပူး 00–99 အပြီးခွေနှင့် မွေးဂဏန်းဇယား | P | s4-wheel and s4-born; audit every table row, R and existing five-trading-day policy. |
| cherry-022 | ခဲထွက်ရက် အလယ်ရက်နေ့တွင် အပူးဆင်းခဲ့လျှင် | M | Middle day of three-digit draw week undefined; two-day horizon explicit once trigger clarified. |
| cherry-023 | အလယ်အပူးမူ (44 အပူးမူ) | P | 44 double-born table contains some outputs; separate middle-double condition and R/horizon absent. |
| cherry-024 | တနင်္လာနေ့ 1 နှစ်လုံး/သုံးလုံး မူ | M | Monday full day 1 count and remaining-week horizon missing. |
| cherry-025 | တနင်္လာနေ့တွင် (9) လာပါက | M | Monday 9 digit trigger; duration not explicit. |
| cherry-026 | တနင်္လာနေ့တွင် (3) လာပါက | M | Monday 3 digit trigger; clarify ပြန်ကျ and duration. |
| cherry-027 | တနင်္လာနေ့တွင် (5) လာပါက | M | Monday 5 trigger; distinct zero-break/ten-sum and four remaining days digit-5 branch. |
| cherry-028 | တနင်္လာမနက် ဘရိတ်အလိုက်ဇယား | P | s5-mon; five trading days may extend beyond source week. |
| cherry-029 | ဗုဒ္ဓဟူးနေ့ ညနေထွက်ဂဏန်း အပူးဖြစ်ခဲ့လျှင် | P | s5-wed; source remaining four draws / next Wednesday, not generic five trading days. |
| cherry-030 | သောကြာနေ့ (မနက် Or ညနေ) ပါဝါထွက်ခဲ့လျှင် | P | s5-fri; output set present, expiry inferred rather than given. |
| cherry-031 | ပိတ်ရက်ရှိတဲ့ တစ်ပတ်အတွင်း (ရက်တိုအပတ်) | M | Short week holiday condition, R pairs; no engine signal. |
| cherry-032 | (01) / (01) R ထွက်လျှင် | P | s6-01; reverse trigger/branch and born duration missing. |
| cherry-033 | (04) ထွက်လျှင် | P | s6-04; direct pair present; source born duration not specified. |
| cherry-034 | (06) R ထွက်လျှင် | M | 06R -> siblings for four draws absent. |
| cherry-035 | (07) / (70) R ထွက်လျှင် | P | s6-07, s6-70; distinguish 07 direct and 70R branch, horizon. |
| cherry-036 | (09) ထွက်လျှင် | P | s6-09; current-only despite born wording. |
| cherry-037 | (10) R ထွက်လျှင် | M | 10R -> 7 break absent. |
| cherry-038 | (12) R ထွက်လျှင် | P | s6-12 and wheel; missing digit-1 and 80/35 R, horizon. |
| cherry-039 | (14) ထွက်လျှင် | P | s6-14; current-only despite born wording. |
| cherry-040 | (17) ထွက်လျှင် | P | s6-17; current-only despite born wording. |
| cherry-041 | (23) ထွက်လျှင် | P | s6-23; current-only despite born wording. |
| cherry-042 | (27) / (27) R ထွက်လျှင် | P | s6-27 direct; missing 27R zero head / eight break. |
| cherry-043 | (43) R ထွက်လျှင် | P | s6-43; missing reverse input. |
| cherry-044 | (49) ထွက်လျှင် | P | s6-49; current-only despite born wording. |
| cherry-045 | (54) ထွက်လျှင် | P | s6-54; current-only despite born wording. |
| cherry-046 | (57) R ထွက်လျှင် | M | 57R -> 357 ambiguous expansion and horizon. |
| cherry-047 | (59) ထွက်လျှင် | P | s6-59; missing reverse-result branch and born horizon. |
| cherry-048 | (64) ထွက်လျှင် | P | s6-64; current-only despite born wording. |
| cherry-049 | (65) ထွက်လျှင် | P | s6-65; current-only despite born wording. |
| cherry-050 | (69) ထွက်လျှင် | P | s6-69; current-only despite born wording. |
| cherry-051 | 78 ထွက်လျှင် — ပါဝါပဒေသာမူ | P | s6-78; eight direct outputs, missing R outputs. |
| cherry-052 | (79) R ထွက်လျှင် | P | s6-79; reverse trigger and born duration missing. |
| cherry-053 | (83) R ထွက်လျှင် | P | s6-83; reverse trigger missing. |
| cherry-054 | (84) R ထွက်လျှင် | P | s6-84; reverse trigger and born duration missing. |
| cherry-055 | (89) ထွက်လျှင် | P | s6-89; current-only despite born wording. |
| cherry-056 | (92) ထွက်လျှင် | P | s6-92, s6-am92; only two example pairs for 7 break; 90 born scope missing. |
| cherry-057 | (94) R ထွက်လျှင် | M | 94R digit 1 within five days absent. |
| cherry-073 | နောက်ပိတ်အလိုက် အသေတွဲဇယား | R | deadTriples six exact non-double pairs; no prospective exclusion scoring. |
| cherry-074 | 00 အပူး နှင့် ၅ ဘရိတ်မူ | P | s8-00-bk; direct pairs only, five working days vs plain five days. |
| cherry-075 | 0 နှင့် 1 သတိထားရန်အတွဲ | M | 0/1 see-condition missing; clarify which observation is trigger. |
| cherry-076 | 80 R ထွက်လျှင် အပြီးခွေမူ | P | s8-80 current and born; reverse trigger and expiry unclear. |
| cherry-077 | 67 R ထွက်လျှင် အပြီးခွေမူ | P | s8-67 current and born; reverse trigger and expiry unclear. |
| cherry-078 | 0 ထွက်လျှင် ပတ်သီး/ပိတ်မူ | M | Separate 0 -> 7 digit rule absent; +7 cut is a different rule; confirm digit trigger scope. |
| cherry-079 | 74 (R) ထွက်လျှင် ပတ်သီး/ပိတ်မူ | P | s8-74; reverse trigger missing. |
| cherry-080 | 48 (R) ထွက်ပြီး ပတ်သီး/ပိတ်မူ | P | s8-48; reverse trigger missing. |
| cherry-081 | 71 (R) ထွက်ပြီး ပတ်သီး/ပိတ်မူ | P | s8-71; reverse trigger missing. |
| cherry-082 | 15 (R) ထွက်ပြီး ပတ်သီး/ပိတ်မူ | P | s8-15; reverse trigger missing. |
| cherry-083 | 88 ပူးထွက်ပြီး ပတ်သီး/ပိတ်မူ | R | s8-88; digit 3 anywhere. |
| cherry-084 | 13 (R) ထွက်လျှင် ပတ်သီး/ပိတ်မူ | P | s8-13; reverse trigger missing. |
| cherry-085 | ညနေပိုင်း 4 ထိပ်မူ | M | Evening 4 head -> power or astrology absent. |
| cherry-086 | တနင်္လာမနက် 7 ထိပ်မူ | M | Monday AM 7-head; three separate day conditions / duration. |
| cherry-087 | ကြာသပတေး/သောကြာ ထိပ်ပေါင်း ပါဝါတွဲမူ | M | Thu AM and Fri AM head combination; next-week R group absent. |
| cherry-088 | တနင်္လာ–သောကြာ ပါဝါမူ | M | Mon AM / Fri PM head condition; next-week sibling/double 2–3 hit horizon absent. |
| cherry-089 | WaiLin 2D နေ့စဉ် တစ်လုံးပိုင်မူ | M | Yesterday AM head + PM head + PM tail, daily digit; clarify previous calendar vs trading date / mod 10. |
| cherry-090 | ဂဏန်း 1–5 တွဲဖက်ပတ်သီးမူ | M | Digit 1–5 follow table absent; confirm last result digit scope. |
| new-mob-double | လူမိုက်ကြီးအပူး | P | Present; source says progressive follow, exact expiry unspecified. |
| new-mob-wheel | လူမိုက်အခွေ | M | Missing separate အပေါက်လူမိုက် trigger membership; not same group. |
| new-bk-50 | 5/0 ဘရိတ်ကြီးမူ | M | Need precise large-break condition and 2/12, 11 semantics. |
| new-special-9072 | အလယ်နက္ခတ်မူ | M | Need middle-digit input definition and trigger. |
| new-special-3103 | အလယ်သီးအိုက်မူ | M | Need middle-digit trigger and branch selection. |
| new-born-75 | 75 ပြန်ထွက်မူ | P | Present; verify ပြန်ထွက် means exact 75 vs reversed/repeated. |
| new-bk-0chain | 0 လက်နှင့် အလယ်ပူးဘရိတ်မူ | M | Need two-of-group observation window and reverse semantics. |
| new-double-chain | အပူးထွက်ပြီး ပေါင်းခြင်းကပ်မူ | M | Need chain arithmetic/phase beyond examples. |
| new-bk-evening7 | ညနေ 7 ပိတ်မူ | M | Evening 7-tail trigger known, unspecified subsequent-day horizon. |
| new-bk-six | 6 ဘရိတ်အခွေမူ | R | Present; source correction 1649, non-double wheel. |
| new-session-match | ည/မနက် ဘရိတ်တူနှင့် အလယ်ကွက်တူမူ | M | Need middle data and exact comparison/phase. |
| new-day-mon | တနင်္လာနေ့ဆက်စပ်မူ | M | Several explicit conditions but target session/horizon and last-tail equality unclear. |
| new-day-tue | အင်္ဂါနေ့ဆက်စပ်မူ | M | Several explicit conditions but target session/horizon unclear. |
| new-day-wed | ဗုဒ္ဓဟူးနေ့ဆက်စပ်မူ | M | Target session/horizon; 5 three times condition unclear. |
| new-day-thufri | ကြာသပတေး/သောကြာ ဆက်စပ်မူ | M | Need exact Thu/Fri tail relation and branch selection. |
| new-day-fri | သောကြာနေ့ဆက်စပ်မူ | M | Target session/horizon; some branch semantics unclear. |
| new-bk-19r | 19R နောက် 1 ဘရိတ်မူ | R | Present 19/91; five calendar days, break 1. |
| new-born-50 | 50 နောက် 3/8 ထိပ်မူ | R | Present 50 -> 3/8 head, example expansion; current-only policy. |
| new-groups-j | မြန်မာပါဝါ J မှ နက္ခတ်မူ | M | Need definition of J and abundance threshold. |
| new-born-return1 | ပြန်ကျလက်နှင့် 1 ပိတ်မူ | M | Need exact return-hand condition/phase. |
| new-special-967197 | 96R၊ 71R၊ 97R ဆက်စပ်မူ | M | Some explicit pair mappings; verify source direction, phase, 20/02 and 14679 conditions. |
| new-special-khant | 2D Ko Khant ဆက်စပ်မူ | P | Only explicit 98/11/91 -> zero head; 40R and other branches missing. |
| new-groups-weekly | အပတ်စဉ်မပျောက်တတ်သည့် မူများ | M | Weekly observation statements; define week scope and whole-week score, no guaranteed future outcome. |

## Completion gate
- Resolve ambiguous source terminology, trigger, output and horizon with user/source; never silently invent them. Definitions (D) may be represented as context detectors once inputs are available, not as standalone forecasts without outputs.
- Audit canonical-to-engine mapping independently of score mapping, including per-subrule identity and cross-listed 021. Exclusion rules are currently missing prospective exclusion score records. Separate whole-window outcome evaluation from per-draw membership. Do not retroactively count any new signal as a recorded forecast. Preserve snapshots.
- First repair batch may address explicit direct/reverse trigger omissions without changing original wording or archive. Every changed source must have positive, negative, and temporal tests. Subsequent batches cover time windows, missing input definitions, and source comparisons; no UI redesign until coverage review. No claim of 98/98 implementation until each row is resolved and validated.
