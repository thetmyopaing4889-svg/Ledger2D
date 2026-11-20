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
