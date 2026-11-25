# Missing rule clarification — batch 2
Baseline Build 1946; follow-up to batch 1 (6d31ddfc730a2da2f9ed64ae3f46f35b10770a78). User answers numbered 1–10 correspond to overall 11–20. Requirements only; no code changes or retrospective score.

| No. | Canonical ID | User decision |
|---|---|---|
| 11 | cherry-034 | 06R: four scheduled sessions, not four dates; normally two trading days. Existing proposed stop-on-hit behavior not explicitly restated in the reply; confirm if needed. |
| 12 | cherry-046 | 57R: 357 wheel INCLUDING doubles; next session only. |
| 13 | cherry-075 | Exclude from Better Data only, preserve reader/source. |
| 14 | cherry-078 | Any position of 0 in preceding result; 7 digit anywhere; next session only. |
| 15 | cherry-085 | Evening 4 head; power or astrology group for following morning session only. |
| 16 | cherry-086 | Monday AM 7 head: retain only digit 3 follow for same week; exclude Thursday 5 and Friday 4 branches from Better Data, preserve original source. |
| 17 | cherry-087 | User confirms prior proposal: Thu/Fri AM head power pair -> next Monday–Friday, stop on first matching pair; R outputs as source. |
| 18 | cherry-088 | Exclude from Better Data only. |
| 19 | cherry-089 | Sum modulo 10 (17 -> 7); user says same-week only. Daily original wording and previous question suggest following day AM/PM with no next-week carry, but whether the digit persists for the entire week or is recalculated daily requires explicit clarification. |
| 20 | cherry-090 | Exclude from Better Data only. |

Batch 2: seven retained (11,12,14,15,16,17,19), three excluded (13,18,20). Combined batches: fourteen retained in principle, six excluded, none implemented by these documentation commits. The remaining 17 missing entries are added photo-source rules; original pages not recovered, use reader summaries and disclose missing source. No edits to main, UI, source library, scores or existing behavior.
