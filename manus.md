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


## 15. Latest verified continuation — 2026-09-10

- Added immutable commission snapshot fields to `BetEntryEntity`: `commissionRateBasisPoints` and `commissionAmount`.
- Added Room migration `4 -> 5` for the commission snapshot columns.
- Commission is captured at bet confirmation from the Customer's current rate and historical reports read the stored aggregate commission rather than recalculating old bets from a later-edited Customer rate.
- Added localized dictionary entries and routed the new Today Ledger and Settlement operational screens through `LanguageState`.
- The first CI attempt for this batch failed only because `CommissionCalculator` was not imported in `Repositories.kt`; it was fixed in commit `ffb2b76`.
- Final verified commit: `ffb2b76` (`Fix commission snapshot calculator import`).
- Final GitHub Actions run: `34491160761`, green for unit tests, lint, debug APK, release APK verification, and both artifact uploads.

### Remaining honest scope

Full language routing across every legacy screen and a real Android emulator/device walkthrough are still not proven by source/CI alone. Backup/restore is also not shipped because it requires a complete file-picker and transactional validation implementation. These are verification/scope items, not silently claimed as complete.


## 14. Provisional UX direction agreed for continued discussion — 2026-09-11

The user clarified that the current problem is not missing features or broken business logic. The primary problem is that the current Home screen and navigation expose too many overlapping actions. The next AI must continue the UX discussion before changing code. This section records the current direction, but it is **not yet final approval for implementation**.

### Core instruction for the next AI

> **Do not start UI or navigation implementation yet. Continue discussing, validating, and confirming this information architecture with the user first.**

The next AI must understand the user's intended structure, compare it with the current codebase, identify contradictions or missing decisions, and ask focused clarification questions where necessary. It must not silently convert this provisional direction into code.

The work sequence is:

```text
Current application audit
→ User-flow comparison
→ Proposed structure
→ Discussion with user
→ Explicit confirmation
→ UI/navigation implementation
→ Build and verification
```

### Provisional Home direction

The user is currently considering the following Home structure:

```text
Home
├── အမြန်စာရင်းသွင်းရန်
├── ဒိုင်အသစ်ထည့်ရန်
├── ထိုးသားအသစ်ထည့်ရန်
├── ပိတ်ရက်
├── ထီပေါက်စဉ်
├── လမ်းညွှန်
├── Agent Dashboard / Panel
└── Customer Dashboard / Panel
```

Home should also show the current date and useful current-day information. These are the currently discussed Home destinations, not a final implementation instruction. The next AI must help determine whether all of them should be equally prominent, compactly grouped, or separated into primary actions, global actions, and dashboard entry points.

The user's main-entry intention is:

- **အမြန်စာရင်းသွင်းရန်** must allow Agent selection and then Customer selection before entering the existing betting workflow.
- **ဒိုင်အသစ်ထည့်ရန်** should open the normal Agent creation form.
- **ထိုးသားအသစ်ထည့်ရန်** must first require Agent selection, then open the Customer creation form under that Agent.
- Home should not become a random grid of every feature. Button prominence, grouping, duplication, and visual hierarchy still require discussion and confirmation.

### Provisional Agent Dashboard / Panel direction

The user is considering an Agent Dashboard / Panel as a dedicated workspace. It should contain Agent-oriented areas:

```text
Agent Dashboard / Panel
├── Agent list
├── စုစုပေါင်းစာရင်း
├── အစီရင်ခံစာ
├── ပိတ်ဂဏန်း
├── ထီပေါက်စဉ်
└── ကန့်သတ်ပမာဏ
```

The user specifically wants Agent-related list/report/configuration views to support explicit scope:

- Is the view for **all Agents**?
- Or is it for **one selected Agent**?

For example, Agent totals should not require the old path of Agent List → Agent Detail → Total. The proposed direction is to let the Agent-oriented workspace or its child workspace make the Agent scope explicit, such as All Agents or one selected Agent. The exact placement of the selector, persistence of the selected Agent, and whether this is a shared workspace filter are still open decisions for discussion.

