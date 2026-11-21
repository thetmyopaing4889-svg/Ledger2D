# Cherry 2D: additional source rules and Better Data integration

## Base and boundaries
- From tested successful Build 1814 (commit `94c97d5c895b9237df228138937ee78eaca01315`) on isolated `feature/rules-library-betterdata-extension-from-build1814-20261120`; no edits to `main`.
- New rules transcribed/conservatively summarized from three user-supplied Myanmar 2D Formula Reference page images. These are **source claims**, not scientifically validated win rates.
- Keep the original 75 canonical rules and the original `two_d_rules_source.txt` unchanged. New source entries live in `two_d_rules_reader.json`.
- Preserve all existing LIVE, Research Workspace, formula-discovery, Room and settlement behavior.

## Library
- 23 new reader entries (98 total), exactly 10 functional books, with `လူမိုက်အုပ်စု` first and its **two** separate rules:
  - group trigger 17, 20, 80, 56, 51 => doubles (00,11,...99) proposed in source;
  - same trigger => 1267 wheel. **Not** "follow the other four group pairs."
- Duplicated 00–99 doubles table / canonical 12R and 27R notes are not re-added as separate source rules.
- Five weekday associations are recorded only as source references; insufficient precise trigger-to-output mapping for safe calculation.

## Active Better Data signals (conservative)
- `new-mob-double`, `new-mob-wheel`: current-tab next draw only; source does not define a longer expiry.
- `new-born-75`: morning 75 => 45789 wheel, **next two working days** (not the same day).
- `new-bk-19r`: 19/91 => one-break family for five working days.
- `new-special-khant`: only the explicit 98/11/91 => zero-head part, next draw.
- `s6-12` + `s6-12-wheel`: previously catalogued 12R => 48R and 4789 wheel now represented in current-tab evidence.
- New evidence uses stable IDs, normal existing cuts, same report dedup/ranking, no forced prioritization, no invented prediction success rates.

## Deferred from automated calculation
- 6 break => "1649" has a conflicting parenthetical source digit set; no formula evaluation until corrected.
- 9072, 3103, double-chain, 0-chain, J astrology, weekday associations, 96R/71R/97R, weekly always-present claims: source wording lacks necessary phase/trigger or expiry conditions.
- Any zero or ambiguous explicit expiry is treated as **current draw only**, never silently as a multi-day born rule.
- The isolated V3 discovery / Champion path remains untouched.

## QA
- Book count / original 75 preservation / new 23 / total 98.
- Mob outcome exact pair sets; born rule two-day and five-day bounds; 0-head trigger.
- Existing app-wide Gradle unit tests, lint, debug and release packaging must pass before device acceptance.

## Reader presentation final pass
- Reader entries contain concise formula content only. Unverified duration, contradictory source parentheses and parser-review notes remain in this document, not reader UI.
- New rule cards in Better Data show traceable names, triggers and pair-generation explanations, with a source-page excerpt link where the source is unambiguous.
- Page 3's opening line (46,56,50,70 and 0-break group) was retained alongside the continued Friday rule.

## Build 1817 corrections (20 November 2026)
- **Separate mob conditions:** the user-supplied page 1 explicitly lists 17/20/80/56/51 for လူမိုက်ကြီးအပူး but does not list member pairs for the separate အပေါက်လူမိုက် => 1267 wheel. The latter stays readable, but is disabled in Better Data until its exact input list is known.
- **6 BK:** page 2 explicitly says 6 break => 1649 (1, 6, 4, 9) wheel. Current next-draw calculation outputs all 12 non-double direct/reverse pairs. Do not invent longer duration.
- **50:** page 3 contains previously catalogued 12R / 27R and a distinct new 50 => 3 head/8 head (37/82 examples). The added reader uses one 50-only ID; existing 12R/27R source remains unchanged, while Better Data expands both heads into 20 pairs for next draw.
- **Number First lookup:** source mentions from new reader entries are visible under a separate heading and open their books; not misrepresented as directly triggered rules. Copy includes source text from both old and new libraries.
- All uncertain formulas remain reference-only until conditions are clarified and tested against data. Historical win-rate claims are not independently proven.

## Follow-up: source-day units and reference labels (2026-11-21)

- Additional source image pages 1 and 3 say **၂ ရက်** (75 -> 45789) and **၅ ရက်** (19R -> 1-break), without a special "trading days" qualifier. This build interprets those as **calendar-day bounds**, and does not change the pre-existing born rules' trading-day windows. The meaning is a source interpretation, not a proven forecast.
- The historical test explicitly covers a Thursday-to-Monday 75 signal (expired after two calendar days), a Friday-to-Wednesday 19R signal (five days), and unchanged Friday-to-Monday legacy double birth.
- Better Data's source link calls added image summaries **ဓာတ်ပုံအကျဉ်းချုပ်**, not verbatim original quotations. Existing legacy excerpts remain labeled original.
- Source-ambiguous 17 other added references remain reader-only; do not invent trigger/expiry until source instructions specify them.
