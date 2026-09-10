# Ledger2D — AI Handoff and Completion Plan

## 1. Mission

This repository is the Myanmar 2D Agent Ledger Android application. The authoritative product specification is `/home/ubuntu/upload/architecture.md` from the user. A future AI coding agent must read that file completely before changing code. Do not silently simplify business rules, rename required Burmese/English terminology, or add unrequested online/account features.

The requested final result is a polished, production-oriented, offline-first Android application for one user. It must build from a clean checkout and produce a downloadable debug APK through GitHub Actions.

## 2. Repository and delivery state

- Repository: `thetmyopaing4889-svg/Ledger2D`
- Branch: `main`
- Last known-good commit: `d2e1985` (`Improve Burmese betting preview layout`)
- GitHub Actions workflow: `.github/workflows/android.yml`
- Last known-good Actions run before the current refinement attempt: `34188755121`
- Local project path: `/home/ubuntu/Ledger2D`
- The repository was initially empty; the Android project was created from scratch.
- Never place GitHub tokens, passwords, or other credentials in source files, commits, logs, or this document.

## 3. Technology and architecture already established

The project uses:

- Kotlin
- Jetpack Compose
- Material 3
- Gradle Kotlin DSL and Gradle version catalog
- Gradle wrapper
- Room local relational database
- KSP/Room code generation
- ViewModel and Kotlin Flow for reactive state
- Navigation Compose
- Java `LocalDate` and `DrawSession` (`MORNING` / `EVENING`)
- Integer MMK arithmetic for money
- Basis points for commission rates
- Feature-oriented package layout under `app/src/main/java/com/myanmar/ledger2d/`

Main packages:

```text
core/database       Room database, entities, DAOs
core/design         Material theme and design tokens
core/domain         Parser, expansion, validation, calculations, weekly logic
core/model          Domain models and draw identity
core/repository     Repository interfaces/implementations
feature/main        Navigation, ViewModel, Compose screens
```

Important source files:

```text
app/src/main/java/com/myanmar/ledger2d/core/database/Daos.kt
app/src/main/java/com/myanmar/ledger2d/core/database/Entities.kt
app/src/main/java/com/myanmar/ledger2d/core/database/LedgerDatabase.kt
app/src/main/java/com/myanmar/ledger2d/core/domain/BetEngine.kt
app/src/main/java/com/myanmar/ledger2d/core/domain/Calculators.kt
app/src/main/java/com/myanmar/ledger2d/core/domain/WeeklyReport.kt
app/src/main/java/com/myanmar/ledger2d/core/model/Models.kt
app/src/main/java/com/myanmar/ledger2d/core/repository/Repositories.kt
app/src/main/java/com/myanmar/ledger2d/feature/main/LedgerNav.kt
app/src/main/java/com/myanmar/ledger2d/feature/main/LedgerViewModel.kt
app/src/main/java/com/myanmar/ledger2d/feature/main/Screens.kt
```

## 4. Completed functionality that must be preserved

The last verified build contains:

- Welcome → Agent List navigation
- Agent create/edit
- Customer create/edit under an Agent
- Room entities and foreign-key relationships for Agent, Customer, BetEntry, BetLine, WinningNumber, ClosedDay, ClosedNumber, AllLimit, SpecialLimit
- Offline database as the authoritative source
- Betting Entry with Date, Draw, Input Box, quick formats, live validation, and final `Confirm` button
- Parser support for separators `.`, `-`, space, `/`
- Reverse `R` / `r`
- Quick formats: ပါဝါ, နက္ခတ်, အပူး, ညီအကို, အခွေ, အခွေပူး, ပတ်သီး, ထိပ်စည်း, နောက်ပိတ်
- Duplicate digit aggregation
- Cumulative All Limit and Special Limit precedence
- Closed Number validation after expansion
- Deterministic commission, payout, and profit/loss calculators
- 100-digit customer List and Agent Total List
- Global Winning Number entry/history skeleton
- Agent and Customer winning/report route skeletons
- Burmese compact betting preview redesign from commit `d2e1985`
- Unit tests for parser, formats, limits, closed numbers, calculations, aggregation, and weekly-row rules
- GitHub Actions test + debug APK artifact workflow

