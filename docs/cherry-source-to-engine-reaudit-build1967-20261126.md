# Source-to-engine re-audit — Build 1967 (2026-11-26)

Baseline fc7ff8a6b69194ce44d9e2935ad8bd1762b15f45 on feature/cherry-rule-coverage-from-build1945-20261125. CI run 45640177119 completed success; debug/release artifacts 15035652278/15035587483. All five partial implementation builds 1963–1967 passed. Reviewed actual reader JSON, calculation engine, clarification docs, scoring bridge and snapshot logic. Static source coverage audit, not exhaustive runtime tests or evidence of forecasting accuracy. Original image sources not re-inspected; use stored reader summaries.

Counts: 98 unique reader entries; 78 represented in calculation/cuts (including entries with limited approved branches or documented interpretation gaps), 18 excluded by user, 2 descriptive. R = represented, NOT fully certified; E = intentionally excluded; D = descriptive. No reader deletion. All 50 prior partial dispositions are addressed; this audit does not certify every edge case or source score.

| Source ID | Title | Status | Engine identity | Scope / observation |
|---|---|---|---|---|
| cherry-001 | ထိပ်တူ / ပိတ်တူမူ | R | s1-day-head-tail | Current following calendar day AM/PM, same week; union of alternatives |
| cherry-002 | ဘရိတ်တူမူ | R | s1-day-break | Current following day same week, exact observed repeat union |
| cherry-003 | ဘေးတစ်စောင်း ဘရိတ်တူမူ | R | s1-day-diagonal | Current following day same week; diagonal modulo equality |
| cherry-004 | Modern / Internet ထိပ်တူ၊ ပိတ်တူမူ | R | s1-930 | Current AM independent 9:30, digit zero or double |
| cherry-005 | Modern / Internet ဘရိတ်တူမူ | R | s1-930bk-v2 | Current AM independent 9:30, exact same-week repeat union |
| cherry-006 | အလယ်သတ်ကွင်း အဓိပ္ပာယ် | D | — | Definition only, no calculation output specified |
| cherry-007 | အလယ်အထက်/အောက် ဘရိတ်တူလက္ခဏာ | D | — | Definition only, no calculation output specified |
| cherry-008 | ဂဏန်း 0–9 အလိုက် ဇယား | R | s2-p-*, s2-h-*, s2-f-*, s2-return-*, s2-tail6-49, s2-8head, +7 cut | All columns represented; first-return next-slot default and friend/head alternatives unresolved; cut scoring separate |
| cherry-009 | ပါဝါထဲမှ (16 - 94 - 83) ကျခဲ့ပါက | R | s3-ten | Current immediate; modulo-zero includes 00, source says ဆယ်ကျ; verify intended exact ten vs zero break |
| cherry-010 | ပါဝါထဲမှ (50 - 27) ကျခဲ့ပါက | R | s3-w-010 | Same-week literal triggers and direct siblings |
| cherry-011 | ညီအစ်ကိုထဲမှ (12 - 65 - 32) ကျခဲ့ပါက | R | s3-w-011 | Same-week direct power + exact sum ten |
| cherry-012 | ညီအစ်ကိုထဲမှ (34 - 45 - 67 - 78 - 89) ကျခဲ့ပါက | R | s3-w-012 | Same-week direct astrology, shared family with 016 |
| cherry-013 | နက္ခတ်ထဲမှ (35 - 81) ကျခဲ့ပါက | R | s3-w-013 | Same-week doubles |
| cherry-014 | အပူးအလိုက် | R | s3-w-014 | Same-week doubles, separate identity |
| cherry-015 | ညီအစ်ကိုအလိုက် | R | s3-w-015 | Same-week direct siblings after double |
| cherry-016 | နက္ခတ်အလိုက် | R | s3-w-016 | Same-week direct sibling trigger, direct astrology; shared family with 012 |
| cherry-017 | ၉ ဘရိတ် နှင့် ၇ ဘရိတ် အလိုက် | R | s3-w-017-9, s3-w-017-7 | Same-week full arithmetic breaks |
| cherry-018 | နောက်ဆက်တွဲ အလိုက်ဂဏန်းများ | E | — | Excluded by user; reader preserved |
| cherry-019 | ၆ ကွက် ဆတိုးအုပ်စုမူ | R | s3-six | Weekly distinct follow-ups, ends after third additional distinct hit; whole-window score pending |
| cherry-020 | အပတ်စဉ် အထွက်အများဆုံး ဂဏန်းများ | R | s3-week | Weekly literal set remaining; whole-window score pending |
| cherry-021 | အပူး 00–99 အပြီးခွေနှင့် မွေးဂဏန်းဇယား | R | s4-wheel-*, s4-born-* | Same-week all ten rows, marked R groups expanded; source cross-listed two score keys |
| cherry-022 | ခဲထွက်ရက် အလယ်ရက်နေ့တွင် အပူးဆင်းခဲ့လျှင် | E | — | Excluded by user; reader preserved |
| cherry-023 | အလယ်အပူးမူ (44 အပူးမူ) | R | s4-mid44 | Same-week subset; shared s4-born-44 evidence family |
| cherry-024 | တနင်္လာနေ့ 1 နှစ်လုံး/သုံးလုံး မူ | R | s5-mon-ones | Monday both sessions, 2/3 occurrences, remainder of week |
| cherry-025 | တနင်္လာနေ့တွင် (9) လာပါက | R | s5-mon-nine | Monday either session, same-week doubles |
| cherry-026 | တနင်္လာနေ့တွင် (3) လာပါက | R | s5-mon-three | Monday either session, chronological exact repeat watch |
| cherry-027 | တနင်္လာနေ့တွင် (5) လာပါက | R | s5-mon-five-break, s5-mon-five-digit | Same-week zero-break union and Tue–Fri digit branch |
| cherry-028 | တနင်္လာမနက် ဘရိတ်အလိုက်ဇယား | R | s5-mon-[0-9] | Monday AM three independently retiring breaks |
| cherry-029 | ဗုဒ္ဓဟူးနေ့ ညနေထွက်ဂဏန်း အပူးဖြစ်ခဲ့လျှင် | R | s5-wed | Wednesday PM double, next four scheduled slots capped following Wed; examples not separately weighted |
| cherry-030 | သောကြာနေ့ (မနက် Or ညနေ) ပါဝါထွက်ခဲ့လျှင် | R | s5-fri | Prior Friday power, next Mon–Fri; literal outputs, named group both directions |
| cherry-031 | ပိတ်ရက်ရှိတဲ့ တစ်ပတ်အတွင်း (ရက်တိုအပတ်) | E | — | Excluded by user; reader preserved |
| cherry-032 | (01) / (01) R ထွက်လျှင် | R | s6-01-68, s6-01-46 | Same-week independent literal/explicit R triggers |
| cherry-033 | (04) ထွက်လျှင် | R | s6-04 | Same-week literal |
| cherry-034 | (06) R ထွက်လျှင် | R | s6-06 | Four scheduled sessions, named siblings both directions; stop-on-hit default |
| cherry-035 | (07) / (70) R ထွက်လျှင် | R | s6-07, s6-70 | Same-week distinct literal 07 and 70 triggers; power output R |
| cherry-036 | (09) ထွက်လျှင် | R | s6-09 | Same-week literal |
| cherry-037 | (10) R ထွက်လျှင် | R | s6-10 | Next scheduled session, explicit R trigger |
| cherry-038 | (12) R ထွက်လျှင် | R | s6-12-digit, s6-12, s6-12-extra, s6-12-wheel | Four independent same-week branches |
| cherry-039 | (14) ထွက်လျှင် | R | s6-14 | Same-week literal |
| cherry-040 | (17) ထွက်လျှင် | R | s6-17 | Same-week literal |
| cherry-041 | (23) ထွက်လျှင် | R | s6-23 | Same-week literal |
| cherry-042 | (27) / (27) R ထွက်လျှင် | R | s6-27, s6-27-extra | Same-week literal direct branch and R zero-head/break branch |
| cherry-043 | (43) R ထွက်လျှင် | R | s6-43 | Same-week explicit R input, literal output |
| cherry-044 | (49) ထွက်လျှင် | R | s6-49 | Same-week literal, separate 057 |
| cherry-045 | (54) ထွက်လျှင် | R | s6-54 | Same-week literal |
| cherry-046 | (57) R ထွက်လျှင် | R | s6-57 | Immediate R input, wheel includes doubles |
| cherry-047 | (59) ထွက်လျှင် | R | s6-59, s6-59-repeat | Same-week direct and chronological exact repeat; source percentage not measured |
| cherry-048 | (64) ထွက်လျှင် | R | s6-64 | Same-week literal |
| cherry-049 | (65) ထွက်လျှင် | R | s6-65 | Same-week literal |
| cherry-050 | (69) ထွက်လျှင် | R | s6-69 | Same-week literal |
| cherry-051 | 78 ထွက်လျှင် — ပါဝါပဒေသာမူ | R | s6-78 | Same-week literal trigger, full R output group |
| cherry-052 | (79) R ထွက်လျှင် | R | s6-79 | Same-week R trigger, literal outputs |
| cherry-053 | (83) R ထွက်လျှင် | R | s6-83 | Same-week R trigger, literal output |
| cherry-054 | (84) R ထွက်လျှင် | R | s6-84 | Same-week R trigger, literal outputs |
| cherry-055 | (89) ထွက်လျှင် | R | s6-89 | Same-week literal |
| cherry-056 | (92) ထွက်လျှင် | R | s6-92, s6-am92 | Weekly literal born plus same-day AM-to-PM break, special examples in detail |
| cherry-057 | (94) R ထွက်လျှင် | R | s6-94 | Explicit R trigger, five calendar days |
| cherry-073 | နောက်ပိတ်အလိုက် အသေတွဲဇယား | R | deadTriples / cut map | All ten rows represented, prospective cut score absent |
| cherry-074 | 00 အပူး နှင့် ၅ ဘရိတ်မူ | R | s8-00-bk | Five calendar days, exact five source pairs |
| cherry-075 | 0 နှင့် 1 သတိထားရန်အတွဲ | E | — | Excluded by user; reader preserved |
| cherry-076 | 80 R ထွက်လျှင် အပြီးခွေမူ | R | s8-80 | Same-week Born only, R input, 16-pair wheel |
| cherry-077 | 67 R ထွက်လျှင် အပြီးခွေမူ | R | s8-67 | Same-week Born only, R input, 16-pair wheel |
| cherry-078 | 0 ထွက်လျှင် ပတ်သီး/ပိတ်မူ | R | s8-zero-follow | Immediate any zero trigger |
| cherry-079 | 74 (R) ထွက်လျှင် ပတ်သီး/ပိတ်မူ | R | s8-74 | Immediate R trigger |
| cherry-080 | 48 (R) ထွက်ပြီး ပတ်သီး/ပိတ်မူ | R | s8-48 | Immediate R trigger |
| cherry-081 | 71 (R) ထွက်ပြီး ပတ်သီး/ပိတ်မူ | R | s8-71 | Immediate R trigger |
| cherry-082 | 15 (R) ထွက်ပြီး ပတ်သီး/ပိတ်မူ | R | s8-15 | Immediate R trigger |
| cherry-083 | 88 ပူးထွက်ပြီး ပတ်သီး/ပိတ်မူ | R | s8-88 | Immediate literal double |
| cherry-084 | 13 (R) ထွက်လျှင် ပတ်သီး/ပိတ်မူ | R | s8-13 | Immediate R trigger |
| cherry-085 | ညနေပိုင်း 4 ထိပ်မူ | R | s8-evening-four | Next calendar morning only; weekend/holiday next scheduled morning interpretation unresolved |
| cherry-086 | တနင်္လာမနက် 7 ထိပ်မူ | R | s8-mon-seven | Approved Monday AM branch only; other two excluded |
| cherry-087 | ကြာသပတေး/သောကြာ ထိပ်ပေါင်း ပါဝါတွဲမူ | R | s8-thufri-power | Prior Thu/Fri AM heads; next week R output; named power orientation retained |
| cherry-088 | တနင်္လာ–သောကြာ ပါဝါမူ | E | — | Excluded by user; reader preserved |
| cherry-089 | WaiLin 2D နေ့စဉ် တစ်လုံးပိုင်မူ | R | s9-wailin | Daily modulo sum, following calendar day same week; daily vs entire-week ambiguity |
| cherry-090 | ဂဏန်း 1–5 တွဲဖက်ပတ်သီးမူ | E | — | Excluded by user; reader preserved |
| new-mob-double | လူမိုက်ကြီးအပူး | R | new-mob-double | Same-week literal group, independent doubles |
| new-mob-wheel | လူမိုက်အခွေ | R | new-mob-wheel | Same-week approved group, independent non-double wheel |
| new-bk-50 | 5/0 ဘရိတ်ကြီးမူ | E | — | Excluded by user; reader preserved |
| new-special-9072 | အလယ်နက္ခတ်မူ | E | — | Excluded by user; reader preserved |
| new-special-3103 | အလယ်သီးအိုက်မူ | E | — | Excluded by user; reader preserved |
| new-born-75 | 75 ပြန်ထွက်မူ | R | new-born-75 | AM literal; following two calendar dates only |
| new-bk-0chain | 0 လက်နှင့် အလယ်ပူးဘရိတ်မူ | E | — | Excluded by user; reader preserved |
| new-double-chain | အပူးထွက်ပြီး ပေါင်းခြင်းကပ်မူ | E | — | Excluded by user; reader preserved |
| new-bk-evening7 | ညနေ 7 ပိတ်မူ | R | new-bk-evening7 | Same-week union, stop-on-hit default |
| new-bk-six | 6 ဘရိတ်အခွေမူ | R | new-bk-six | Immediate arithmetic break trigger, non-double wheel |
| new-session-match | ည/မနက် ဘရိတ်တူနှင့် အလယ်ကွက်တူမူ | E | — | Excluded by user; reader preserved |
| new-day-mon | တနင်္လာနေ့ဆက်စပ်မူ | R | new-day-mon-b4/t6/79/89 | Both sessions same week; last-tail equality branch pending, not implemented |
| new-day-tue | အင်္ဂါနေ့ဆက်စပ်မူ | R | new-day-tue-b3/56/h5/b0/b4 | Both sessions same week |
| new-day-wed | ဗုဒ္ဓဟူးနေ့ဆက်စပ်မူ | R | new-day-wed-b9/b5/t6 | Approved three branches same week; fourth excluded |
| new-day-thufri | ကြာသပတေး/သောကြာ ဆက်စပ်မူ | E | — | Excluded by user; reader preserved |
| new-day-fri | သောကြာနေ့ဆက်စပ်မူ | E | — | Excluded by user; reader preserved |
| new-bk-19r | 19R နောက် 1 ဘရိတ်မူ | R | new-bk-19r | Explicit R trigger, five calendar days |
| new-born-50 | 50 နောက် 3/8 ထိပ်မူ | R | new-born-50 | Immediate literal trigger, heads union |
| new-groups-j | မြန်မာပါဝါ J မှ နက္ခတ်မူ | E | — | Excluded by user; reader preserved |
| new-born-return1 | ပြန်ကျလက်နှင့် 1 ပိတ်မူ | E | — | Excluded by user; reader preserved |
| new-special-967197 | 96R၊ 71R၊ 97R ဆက်စပ်မူ | E | — | Excluded by user; reader preserved |
| new-special-khant | 2D Ko Khant ဆက်စပ်မူ | E | — | Excluded by user; reader preserved |
| new-groups-weekly | အပတ်စဉ်မပျောက်တတ်သည့် မူများ | R | new-groups-weekly-nine/break/wheel | Three independent weekly watches, first-hit retirement default |

