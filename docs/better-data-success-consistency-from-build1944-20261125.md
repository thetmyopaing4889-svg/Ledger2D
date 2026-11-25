# Better Data / Rules consistency — Build 1944
Approved baseline: f2facf075dcfeacd79158aae1b3cc3040faea461, Build 1944. Isolated branch only; no main, Home/Form, Research, LIVE fetcher, formula-discovery engine or original rule text changes.

## Requirements
1. Use the existing reader-facing wording for Current, Born and Success; retain stable rule IDs and raw archived provenance.
2. Distinguish exact pair vs reverse, distinct evidence instance vs rule family vs canonical Library source. Do not invent a whole-source mapping for partial rules; keep one score per eligible source/draw.
3. Remove raw section headings and technical origin strings from visible Success list. Keep records.
4. Show correct rules first; misses collapsed by default, expandable.
5. Provide details using actual recorded pairs and source evidence; never reconstruct a historical forecast using today's data. Show source text only on verified mapping; old snapshots may lack detailed evidence.
6. Preserve existing Library success bridge and historical archives, verify no duplicate awards.
7. Clearly distinguish all generated pairs vs uncut eligible pairs in Current/Born; Success evaluates only the recorded uncut pairs.
8. Keep Current/Born on the pending draw until confirmed winning number arrives, including late AM/PM results and screen reopen. Do not interpret a provisional live result as confirmed. Respect weekends and closed dates; avoid moving backwards after a later confirmed result. Missing data is not a loss.
9. Preserve strict pre-result snapshot cutoff (AM 12:00, PM 16:30); display rollover is independent. Never create or revise the finished/closed draw's archived evidence after cutoff. Do not alter settlement schedule. Display archived inputs when available for a pending draw; if unavailable, no retrospective scoring.
10. Regression coverage: 53 -> 97 exact-pair evaluation, duplicate labels, missing 9:30 values, late AM at 12:03 and PM at 16:33, confirmed arrival, restart, closed day/weekend, historical Library scoring. CI tests/lint/APKs and phone acceptance required.

## Implementation scope
Prefer a pure Better Data draw-selection helper, reuse confirmed Historic2DDataSource, separate display cutoff and storage cutoff, scoped Success UI changes. No change to rule calculation semantics or score definitions without explicit evidence; record remaining gaps rather than fabricating past snapshots. Build success is not device acceptance.