## 5. Last verified commands

Run from the repository root:

```bash
./gradlew clean testDebugUnitTest assembleDebug --no-daemon
```

Expected result at commit `d2e1985`:

```text
BUILD SUCCESSFUL
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions workflow must continue to run:

```yaml
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

and upload:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 6. Critical product rules

All rules below come from `architecture.md` and are non-negotiable:

1. Android native Kotlin + Compose.
2. Room database is the single source of truth.
3. Offline-first; no login, cloud sync, ads, subscription, online server, export, notification, or app-lock feature unless separately requested.
4. UI must never execute SQL directly.
5. Business formulas belong in domain services/use cases, not Composables.
6. Money is exact integer MMK; never use floating point for money.
7. Percentages use deterministic basis points.
8. Date + draw session identifies a draw; use proper ISO/local date internally.
9. Customer belongs to exactly one Agent.
10. Winning Number is global, not owned by Agent or Customer.
11. Closed Day is global and belongs on Agent List.
12. Closed Number is Agent-specific and applies after every parser/format expansion.
13. Special Limit takes precedence over All Limit for the same digit.
14. Betting submission button is exactly `Confirm`, never `Save`.
15. Winning-number input form uses `Save` and `Cancel`.
16. User-created/configurable data must support Edit, including winning numbers, limits, closed numbers, closed days, customers, agents, and betting records.
17. Do not invent an evening clock time.
18. Weekly report always has 5 operating weekdays × 2 draws = 10 rows, including future blank rows.
19. First production launch is empty: `No Agents`; do not seed fake business data.

## 7. Work that is still missing or incorrect

The current last-known-good commit is safe and buildable, but the following must be implemented before claiming final completion.

### A. Global Winning Number

The top-level `Agent List → ထီပေါက်စဉ်` screen must show exactly two choices:

```text
ပေါက်ဂဏန်းထည့်ရန်
ထီပေါက်စဉ်ကြည့်ရန်
```

The entry form must support Date (today default), မနက်/ညနေ, Digit 00–99, Save, Cancel. It must create or update one global result per date/session, prevent duplicate date/session records, and provide Edit/remove.

The history must use day-based cards, with morning and evening results displayed separately. It must not mix global winning history with customer betting data.

### B. Agent-level Winning Number

`Agent → ထီပေါက်စဉ်` must use the global winner and aggregate all customers under that Agent for each date/session. It must show real stored data:

- ထိုးကြေး
- ပေါက်ကြေး (stake on the global winning digit)
- လျော်ပေးငွေ = ပေါက်ကြေး × Agent Rate
- ရှုံး/မြတ် = ထိုးကြေး − လျော်ပေးငွေ

Use day cards and keep morning/evening separate.

### C. Customer-level Winning Number

`Customer → ထီပေါက်စဉ်` must show only that customer’s data in day cards. For each date and each session, show the global winning digit if available, total stake, winning stake, payout, and P/L. Past dates remain viewable. If no winning result exists, show an unavailable state instead of asking the user to enter a winner.

### D. Real reports, not explanatory placeholders

`Agent → Report` must be customer-by-customer with the required columns and total row. `Customer → Report` must have exactly:

```text
ရက်အလိုက်
Weekly အလိုက်
```

Daily must select date and မနက်/ညနေ, then Before/After. Before does not require a winning number. After must show unavailable until the relevant global winner exists. Weekly must always show Monday–Friday × morning/evening = 10 rows, including future blank rows, and both Before and After need totals.

All formulas must be centralized in domain services and tested:

```text
ကော်မရှင် = Total Bet × Customer Commission Rate
ပေါက်ကြေး = amount bet on winning digit
လျော်ပေးငွေ = ပေါက်ကြေး × Agent Rate
ရှုံး/မြတ် = Total Bet − လျော်ပေးငွေ
```

### E. Customer Analysis / သုံးသပ်ချက်

