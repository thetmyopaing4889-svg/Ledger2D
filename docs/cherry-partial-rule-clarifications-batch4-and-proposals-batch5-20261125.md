# Partial rules — batch 4 decisions and final batch 5 proposals
Code baseline Build 1956 (8e314ff24fb548498b8626e66366a95180bf459d); previous document d6d2259955c12e9854cf80bf68e911b59f64c6e5. User explicitly approves all proposals 31–40. Requirements only; no app code changes.

## Batch 5 proposals (pending user review)
Compared reader JSON and CherryBetterDataEngine.kt at d6d2259955c12e9854cf80bf68e911b59f64c6e5. Image-source entries are stored summaries, not verified original photo text.
| No. | ID | Gap and proposed resolution |
|---|---|---|
| 41 | cherry-076 | 80/08 -> 1264 wheel including doubles (16 pairs). Currently present in both current and born; use only same-week born, stop first hit, avoid duplicate evidence. |
| 42 | cherry-077 | 67/76 -> 8123 wheel including doubles (16 pairs). Same-week born only, stop first hit. |
| 43 | cherry-079 | 74/47 -> digit 8 anywhere (19 pairs). Already both directions correct; keep current next scheduled session only, following user-approved 078 convention for comparable duration-free digit follows. |
| 44 | cherry-080 | 48/84 -> tail 6 (10 pairs); keep next scheduled session only. |
| 45 | cherry-081 | 71/17 -> digit 2 anywhere (19 pairs); keep next scheduled session only. |
| 46 | cherry-082 | 15/51 -> digit 4 anywhere (19 pairs); keep next scheduled session only. |
| 47 | cherry-084 | 13/31 -> break 3 (10 pairs); keep next scheduled session only. |
| 48 | new-mob-double | Literal 17/20/80/56/51 group -> doubles; same-week born like user-confirmed mob wheel, independent criterion stops on first hit; no implicit R. |
| 49 | new-born-75 | Literal AM 75 (not 57). Preserve next two calendar dates (not same-day PM), stop on first matching eligible result; correct existing helper so same-day PM does not terminate a rule before its start. Proposed interpretation: no separate repeat prerequisite without defined comparison window. |
| 50 | new-special-khant | Keep explicit literal 98/11/91 -> head 0; add explicit 40R (40/04) -> tail 4. Next scheduled session only, independent triggers. Unclear 5/7 break and 5/0 digit–6 break relations are not directional rules as written; omit those branches until clarified, preserve reader and do not award whole-source score. |

All 50 originally partial entries now statically inspected; 40 approved and final 10 proposals pending. Source decisions, scope and implementation tests must still be completed before declaring full coverage. No source deletion, archive rewrite, ranking or design changes. Named digit, break and wheel criteria use category semantics; explicit listed pairs follow user R rule. No claim of predictive reliability.
