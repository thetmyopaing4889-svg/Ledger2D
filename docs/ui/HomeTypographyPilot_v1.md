# Cherry's 2D Ledger — Home typography pilot (after Build #936)

## Scope
Only HomeWeeklyResults heading, morning/evening row labels, HomeCommissionCard heading/metadata/left row labels, quick-action captions/badges, and Home footer opt into HomeTypography styles.

The opt-in styles delegate font-family selection to LedgerTypographyStandard. They preserve existing Home compact size and line-height measurements:
- English: SansSerif.
- Myanmar: bundled Noto Sans Myanmar UI.
- Do not globally override MaterialTheme.
- Keep original cherry header art, card shapes, dimensions, positions, backgrounds, touch targets and bottom navigation.

## Exclusions / unresolved issues
- Weekly date/weekday labels, result digits and all commission monetary values retain existing rendering and calculation logic.
- Do not change stakes, commissions, winners, bet logic, reports, database or customer/agent JPEG exports.
- Home uses a fixed-height, non-scrolling layout. Changing sizes, copy wrapping, shortcut geometry or component heights needs explicit device checks before rollout.
- Existing money fields can use ellipsis when very long. This is NOT solved by this typography pilot. No financial figure was altered; a separately designed non-clipping money layout is required.
- Existing long Myanmar weekly heading may use more lines than the English title. Compilation alone cannot prove language layouts equal.

## Acceptance for this pilot
1. Unit tests, Android lint, debug APK, release APK and artifact uploads pass.
2. Same-device English and Myanmar Home screenshots show unchanged navigation and Cherry art.
3. Weekly grid dates and winner numbers are intact.
4. Commission totals agree with existing Commission details and reports.
5. Shortcut touch targets and badges remain usable.
6. If anything overflows, revise Home-only pilot or revert to backup; do not rebuild the entire UI.