Add a Customer detail route named `Analysis` or `သုံးသပ်ချက်` (prefer user-facing `သုံးသပ်ချက်`). It must use selected date + session and real Room totals. Show:

- လက်ရှိအကွက်အရေအတွက် (distinct digits)
- ထိုးကြေးစုစုပေါင်း
- Limit သတ်မှတ်ထားသောအကွက်အရေအတွက်
- Limit နီးနေသောအကွက်အရေအတွက်
- ထိုးကြေးအများဆုံးအကွက်များ
- ပိတ်ထားသောအကွက်များ
- ထပ်မလက်ခံသင့်တော့သောအကွက်များ
- Scenario if each digit wins: winning stake, payout, P/L
- Worst-case payout and worst-case P/L

Default limit thresholds:

```text
80%+  သတိပေး
90%+  အလွန်နီး
100%  ပြည့်ပြီး / ထပ်မလက်ခံ
```

Do not claim statistical probability. Label the result as current-bet scenario analysis. A limit-less digit should not be described as limit-near, but high concentration may still be warned.

### F. UX and Burmese localization

The current betting preview has already been made Burmese and compact. Continue the same quality throughout the app:

- Replace remaining unnecessary English user-facing explanatory text with clear Burmese while preserving required labels such as `Save`, `Cancel`, `Confirm`, `Before`, `After`, `Analysis`, `Report`, and `List` where the specification requires them.
- Use localized, readable date presentation; keep ISO date internally.
- Format money with comma grouping and `MMK`.
- Use compact summary cards rather than long repetitive cards.
- Add clear date/session context to List and Total List.
- Keep Confirm visible and never let it cover the final preview row.
- Use intentional loading, empty, error, and unavailable states.
- Use accessible text/icons, not color alone.
- Add safe edit/delete confirmations and update dependent data reactively.
- Prefer date pickers over free-form date fields where stable and feasible.

### G. Offline backup/restore

Because the user is offline and single-user, security/login is intentionally not needed. Data loss prevention is important. Implement a safe local backup/restore feature if possible in the current scope:

- Versioned JSON backup of all Room entities and schema version.
- User selects a local file destination/source through Android file picker.
- Validate schema, dates, digits, foreign keys, and duplicate winner identity before restore.
- Restore transactionally; do not partially overwrite data.
- Explicit warning before restore.
- Never sync to cloud automatically.

Do not add a fake backup button. If a complete file-picker implementation cannot be safely completed in this pass, leave it out rather than shipping a non-functional control, but document it as the only remaining item.

## 8. Data and implementation guidance

Before editing, inspect current `Entities.kt`, `Daos.kt`, `Repositories.kt`, `LedgerViewModel.kt`, and `Screens.kt`. Prefer adding projection queries to DAOs for aggregate data rather than loading the whole database into UI memory. Use Flow for reactive screens and suspend queries for domain calculations.

Recommended domain services:

```text
BetParser
BetExpansionEngine
LimitValidator
ClosedNumberValidator
BetAggregator
CommissionCalculator
PayoutCalculator
ProfitLossCalculator
WinningNumberResolver
AnalysisCalculator
ReportCalculator
BackupService
```

Never duplicate formulas in separate screens. Add tests before or alongside every domain change.

## 9. Required tests before delivery

At minimum, all existing tests must remain green, plus tests for:

- One winner per date/session and duplicate prevention
- Global winner resolution by date/session
- Agent aggregate across multiple customers
- Customer scope excluding other customers
- Winning stake and payout
- Missing winner → After unavailable
- Daily Before and After formulas
- Weekly 10 rows with future blanks and total row
- Analysis distinct count, total stake, threshold warnings, highest-stake ranking, closed slots, and scenario P/L
- Editing a source bet changes totals/reports
- Backup validation and transactional restore, if implemented

## 10. One-pass execution checklist

The next AI should do the entire remaining implementation in one uninterrupted pass, not stop after analysis:

1. Read `architecture.md` and this `manus.md` completely.
2. Inspect current code and preserve the last known-good behavior.
3. Implement data/domain/services first.
4. Implement real winning screens, analysis, and reports.
5. Implement Burmese UI polish and safe edit/delete.
6. Implement backup/restore only if fully functional.
7. Run formatting/compile/tests.
8. Fix every compiler and test failure.
9. Run:

```bash
./gradlew clean testDebugUnitTest assembleDebug --no-daemon
```

10. Verify APK exists at `app/build/outputs/apk/debug/app-debug.apk`.
11. Update README with exact build and feature notes.
12. `git add . && git commit -m "Complete Ledger2D analysis winning reports and UX"`.
13. Push `main` to origin.
14. Confirm GitHub Actions succeeds and uploads `ledger2d-debug-apk`.
15. Report exact commit, Actions run URL, APK artifact URL/path, test result, and any genuinely remaining item. Do not claim complete if a critical screen is still a placeholder.

## 11. Safety rule for interrupted work

If a broad rewrite causes compilation errors, do not leave broken code in `main`. Save the attempted diff separately, restore the last known-good commit, and retry in a smaller coherent rewrite. A buildable app is more important than an unverified large patch.

## 12. Definition of done

The app is complete only when the architecture definition of done is satisfied: every requirement is implemented; Room is authoritative; formulas and parser are deterministic and tested; limits and closed numbers work through all expansion paths; date/session is attached to every bet; Confirm is used for betting; global winners drive scoped reports; editing updates dependent data; weekly future rows remain visible; UI is professional, responsive, accessible, and Burmese-clear; no unrequested features are added; and clean checkout build plus GitHub Actions APK build both succeed.


## 13. Current implementation status — 2026-09-09

The repository is on `main` at commit `90e092f` (`Harden user flows and release verification`). This commit adds the user-perspective hardening pass; its GitHub Actions result must be checked before treating the new lint/release workflow as green. This status section is intentionally explicit so a future agent can resume without repeating already-completed work or claiming unverified completion.

### Completed and verified in the current pass

- Premium UI foundation: typography hierarchy, color system, spacing, cards, top-bar navigation, Welcome, Agent, Customer, Betting, report, and analysis visual polish.
- Offline Room persistence with Agent, Customer, Bet Entry/Line, global Winner, Closed Day, Closed Number, All Limit, and Special Limit entities.
- Manual parser, reverse `R/r`, all named quick formats, duplicate aggregation, cumulative limits, special-limit precedence, and closed-number validation after expansion.
- Integer-MMK commission, payout, profit/loss, weekly-row, and customer-analysis domain calculations with existing unit coverage.
- Natural-key-safe updates for winner, all limit, special limit, closed day, and closed number by preserving the existing Room primary key instead of relying on a new-id `@Upsert`.
- ASCII-only `00`–`99` validation at the shared digit validator and winner input boundary, with a regression test for Burmese/Arabic-Indic numerals.
- Removal of the unsupported hard-coded evening `16:30` cutoff; the app no longer invents an evening result clock.
- Betting confirm/edit now reparses the current source and format instead of trusting an older shared preview; repository boundaries reject empty/invalid/duplicate lines; app-level mutation serialization and submit-state reset were added.
- Agent After reports now show an unavailable state when the global winner is absent.
- Report, scoped winning, and analysis screens now have a ViewModel revision refresh trigger after relevant mutations; weekly Before rows exclude future dates.
- Customer Detail now exposes Commission and the 100-digit List routes.
- Closed Number UI has a readable explanation, Burmese copy, empty state, and delete confirmation; Commission, List, History, and Settings copy/readability were polished.
- Default Burmese UI copy was cleaned to remove visible English fragments such as `LEDGER`, `Offline`, `Fast`, `Clear`, `Edit`, `Update`, `All Limit`, `Special Limit`, and `Closed`; the English dictionary was expanded for the corresponding screens.
- Luxury visual pass added a custom burgundy/rose, champagne-gold, and ink palette; richer light/dark surface hierarchy; larger premium shapes; elevation tokens; and motion tokens. App content now enters with a fade/vertical lift, and action tiles use press-scale feedback with animated elevation.
- User-flow hardening added Closed Day and Special Limit delete confirmations, clearer closed-day/limit empty states, Burmese format-help copy, and a more readable closed-day management layout.
- Financial boundaries now reject amounts above a documented safe bound (`9_000_000_000_000_000` MMK) and convert payout/aggregate overflow into explicit arithmetic failures instead of silent wrapping.
- GitHub Actions now runs unit tests, `lintDebug`, `assembleDebug`, and `assembleRelease`, and uploads both debug and release APK artifacts.
- GitHub Actions debug build is green; no online account, cloud sync, ads, billing, or other unrequested service was added.