## Findings to resolve before design
1. Score versioning: snapshot sourceVersion remains cherry-better-data-v3.2-success-v1 after substantial calculation changes. Legacy reused IDs (e.g. s4-born-11/44, s6-*, s8-80/67) can blend different rules/scopes in the same canonical score. s1-930bk-v2 and s3-w-* do not map to current canonical bridge; old s1-930bk/s3-astro still do. Preserve historical snapshots; do not manufacture new evidence. Versioned IDs or explicit compatible version gate required, not mapping old evidence to revised rules blindly.
2. Lifetime vs per-draw scoring: bridge groups canonical ID per draw, unions predictions, and counts any hit once. This is draw-level membership, not a weekly/4-slot birth episode outcome. Weekly watches and episode retirement must have separate semantics. Rank currently groups by full origin-suffixed ID, not a stable formula version, so repeated births do not automatically aggregate as one source. Do not count unrecorded draws as misses. No retroactive scores. Clarify metric labels.
3. Cut score: snapshot enum has PAIR/LONE/WAM/BREAK only; createSnapshot filters out cut picks, so neither +7 nor deadTriples prospective exclusion scores are independently recorded. Add separately typed prospective evidence if approved, one criterion per draw, preserve original snapshots; winning inside excluded set = failed exclusion, outside = correct exclusion. Raw vs uncut offered-pair score must be explicit; current snapshot captures uncut offered pairs, not full raw rule membership.
4. Source display: 008 friend column uses two literal permutations rather than independent digit follows; user explicitly confirmed digit follows for 6-tail 4/9 only, so do not generalize without confirmation. 009 legacy modulo-zero includes 00 while 011 exact sum-ten excludes 00: source term differs (ဆယ်ကျ vs ဆယ်ပြည့်), verify distinction. 085 following morning uses previous calendar date only; closure/weekend interpretation unresolved. 089 daily vs full-week persistence, 008 first-return one session and stop-on-hit defaults, named group orientation (034/085/087 vs direct groups 010–016), and Monday equality branch are documented ambiguities, not newly confirmed requirements. Do not silently change. 086 and new-day-wed omitted branches as approved.
5. 029 special examples not separately surfaced by engine detail; source still accessible. 056 and 074 special examples are present in detail/wording; no duplicate evidence weight. UI first view still old structure until design phase. Existing 2D Analysis reader, Home, LIVE, Research and Math unchanged in Build 1956→1967 compare.
6. Data hygiene: engine filters closed dates, valid digit and before-target but not explicitly non-working weekends; duplicate slot resolves first row. Normal source may already sanitize; verify actual input contract, conflicting duplicates, holiday coverage, and missing slot behavior in next QA. Historical tests passing does not prove device speed. Calculate currently sorts/dedups raw history and scans rows repeatedly; profile on representative history without triggering Math engine.
7. Evidence-family dedup for 012/016 and 021/023 exists in ranking, not canonical score mapping; ensure score aggregation distinct per approved metric. Weekly counts and source identity remain separate.
8. Earlier summary said 22 formerly missing retained: 20 clarified + 2 initial repair = 22; 7 initially represented + 49 partial retained + 22 = 78. Full coverage of approved source interpretation still conditional on items above, and excluded entries remain readable.

## Next gate
- Freeze this 1967 audit and keep code unchanged during audit.
- Address clear scoring identity/version and cut-score gaps in small tested batches, with distinct prospective metrics; settle interpretation gaps before changing affected formulas.
- Check actual pre-result capture, missing history, cutoff, per-source grouping, closure cases and phone performance. No automatic Math discovery.
- Only then premium Current/Born design per approved plan.