The old Agent Detail screen should not be deleted automatically. The next AI must compare these options against the current workflow:

- Keep and simplify Agent Detail as a context screen.
- Merge its useful context into an Agent Dashboard.
- Replace its crowded action hub with scope-aware Agent workspaces.
- Remove it only if context and navigation remain clear after the replacement is confirmed.

### Provisional Customer Dashboard / Panel direction

The user is also considering a Customer Dashboard / Panel containing:

```text
Customer Dashboard / Panel
├── Customer list
├── စာရင်းမှတ်တမ်း
├── အစီရင်ခံစာ
├── အမြန်သုံးသပ်ချက်
├── အကွက်စာရင်း
├── ကော်မရှင်
└── ထီပေါက်စဉ်
```

Because every Customer belongs to an Agent, Customer-oriented work must make both scopes clear:

- Which Agent is selected?
- Which Customer is selected?
- Is the view for all Customers under the selected Agent?
- Or is it for one Customer under that Agent?

The next AI must not assume that a single global Customer list is sufficient. It must propose how Agent selector and Customer selector interact, how the selected context remains visible when moving to history/report/analysis/commission/winning views, and how an All Customers view differs from a single Customer view.

### Important boundary between global and scoped features

The next AI must distinguish:

```text
Global
├── Closed Day
├── Global winning-number entry/history
└── Format Guide

Agent-scoped
├── Agent totals
├── Agent reports
├── Agent closed numbers
├── Agent winning result view
└── Agent limits

Customer-scoped
├── Customer bet history
├── Customer reports
├── Customer analysis
├── Customer digit list
├── Customer commission
└── Customer winning result view
```

This is a discussion model, not yet a final route map. Global winning-number entry must remain distinct from Agent/Customer result views; Agent and Customer result screens should consume the global winning source rather than ask the user to enter duplicate winners.

### Current user-flow questions that must be resolved before coding

The next AI must continue the discussion and explicitly confirm:

1. Whether Home's eight discussed destinations should all be visible at the same visual level.
2. Whether Agent Dashboard and Customer Dashboard are top-level Home buttons, bottom navigation items, or management workspaces reached through a smaller number of destinations.
3. Whether the old Agent Detail remains as a compact context screen or is merged into Agent Dashboard.
4. Whether Customer Dashboard starts with Agent selection, Customer selection, or a recent-context default.
5. Whether Agent totals/reports/configuration share one persistent Agent selector or each workspace owns its own selector.
6. Whether Customer totals/reports/configuration share one persistent Agent + Customer selector.
7. Which destinations are daily operational actions versus occasional setup/configuration.
8. Whether Closed Day, Global Winning, and Guide should be Home quick actions or grouped under a global workspace.
9. Whether the bottom navigation is necessary after the dashboard structure is finalized, and which destinations are genuinely visited repeatedly during a working day.
10. How Home should prioritize current-day summary versus the three main data-entry actions.

### Explicit no-implementation instruction

Until the user and the next AI have discussed and confirmed the above structure:

- Do not rewrite Home.
- Do not remove Agent Detail.
- Do not add or remove bottom-navigation destinations.
- Do not move Closed Day, Winning, Guide, Limit, or Closed Number routes.
- Do not change Room schema or business logic.
- Do not claim that this provisional architecture is final.
- Do not create duplicate routes merely to make a shortcut work.

After explicit confirmation, implement the smallest coherent presentation/navigation change, preserve all existing domain behavior, run the full test/build workflow, and report exactly what was confirmed versus what remains open.

### Handoff message to the next AI

> The user is not asking for more random buttons or a cosmetic Home redesign. They are trying to reorganize the app by separating the main operational entry actions from Agent-scoped and Customer-scoped management workspaces. Carefully understand the provisional Home, Agent Dashboard, and Customer Dashboard structure above. Continue discussing and validating the navigation and scope model with the user first. Only after the user explicitly confirms the final information architecture should you modify the code.