### Currently in progress / next implementation batch

The next batch is the correctness-and-release pass, not another cosmetic-only pass:

1. Move authoritative betting admission checks into a coherent Room transaction and add concurrency/regression tests.
2. Add safe money/rate/aggregate/payout bounds and visible overflow error handling.
3. Add Room/repository/ViewModel tests for winner/limit CRUD, customer-versus-agent scope, missing-winner After behavior, source-bet edits, and reactive totals.
4. Add integration coverage proving the new revision refresh behavior and all report/winner scopes.
5. Complete agent/customer winner history cards, report columns, and remaining safe delete confirmations.
6. Add a tested local backup/restore flow or explicitly keep the app pre-release until recovery is available.
7. Validate lint, minified/signed release output, install smoke tests, and real-device UI/accessibility behavior.

### Known remaining items before claiming final completion

- Database-level atomic admission and proof against concurrent confirmation/winner races are not yet complete; the current `Mutex` is only an app-level safeguard.
- Extreme money/rate/aggregate overflow protection and release-safe error boundaries are not yet complete.
- Room integration, repository, ViewModel, concurrency, and Compose/instrumentation tests are still missing; current tests are primarily pure domain tests.
- Report, scoped winning, and analysis screens now refresh on ViewModel mutations, but Room-backed integration tests and failure-state coverage are still missing.
- Customer Commission and Customer 100-digit List are reachable from Customer Detail; their device-level usability and persistence-error states still require verification.
- Scoped winner history, report completeness/responsive layout, loading/not-found/error states, and safe delete confirmations for Closed Day, Special Limit, and remaining configuration actions need completion.
- Burmese/English localization still has remaining mixed copy and requires an emulator/device pass for Burmese font metrics, accessibility sizing, narrow screens, large font scale, IME behavior, and edge-to-edge layout.
- Luxury UI still requires a real device visual pass: the current design system is a polished foundation, not a verified pixel-level luxury benchmark. The next visual pass should focus on stronger brand identity, premium empty/loading states, consistent iconography, surface elevation, and screen-by-screen spacing rather than adding more features.
- Motion and theme behavior still requires a real emulator/device pass to verify reduced-motion expectations, Burmese text reflow, touch feedback timing, and performance on lower-end hardware.
- A source-level user walkthrough found and removed an additional mixed-copy string (`Update လုပ်ရန်…`) and normalized the closed-number explanation and report mode labels. This does not replace a real Burmese device walkthrough.
- Backup/restore is intentionally not shipped; the app has no tested local recovery path while Android system backup is disabled.
- Signed/minified release build, lint, install checks on representative API levels, and production release artifact validation are not complete. CI currently verifies debug APK only.
- The new CI workflow requests a minified release build and lint; the workflow result for commit `90e092f` is still the verification gate. Release signing is not configured for store distribution, so a successful release artifact is verification-only, not a publishable signed release.

The repository must not be described as fully production-complete solely from the green debug CI run. The sandbox currently has no discoverable Android SDK, so the mandated local command cannot be run here until `ANDROID_HOME` or `local.properties` is supplied. A real emulator/device walkthrough remains required.

### User-perspective walkthrough — remaining friction

