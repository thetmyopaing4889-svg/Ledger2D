# Missing rule resolution queue — Build 1946

Base: successful CI run 45573058158 at fb9cf6f7e515eed9e88cb4274fe2a329b7b56266. This is the 39 missing-rule subset of the original 98-entry audit, updated for batch 1: 2 now have a standalone calculation (037, 057); 37 remain. A passing CI does not establish source-completeness or future forecasting performance. Device acceptance pending. The 50 originally partial rules are tracked separately; batch 1 repaired R trigger omissions in 11, but those are not automatically complete. 2 descriptive entries (006,007) have no output as written. The 7 originally represented entries still require validation.

Root cause: the engine is a manually coded subset, not an interpreter of the library asset. Legacy missing entries were not implemented; some need new data/horizon capabilities. Added photo rules were explicitly deferred in earlier implementation notes due to uncertain source wording. Individual historical author intent cannot be proved from code; findings below are observed gaps, not inferred personal motives.

## A — Legacy omissions with sufficiently explicit source condition/output to prepare implementation (5)

| ID | Rule | Blocker / resolution |
|---|---|---|
| cherry-024 | တနင်္လာနေ့ 1 နှစ်လုံး/သုံးလုံး မူ | Monday full-day rule not implemented; needs both confirmed Monday sessions and remainder-of-week boundary; distinguish full-day condition from immediate draw. |
| cherry-031 | ပိတ်ရက်ရှိတဲ့ တစ်ပတ်အတွင်း (ရက်တိုအပတ်) | No short-week detector. Use confirmed trading calendar including weekday holidays; distinguish missing data from a holiday. Define week evaluation and R scope. |
| cherry-034 | (06) R ထွက်လျှင် | Existing born helper uses days, not draw slots. Need a four-scheduled-draw horizon with holiday handling, not four days; verify missing history and stop policy. |
| cherry-085 | ညနေပိုင်း 4 ထိပ်မူ | No session-specific 4-head signal. Source gives no duration; existing duration-free next-draw convention is an interpretation to document. |
| cherry-087 | ကြာသပတေး/သောကြာ ထိပ်ပေါင်း ပါဝါတွဲမူ | No cross-week input comparison. Need exact Thu/Fri AM results and next-week-only boundary; no substitution for absent session. Confirm how reverse pairs and window-level scoring work. |

## B — Legacy omissions requiring definition or timing clarification (15)

| ID | Rule | Blocker / resolution |
|---|---|---|
| cherry-001 | ထိပ်တူ / ပိတ်တူမူ | AM/PM matching chain absent. Resolve which dates/sessions, how long each stage stays active, and meaning of one-less digit at zero. |
| cherry-002 | ဘရိတ်တူမူ | AM/PM break matching chain absent. Resolve phase transition, expiry, and meaning of ပြန်ကျ. |
| cherry-003 | ဘေးတစ်စောင်း ဘရိတ်တူမူ | No diagonal break comparison; define exact four-digit comparison, input slots, and ပြန်ကျ. |
| cherry-018 | နောက်ဆက်တွဲ အလိုက်ဂဏန်းများ | No follow-up signal; define exactly which preceding groups qualify, and the output window. |
| cherry-022 | ခဲထွက်ရက် အလယ်ရက်နေ့တွင် အပူးဆင်းခဲ့လျှင် | No explicit implementation; ခဲထွက်ရက် အလယ်ရက်နေ့ undefined. Source specifies two days after qualifying double; clarify the trigger. |
| cherry-025 | တနင်္လာနေ့တွင် (9) လာပါက | Monday 9 trigger and doubles output stated, but born expiry and digit position scope unspecified. |
| cherry-026 | တနင်္လာနေ့တွင် (3) လာပါက | Monday 3 trigger stated; define ပြန်ကျ, digit position scope and born expiry. |
| cherry-027 | တနင်္လာနေ့တွင် (5) လာပါက | Monday 5 and two branches stated; confirm digit scope, first branch expiry, and remaining-four-day semantics. |
| cherry-046 | (57) R ထွက်လျှင် | 57R stated; 357 expansion and expiry not defined (do not assume wheel). |
| cherry-075 | 0 နှင့် 1 သတိထားရန်အတွဲ | 0/1 association lists stated, but which observation counts as seeing a digit and target window need definition. |
| cherry-078 | 0 ထွက်လျှင် ပတ်သီး/ပိတ်မူ | Separate 0→7 follow rule absent. Confirm 0 input position; not the same as +7 exclusion. |
| cherry-086 | တနင်္လာမနက် 7 ထိပ်မူ | Monday AM 7-head trigger and Thu/Fri follow conditions stated; define duration of first 3 branch, and weekday-specific scope. |
| cherry-088 | တနင်္လာ–သောကြာ ပါဝါမူ | Cross-week head sum rule absent. Clarify whether 0/5 means final digit of sum and whether 2–3 distinct hits or occurrences are required. |
| cherry-089 | WaiLin 2D နေ့စဉ် တစ်လုံးပိုင်မူ | Daily sum absent. Reader example totals 8; source does not cover sum above 9 or prior trading day versus calendar day; clarify before modulo implementation. |
| cherry-090 | ဂဏန်း 1–5 တွဲဖက်ပတ်သီးမူ | Table is explicit, but input digit observation scope, output digit-versus-pair semantics and validity window need confirmation. |