## 16. Latest authoritative implementation handoff — 2026-09-12

> This section supersedes all earlier provisional UX notes, stale commit references, and older “remaining work” lists where they conflict with the current repository state. Earlier sections are retained as historical context only.

### 16.1 Current repository and verification state

The repository is `thetmyopaing4889-svg/Ledger2D`, on branch `main`, at commit `10f4b39` (`Expose shared report helpers to dashboard workspaces`). The working tree was clean and `main` was synchronized with `origin/main` at the time of this update. The final GitHub Actions run was `34663015704` and completed successfully.

The successful CI workflow verified unit tests, lint, debug APK compilation, release APK verification, and upload of the debug and release APK artifacts. The GitHub Actions run is available at [34663015704](https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/34663015704).

This is the correct baseline for the next AI agent. Do not use older references such as `d2e1985`, `90e092f`, `e3f5d76`, `ffb2b76`, or their associated run IDs as the current implementation baseline unless investigating history.

### 16.2 Why the application was changed

The user’s main complaint was not that the app lacked isolated screens. The problem was that the original workflow was too long, repetitive, and difficult to use. The implementation therefore moved from a route-heavy workflow toward a dashboard-and-workspace model.

The central design goal is to let a user choose the scope once and then operate the selected feature in the same workspace. Agent features use an Agent selector with an explicit “ဒိုင်အားလုံး” option where aggregation is meaningful. Customer features require an Agent first, then expose only that Agent’s customers plus “Customer အားလုံး”. No unrelated Agent or Customer may leak into the selected scope.

The second design goal is to make Home a practical first-view screen: the weekly winning-number cards, quick entry, Add Agent, Add Customer, and the requested bottom navigation should be immediately understandable without duplicate welcome headings or unnecessary status cards. The Home weekly cards are compact, date-labelled, and digit-first; totals are available through the relevant interaction rather than competing with the primary winning digit.

### 16.3 Foundation and navigation changes completed

The current application preserves the offline-first Android architecture: Kotlin, Jetpack Compose, Material 3, Room, ViewModel, Kotlin Flow, Navigation Compose, LocalDate, DrawSession, integer MMK arithmetic, and basis-point commission rates. Room remains the authoritative data source and no login, cloud sync, ads, subscription, billing, or online service was added.

The completed navigation and dashboard foundation includes:

- Home as the operational landing screen.
- Agent Dashboard with a real Agent List sourced from Add Agent records.
- Customer Dashboard with a real Customer List scoped to the selected Agent.
- Agent detail and Customer detail routes with edit behavior preserved.
- Agent feature workspace containing Total, Report, Closed Number, Winning, and Limit features.
- Customer feature workspace containing History, Report, Analysis, Digit List, Commission, and Winning features.
- Inline Agent and Customer selectors inside feature workspaces to reduce unnecessary intermediate screens.
- Agent “ဒိုင်အားလုံး” aggregation for report/total/winning-style views where aggregation is valid.
- Customer “Customer အားလုံး” aggregation only after a specific Agent has been selected.
- Feature-specific controls kept unavailable for invalid aggregate operations such as Agent-wide closed-number or limit management across all Agents.
- Home bottom navigation for Home, Agent Dashboard, Customer Dashboard, Closed Day, Winning Number, and Settings.

### 16.4 Home screen changes and their purpose

The Home screen was repeatedly simplified to remove duplicated or low-value content. The current intent is:

- Show one clear brand/welcome treatment rather than duplicate `Home`, `Welcome`, or repeated brand headings.
- Keep weekly winning numbers as the main visual information.
- Display five operating weekdays in compact daily cards, with morning and evening result slots separated.
- Make the winning digits visually prominent and tappable.
- Keep Total Bet and Commission out of the default card when they would make the digit unreadable; reveal detail only through the relevant interaction.
- Keep Quick Entry, Add Agent, and Add Customer as the primary Home actions.
- Avoid a long scrolling dashboard for information that should fit the first view on ordinary phone sizes.
- Use localized/professional date presentation rather than ambiguous short dates such as `7.9` and `8.9`.
- Keep bottom-bar labels and icon choices concise enough for narrow screens.

