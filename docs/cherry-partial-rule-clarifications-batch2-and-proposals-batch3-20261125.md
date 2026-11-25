# Partial rules — batch 2 decisions, batch 3 proposals
Code baseline Build 1956 (8e314ff24fb548498b8626e66366a95180bf459d); prior document 827712ab2a6f1a061a4134f7d674b47400a8375f. Requirements only. No application code changes.

## User decisions 11–20
11,12,14–19 approved as previously proposed. For 13, user explicitly confirms that a trailing R applies to the entire slash-separated group, e.g. 10/20/30R expands to 10,01,20,02,30,03. Expand all members of the explicit R groups in the double-born table; deduplicate. Proposed same-week expiry and stop-on-hit unchanged but not separately restated by user for 13.
20 (cherry-035) correction supersedes prior proposal: 07 alone triggers 49/29; 70 alone triggers the power group with R outputs. Do not let 07 trigger power by reversing the trigger; user explicitly distinguishes 07 and 70. Same-week born scope as proposed.

## Batch 3 proposals (not yet user-approved)
Compared exact reader source and current engine at Build 1956.
| No. | ID | Current gap and proposed resolution |
|---|---|---|
| 21 | cherry-036 | 09 literal ->68 literal. Current next-session only; move to same-week born, stop on hit. |
| 22 | cherry-038 | 12/21 -> digit 1 anywhere, 48/84, 80/08/35/53, non-double 7894 wheel. Current only 48R and wheel. Separate traceable branches, same-week born, independent stop on matching branch; preserve equivalent wheel digit order. |
| 23 | cherry-039 | 14 literal ->17 literal, same-week born, stop on hit. |
| 24 | cherry-040 | 17 literal ->14 literal, same-week born, stop on hit. |
| 25 | cherry-041 | 23 literal ->79 literal, same-week born, stop on hit. |
| 26 | cherry-042 | 27 literal ->47/37 literal; 27 or 72 ->0 head or break 8. Independent branches, same-week born; second branch retires on either criterion hit. Explicit 27R trigger is retained unless user distinguishes directions as in 035. |
| 27 | cherry-043 | 43/34 ->89 literal. Both directions fixed in 1946; propose same-week born rather than next-session only, stop on hit. |
| 28 | cherry-044 | 49 literal ->60 literal, same-week born, stop on hit. Do not alter separate 94R→1 digit rule. |
| 29 | cherry-045 | 54 literal ->04 literal, same-week born, stop on hit. |
| 30 | cherry-047 | 59 literal ->47/79 literal and same-week exact repeat watch. Current only direct pairs. Use already observed exact pairs in target week; retire repeat branch on first exact repeat after trigger, independently of 47/79. Source 88% assertion is not verified and must not be used as measured score. |

30 of 50 original partial entries inspected; 20 discussed, 10 proposals pending, 20 still to inspect. No predictive performance claim or source deletion; prior snapshots remain immutable.