## C — Added photo-source omissions: recover original pages and resolve exact conditions (17)

| ID | Rule | Blocker / resolution |
|---|---|---|
| new-mob-wheel | လူမိုက်အခွေ | Original implementation notes say separate mob group's membership is not listed; recover group definition; do not reuse other mob group's trigger. |
| new-bk-50 | 5/0 ဘရိတ်ကြီးမူ | Need exact big-break definition and interpretation of 2/12, 11. |
| new-special-9072 | အလယ်နက္ခတ်မူ | Need middle-digit input source and trigger meaning. |
| new-special-3103 | အလယ်သီးအိုက်မူ | Need middle-digit trigger and branch semantics. |
| new-bk-0chain | 0 လက်နှင့် အလယ်ပူးဘရိတ်မူ | Need qualifying two-of-group window and R interpretation. |
| new-double-chain | အပူးထွက်ပြီး ပေါင်းခြင်းကပ်မူ | Examples alone do not define general chain transformation. |
| new-bk-evening7 | ညနေ 7 ပိတ်မူ | Trigger explicit; subsequent-day duration unclear. |
| new-session-match | ည/မနက် ဘရိတ်တူနှင့် အလယ်ကွက်တူမူ | Need middle data definition and match/phase rules. |
| new-day-mon | တနင်္လာနေ့ဆက်စပ်မူ | Some mappings explicit; check original target session and same-tail scope. |
| new-day-tue | အင်္ဂါနေ့ဆက်စပ်မူ | Some mappings explicit; check target session/window. |
| new-day-wed | ဗုဒ္ဓဟူးနေ့ဆက်စပ်မူ | Some mappings explicit; clarify three-5 condition and target window. |
| new-day-thufri | ကြာသပတေး/သောကြာ ဆက်စပ်မူ | Need exact tail comparison and choice among branches. |
| new-day-fri | သောကြာနေ့ဆက်စပ်မူ | Some mappings explicit; check target window and ambiguous branches including continuation on page 3. |
| new-groups-j | မြန်မာပါဝါ J မှ နက္ခတ်မူ | Need definition of J and threshold for ကြွယ်. |
| new-born-return1 | ပြန်ကျလက်နှင့် 1 ပိတ်မူ | Need exact return-hand/phase condition. |
| new-special-967197 | 96R၊ 71R၊ 97R ဆက်စပ်မူ | Some explicit mappings; verify R direction, 20/02, 14679 and time scope. |
| new-groups-weekly | အပတ်စဉ်မပျောက်တတ်သည့် မူများ | Need weekly window, per-claim conditions and whole-week evaluation; no guarantee implied. |

## Next implementation gate
Resolve input semantics from original source/user, define exact session and validity window, check rule identity and coverage; only then implement and add exact positive/negative, expiry, missing-data and no-future-data tests. Use scheduled draw counts (not date count) for draw-limited rules, calendar/working-day scopes only when justified, and weekly boundaries explicitly. Do not fake source conditions, backfill prospective scores, or change ranking/visual redesign. Keep existing approved app areas untouched. Re-audit each ID after tests and record source mapping/remaining gaps. The separate original photo pages were not recovered by current library search; reader summaries and prior repo notes are available. No new application code in this triage commit.