The Home layout still requires practical verification on a real phone for exact Burmese font metrics, narrow widths, large font scale, and edge-to-edge insets. This is a device verification item, not a reason to reintroduce duplicate navigation screens.

### 16.5 Agent Dashboard behavior completed

The Agent Dashboard’s Agent List shows the Agents created through Add Agent, including the Agent name and rate. Selecting an Agent opens that Agent’s information/detail flow and preserves edit capability.

Agent feature behavior is now workspace-based:

- **စုစုပေါင်းစာရင်း / Total:** uses real date/session totals and supports Agent selection or valid Agent-wide aggregation.
- **အစီရင်ခံစာ / Report:** supports an individual Agent and displays Agent-level totals plus customer-by-customer breakdown data, including stake, winning stake, payout, commission, and profit/loss.
- **ပိတ်ဂဏန်း / Closed Number:** adds and removes Agent-specific closed digits. Removing a closed digit requires confirmation.
- **ထီပေါက်စဉ် / Winning:** resolves the global winning number for the selected date/session and calculates the selected Agent’s aggregate result. If the winner is unavailable for an After view, the UI shows an unavailable state rather than inventing a result.
- **ကန့်သတ်ပမာဏ / Limit:** supports Agent-wide limits and Agent-specific special limits. Special-limit removal requires confirmation. Special Limit precedence over All Limit remains a domain rule.

Agent-wide closed-number and limit management is intentionally not offered as an invalid “all Agents” mutation. Aggregate read views and management mutations are treated differently.

### 16.6 Customer Dashboard behavior completed

The Customer Dashboard first requires an Agent selection. Only customers belonging to that Agent are then offered. The Customer selector includes the valid “Customer အားလုံး” aggregate option and individual customers. Changing the Agent resets the Customer selection so a customer from the previous Agent cannot remain selected accidentally.

Customer feature behavior is now workspace-based:

- **စာရင်းမှတ်တမ်း / History:** shows the selected Customer’s real betting entries; edit routes to the existing betting edit flow; delete requires confirmation.
- **အစီရင်ခံစာ / Report:** supports Daily and Weekly views, Before and After modes, daily date/session context, and the required Monday–Friday × Morning/Evening ten-row weekly structure with blank/unavailable rows where appropriate. Weekly totals include stake, commission, payout, and profit/loss.
- **အမြန်သုံးသပ်ချက် / Analysis:** shows current digit count, total stake, limited-digit count, 80% warning count, 90% near-limit count, 100% full/reject count, highest-stake digits, closed digits, reject digits, per-digit winning scenarios, worst-case payout, and worst-case profit/loss.
- **အကွက်စာရင်း / Digit List:** displays the 00–99 grid with current stake, limit-use percentage where available, closed state, and full/limit-related visual state. The grid is a view of real Room-backed data, not placeholder digits.
- **ကော်မရှင် / Commission:** allows the selected Customer’s commission rate to be updated with validation.
- **ထီပေါက်စဉ် / Winning:** shows the global winner and the selected Customer’s stake, winning stake, payout, commission, and profit/loss. Missing winners produce an unavailable state for After results.

### 16.7 Business rules preserved during the simplification

The UI was simplified without changing the underlying business rules:

- Room is the single source of truth.
- Customer belongs to exactly one Agent.
- A date plus DrawSession identifies a draw.
- Global Winning Number is not owned by an Agent or Customer.
- Closed Day is global.
- Closed Number is Agent-specific and is validated after parser/format expansion.
- Special Limit takes precedence over All Limit for the same digit.
- Betting uses `Confirm`; winner forms use `Save` and `Cancel`.
- Duplicate parsed digits are aggregated deterministically.
- Money uses exact integer MMK arithmetic.
- Commission uses deterministic basis-point rates.
- Payout, winning stake, commission, net settlement, and profit/loss remain centralized in domain/repository calculations rather than being independently invented by each Composable.
- Bets are blocked by invalid weekdays, closed days, closed numbers, limits, past-session rules, and an already-entered winner according to the existing ViewModel/repository guards.
- Historical commission uses the stored commission snapshot behavior already added to the data model, so later rate edits do not silently rewrite old financial results.
- Weekly reports always represent five operating weekdays × two sessions = ten rows, including blank/future rows where the domain rules require them.