From a user perspective, the main journey is reachable: Welcome → Agent → Customer → Betting → Confirm → History → Reports → Analysis → Settings. The remaining friction is concentrated in production verification rather than missing primary routes: first-load loading feedback is not consistently distinct from an empty state; not-found and persistence-error states are not uniformly surfaced; Closed Day and Special Limit destructive actions still need confirmation; long Burmese report rows and the 100-digit grid need narrow-screen and large-font verification; winner history and report presentation can be more compact and scannable; and the app needs a real-device pass for touch targets, text reflow, motion performance, and edge-to-edge insets.

For the next handoff, use:

```bash
./gradlew clean testDebugUnitTest assembleDebug --no-daemon
```

and verify `app/build/outputs/apk/debug/app-debug.apk`, the GitHub Actions artifact `ledger2d-debug-apk`, and a real device/emulator walkthrough of Welcome → Agent → Customer → Betting → History → Reports → Analysis → Settings.


## 14. Current implementation status — 2026-09-10 continuation

This section supersedes older status notes above where they conflict. The repository is on `main` at commit `e3f5d76` (`Persist settlement confirmations and audit events`). The latest GitHub Actions run is `34480652153`, and it passed unit tests, lint, debug APK build, release APK verification, and both artifact uploads.

### Work completed in this continuation

- Added Agent-wide all-limit and special-digit limit entities, DAOs, repositories, UI, and validation across all customers under one Agent.
- Added bet input-format persistence and Room migration `2 -> 3`.
- Added weekday/session cutoff validation without inventing an unsupported evening clock rule.
- Added result lock guards: bets cannot be edited or deleted after a winning result exists; an existing winning result cannot be changed or deleted while bets exist.
- Added `netSettlement = profitLoss + commission` to the domain calculation and exposed it in reports and settlement UI.
- Added Home-first operational navigation to Today Ledger and Settlement.
- Added Today Ledger grouped by Agent/date/draw session.
- Added Settlement workspace with Agent summaries, payout, commission, net settlement, and confirmation status.
- Added persistent `SettlementEntity` and `AuditEventEntity`, DAOs, repositories, AppContainer wiring, and Room migration `3 -> 4`.
- Added settlement confirmation persistence so the same Agent/date/session cannot be settled twice.
- Added audit events for bet creation, winning-number creation, and settlement confirmation.
- Added regression tests for Agent-wide limits, draw timing, settlement net calculation, and prior core domain behavior.
- Fixed CI compile/test failures rather than leaving unverified code on `main`: shared formatter visibility, and an incorrect exact-limit boundary assertion.

### Remaining implementation work before final claim

The following items are still genuinely open and must not be described as complete until implemented and verified:

1. Commission snapshot: store the Customer commission basis/rate and derived commission amount with the relevant immutable bet/settlement record so later rate edits cannot alter historical results.
2. Full Burmese/English localization: route all remaining Agent, Customer, Limit, History, Winner, Report, Analysis, Settlement, Settings, dialog, error, empty, and unavailable copy through the language state while preserving required labels such as `Confirm`, `Save`, and `Cancel`.
3. Final navigation polish: replace transitional Home action cards with the final operational navigation structure without breaking existing detail routes.
4. Quick Entry final UX: post-confirm receipt/summary, fast reset for the next entry, and remaining one-hand/keyboard polish.
5. Settlement history screen and read-only historical settlement view, including audit visibility where useful.
6. Database-level atomic admission/settlement safeguards and tests for concurrent winner/bet races; the current ViewModel Mutex protects one process only.
7. Full Room/repository/ViewModel integration tests, localization coverage, and error-state coverage.
8. Local backup/restore is not shipped. It may only be added if a complete Android file-picker, schema-validation, and transactional restore implementation can be verified safely; otherwise it remains an explicit pre-release item.
9. Real-device/emulator verification for Burmese text reflow, narrow screens, large font scale, IME behavior, touch targets, edge-to-edge insets, and APK install smoke tests.

### One-pass continuation instruction

Do not stop after analysis or after a partial feature. Implement all feasible items above in one coherent pass, run formatting/static checks, run the complete Gradle test/build workflow, fix every failure, push `main`, verify GitHub Actions, and update this section with the exact final commit, run ID, and any item that is still objectively outside the verified scope. Never claim complete merely because CI is green.
