# Agent / Customer dashboard landing cards — shared style pilot

Build base: #938, Home commission layout pilot.

## What changes
- Only the **entry list cards** in AgentDashboardScreen and CustomerDashboardScreen.
- Use the same reusable card component: white surface, matching icon, border, padding, minimum height (78dp), title/subtitle typography and right arrow.
- English uses existing SansSerif; Myanmar uses Noto Sans Myanmar UI at the same compact role sizes.
- Each card has short bilingual descriptive text. Card title, icon, key, action order and onClick routing are preserved.
- Both landing lists remain scrollable; long descriptions may extend up to two lines without truncating numbers (no money on these cards).

## Explicit exclusions
- All Agent/Customer scope pickers, account forms, transaction rows, reports, analysis, historical result handling, JPEG exports, cash calculations and Home are unchanged in this batch.
- No new backend, database or API interactions.

## Sign-off needed
- Both dashboards English and Myanmar on device at standard font scale.
- Every card opens the identical destination as before, especially agent/customer JPEG, reports, lists and analysis.
- Icon/text vertically centered; no title clipping.
- Verify 78dp minimum and two-line subtitle do not feel too tall on small screens.
- If the shared layout is visually rejected, revert to backup before this batch; preserve existing features.