### 16.8 Safe mutation behavior completed

The implementation includes safe destructive-action handling for the relevant dashboard workspaces:

- Customer betting-entry deletion requires confirmation.
- Agent closed-number removal requires confirmation.
- Agent special-limit removal requires confirmation.
- Closed-day and winner management retain the existing confirmation/edit behavior.
- Relevant ViewModel mutations publish revision changes so report, winning, analysis, and aggregate views refresh after edits.

### 16.9 Verification record and recent corrective fixes

The final pass initially exposed compile errors after the complete inline workspace was added. Those failures were not ignored. The following corrections were made before the final green run:

- Added missing database entity imports for confirmation dialogs.
- Exposed the shared date-format helper to dashboard workspaces.
- Exposed the shared ReportCard composable to dashboard workspaces.
- Removed an incorrect import for DrawReport and used the existing ViewModel model location.
- Re-ran the full GitHub Actions workflow after the corrections.

The final successful commit is `10f4b39`. The successful run `34663015704` verified:

```text
Unit tests                         PASS
lintDebug                          PASS
assembleDebug                      PASS
assembleRelease                    PASS
Debug APK artifact upload          PASS
Release APK artifact upload        PASS
```

A GitHub Actions green result proves the repository compiles, tests, lints, and produces APK artifacts. It does not by itself prove pixel-perfect rendering on every Android device.

### 16.10 Items intentionally outside the verified repository claim

The following must remain explicit rather than being falsely described as complete:

1. A real Android phone/emulator walkthrough has not been performed in this sandbox. The user should install the final APK and test Home, Agent selection, Customer selection, betting confirmation, edit/delete, report, winning, analysis, digit list, Closed Day, Winning Number, and Settings.
2. Device-level Burmese text reflow, narrow-screen layout, large-font accessibility, IME behavior, touch-target sizing, animation performance, and edge-to-edge insets require real-device verification.
3. A tested local JSON backup/restore flow with Android file picker, schema validation, and transactional restore is not shipped. No fake backup button should be added without a complete implementation.
4. Release APK verification is build verification; store-distribution signing and publishing credentials are not configured.
5. Database-level proof against cross-process concurrent bet/winner races is outside the current verified CI scope. The current app-level mutation serialization must not be described as a multi-process database lock.

These are verification or explicitly unshipped-scope items. They must not be converted into vague “feature missing” claims about the completed dashboard/report/analysis workspaces unless a new code audit demonstrates a concrete defect.

### 16.11 Handoff instructions for the next AI

Before changing code, read `/home/ubuntu/upload/architecture.md` and this entire `manus.md`. Treat section 16 as the current source of truth. Do not recreate the removed Agent Selector or Customer Selector route flow merely to add screens; selectors belong inside the existing feature workspaces unless the user explicitly requests a new route.

Do not claim “everything is complete” from a compile-only result. For source changes, run at minimum:

```bash
cd /home/ubuntu/Ledger2D
./gradlew clean testDebugUnitTest assembleDebug --no-daemon
```

Then push `main`, wait for GitHub Actions, and verify the corresponding run has passing unit tests, lint, debug APK, release APK verification, and artifact uploads. If a CI failure occurs, read the exact compiler/test log, fix it, rerun the workflow, and report the actual state rather than hiding the failure.

The next practical action is a user-side APK install and device walkthrough. If that walkthrough reveals a specific UI defect, fix that defect without re-expanding the workflow into unnecessary intermediate screens.
