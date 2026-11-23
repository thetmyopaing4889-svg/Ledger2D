# Cherry 2D — Design Consistency Master Plan (Build 1864)

**Created:** 2026-11-23  
**Status:** APPROVED FOR PLANNING; implementation NOT STARTED.  
**Repository:** `thetmyopaing4889-svg/Ledger2D`  
**Approved baseline:** successful Android CI Build **1864**, commit `f48c8ae8a45d584c8d416196a9cd4404916626ff`  
**Base branch:** `fix/profile-photo-coverage-pass-from-build1861-20261123`  
**Working branch:** `feature/design-consistency-from-build1864-20261123`  
**CI baseline:** https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45159707810

> **NON-NEGOTIABLE:** Cherry 2D ရဲ့ ပြီးထားပြီးသားအပိုင်းတွေကို မဖျက်၊ မပျက်စေဘဲ UI လိုအပ်ချက်ရှိတဲ့နေရာတွေကိုသာ အဆင့်လိုက်ပြင်ရမယ်။ 2D Analysis → **Research Workspace** ဟာ အသုံးပြုသူအတွက် အထူးလက်ဆောင်ဖြစ်ပြီး **လုံးဝမထိရ**။ `main` မှာ မပြင်ရ။
>
> **Read this document BEFORE every new implementation session, branch switch, or continuation.** Update the checkpoint log here after each verified pass. Do not infer approval from CI alone.

---

## 0. Purpose and scope

- Goal: Make active non-protected Cherry 2D Android screens visually consistent in header/back, bilingual typography, cherry accent colors, card styling, spacing, avatar/name alignment, selectors, buttons and feedback.
- This is **UI consistency**, not a rebrand, navigation redesign, data migration, financial behavior change, or rebuild from scratch.
- Use **Home's accepted Cherry brand** and **accepted Agent/Customer Add/Edit Form polish** as reference **only**. The reference screens themselves must not change.
- Existing shared Compose components and design tokens should be **reused selectively**, not globally redefined.
- Source audit found **47 navigation route declarations** in `LedgerNav.kt`; this is not a claim that all 47 are unique current user-facing destinations. Distinguish active, alias and legacy routes before touching any implementation.
- Source audit indicates opportunities for consistency; actual defects and layout acceptance still require screenshots/device QA.

### 0.1 Absolute exclusion / protected files and behavior

**NO CODE CHANGES to any of the following in this workstream:**

1. **2D Analysis → Research**: its UI, typography, colors, cards, tab behaviors, search, guide, formula, research logic, history data, animations, and references. Research is deliberately preserved as the user's special gift.
2. `feature/main/Historical2DTimelineScreen.kt` — shares Analysis/Summary/Rules/Research chrome and navigation. **Freeze the entire file** to avoid indirect Research changes. Also freeze `TwoDResearchLabScreen.kt`, `TwoDResearchGuideScreen.kt`, `TwoDResearchHistoryAuditScreen.kt`, `TwoDResearchEngine` and any research-related code/assets. If a proposed change requires touching this boundary: **STOP, document it, do not silently edit**.
3. **Home**: hero/cherry image, approved 3D/glass Quick Entry, Weekly Card, footer, home dimensions/animations/content. Freeze Home-specific regions in `Screens.kt`, `Home*.kt`, assets and transitions.
4. **Accepted Agent/Customer Add/Edit Form**: `CherryProfileFormUi.kt`, crop flow, saved drafts, photo persistence, header, fields, Back/Save behavior, gradient and layout. Freeze corresponding form functions in `Screens.kt`.
5. **2D Rules Library** approved reader/formula text/examples and Better Data math engine/discovery; do not modify formula, preview, data selection or computation.
6. **JPEG document pixels and rendering**: `VoucherRenderer.kt`, `AgentSettlementJpegRenderer.kt`, `CustomerSettlementJpegRenderer.kt` and pagination/monetary export logic must remain unchanged; only non-renderer selection/preview *UI* may be adjusted in a later separate phase.
7. **Core and business logic**: `core/database/**`, `core/domain/**`, `core/repository/**`, `core/profile/**`, `core/backup/**`, Live result timing/polling, any result source/settlement/commission/payout/limit/validation, Room and saved data, app performance lifecycle.
8. `LedgerNav.kt`: no route changes or changed back-stack behavior in a styling pass; no broad `Theme.kt`/global Typography changes without explicit separate review.

**Mixed-file safety:** `Screens.kt` includes both protected Home/Form implementation and unprotected operational screens. When needed, change only the exact operational function and verify protected source ranges remain identical. Do not reformat/rewrite the entire file.

### 0.2 Repository workflow

1. Preserve Build 1864 as recovery baseline. Never work directly on `main` or amend the baseline branch.
2. **First commit only this plan** on the dedicated working branch. No styling/UI implementation is included in the documentation commit.
3. Before changing any UI file, inspect the active route, its call sites and the accepted shared component contracts.
4. Each small UI pass: scoped changes → code review / tests → Android CI debug+lint+unit tests+release APK → phone screenshot comparison → acceptance or local revert.
5. Do not declare a screen approved from green CI alone. Screenshot/device confirmation is distinct from successful compilation.
6. If a screen is already consistent, record **NO CHANGE** and move on.
7. After every pass, append the build/run/commit, files changed, screenshot checklist, regressions and user acceptance to Section 9.
8. Later sessions MUST read this file and the latest completed checkpoint; continue from latest accepted commit/build, never an older branch or `main`.

---

## 1. Current design system (reuse, do not redesign)

Source paths:
- `core/design/Theme.kt` — `AppColors`, `AppDimens`, `AppShapes`, `LedgerTheme`.
- `core/design/LedgerTypographyStandard.kt` — EN SansSerif / MM bundled Noto Sans Myanmar UI, role-based font styles.
- `core/design/LedgerControlDimensions.kt` — selector min 68dp, selected-value/label sizes, spacing, FAB safe bottom.
- `feature/main/Screens.kt` — `AppScaffold` / `OperationalScaffold`: centered title, normal Back and global Quick Entry FAB.
- `feature/main/DashboardScreens.kt` — `DashboardLandingCard`, `ScopeDropdown`, `SelectionCard`.
- `feature/main/DashboardCardTypography.kt` — approved Agent/Customer dashboard typography pilot.
- `feature/main/CherryProfileIdentity.kt` and `CherryProfileAvatar` — correctly scoped agent/customer avatar + name.

**Current palette:** `AppColors.Primary #FF2D55`, `PrimaryDeep #9F1239`, `Blush #FFF3F7`, `Champagne #FFFFFF`, `Ink #24191E`. Existing Home/Form accents are approved. Financial positive/negative/error/closed/limit colors retain semantic meaning; do not convert all financial states to cherry red.

**Typography:** existing roles include `APP_HEADER 19sp`, `SECTION_TITLE 17sp` (Myanmar role may use 16sp), `BODY 14sp`, `HELPER 13sp`, `BUTTON 14sp`, `MONEY 18sp`. Dashboard card pilot uses title 14sp and subtitle 11sp. Test actual Burmese readability rather than assuming smaller font is acceptable.

**Geometry:** existing app screen padding token 18dp; some approved landing/list workspaces use 16dp, forms use their own 17dp or 15dp geometry. Do not blindly search-and-replace 14/16/18dp everywhere. Favor local alignment within an existing screen family.

**Components:**
- Standard operational screen: existing centered `AppScaffold`; do not change it globally during this pass.
- Standard agent/customer picker: existing `ScopeDropdown` (min 68dp, avatar optional); preserve the guard against displaying stale IDs.
- Standard operational identity: `CherryProfileIdentity` or `CherryProfileAvatar`, only for positive individual IDs, never for an aggregate -1/0 selection.
- Cards: dashboard landing cards stay approved white/small-elevation style. Form keeps unique cherry glass. Finance/report tables favor readability, not unnecessary 3D effects.
- Buttons: align primary/secondary/destructive roles within each screen group, respecting existing device-approved forms and accessibility hit targets.
- Back/top actions: no duplicate Back button, oversized circle, logo overlap, or changed back semantics.
- Feedback: consistent localized loading/empty/error/success visuals without debug text or unnecessary prose.

---

## 2. Workstream A — Agent/Customer management (FIRST)

**Order:** Agent + Customer Dashboards → Lists → Information → Quick Entry → Settings/Add Customer from Home.

| ID | Active screen | Source files (candidate) | Check / planned UI-only correction | Preserve |
|---|---|---|---|---|
| A01 | Agent Dashboard | `DashboardScreens.kt`, `DashboardCardTypography.kt` | Card title/subtitle/bilingual wrapping, icon/chevron alignment, card padding/height, list spacing | Existing card order, route targets, approved shared landing-card structure |
| A02 | Customer Dashboard | same | Match Agent Dashboard's equivalent card geometry; verify Burmese long labels and font scale | Existing feature keys and routes |
| A03 | Agent List | `ListWorkspaceScreens.kt` | Avatar 42dp alignment, search box, card spacing, long names, Add action, empty state | Search behavior, selection, records, list tap |
| A04 | Customer List | `ListWorkspaceScreens.kt` | Match Agent List card geometry, agent picker, avatar, phone/address text handling | Agent/customer filtering, add/selection |
| A05 | Agent Information | `ListWorkspaceScreens.kt` | 104dp avatar/header, label/value alignment, report/customer navigation buttons, title | Details, agent rates, links |
| A06 | Customer Information | `ListWorkspaceScreens.kt` | Shared `InformationCard`, label/value alignment, long text/phone, edit action | Details, customer relationships |
| A07 | Quick Entry | **only** `QuickEntryScreen` / `SelectionField` regions in `Screens.kt` | Two scope selectors with portraits, name overflow, selection-summary wrap, Continue button, narrow screen & keyboard | Selected IDs, default selection, route, betting behavior |
| A08 | Settings | **only** `SettingsScreen` region in `Screens.kt`, `CherryBackupSettings.kt` if relevant | Group headings, default selectors, history update feedback, language/backup row spacing and button hierarchy | Locale state, default IDs, backup/restore functionality |
| A09 | Add Customer from Home | `DashboardScreens.kt` | Agent picker, Continue action, empty state, safe padding | Route to existing approved Customer Form |
| A10 | Agent/Customer Add/Edit Form | `Screens.kt` form regions, `CherryProfileFormUi.kt` | **NO CHANGES**; screenshot regression only for avatar/crop, frame, back/save, keyboard | All approved form UI and data behavior |

**Acceptance for A:** both dashboards read as one family; list/info cards and avatar positions are predictable; MM/EN titles do not clip; selectors show the correct person or no person on aggregate; all navigation/save behavior remains intact.

---

## 3. Workstream B — Financial operations (SECOND)

| ID | Screen | Candidate files | Check / planned UI-only correction | Preserve |
|---|---|---|---|---|
| B01 | Betting Add/Edit | `Screens.kt` `BettingScreen` region | Two-line title, identity header, Date/Session/input layout, sticky action/keyboard insets | All parsing, preview, submit/edit, historical-rate rules |
| B02 | Bet History | `Screens.kt` `BetHistoryScreen` region, `BetEntriesWorkspace.kt` | Customer identity, date/session chips, voucher action, long/empty history | Entry sort, edit/delete, voucher selections |
| B03 | Today Ledger | `OperationalScreens.kt` | Agent group header, amount alignment, list density, bottom-nav/FAB clearance | Existing totals and entries; **no photos in transaction rows** |
| B04 | Agent Digit Totals | `AgentDigitTotalsScreens.kt` | Group identity, table header, digit/amount columns, controls | Digit and amount calculations |
| B05 | Customer Digit Views / 00–99 | `CustomerDigitViewsScreen.kt` | Five-column/narrow grid legibility, chips, sort/filter, no clipped amounts | All numbers and stake values |
| B06 | Agent Ledger Report | `AgentStatementScreens.kt`, `AgentLedgerReportResults.kt` | Group header photo/name, table labels and numerals, horizontal scrolling, summary hierarchy | Page structure, monetary results, filters |
| B07 | Customer Ledger Report | `CustomerStatementScreen.kt`, `CustomerLedgerReportResults.kt` | Match Agent report for tables, font, header, interval selection | Same calculations and group order |
| B08 | Commission Review | `CommissionReviewScreen.kt` | Selector/period/session controls, expand/collapse card, total highlight | Commission amount calculations |
| B09 | Customer Commission/Limit | `Screens.kt` selected regions | Identity row, field label and button hierarchy, error state | Rate/basis-point logic, stored limits |
| B10 | Agent Closed/Limit | `AgentPatternRulesScreens.kt` and caller | Header, input/status/Date/Session alignment | Closed/limit persistence and logic |
| B11 | Risk Analysis | `CustomerReviewScreens.kt`, `CustomerRiskReviewResults.kt` | Group summary hierarchy, filters, long names, money contrast | Risk/coverage calculation |

**Financial acceptance:** amounts and totals are unchanged; numeric values are never ellipsized; money columns align consistently; group rows can have avatars, individual transaction/amount rows cannot.

---

## 4. Workstream C — Export and JPEG preview UI (THIRD)

| ID | UI | Candidate file | Check / planned correction |
|---|---|---|---|
| C01 | Voucher selection/preview | `VoucherPreviewPlaceholderScreen.kt` and history entry *UI* | Preview scaling, page navigation, Export/Save action, status dialog, safe layout |
| C02 | Agent settlement picker/preview | `AgentSettlementJpegScreen.kt` | Scope selector, period fields, generate/loading actions, preview page controls |
| C03 | Customer settlement picker/preview | `CustomerSettlementJpegScreen.kt` | Mirror C02 selection/preview geometry; avoid bottom/action clipping |
| C04 | Gallery feedback | UI only | Localized success/error messages, no duplicate action, no silent failure |

**Hard rule:** Do **not** edit `VoucherRenderer.kt`, `AgentSettlementJpegRenderer.kt`, `CustomerSettlementJpegRenderer.kt`, exporter or pagination/math classes. Validate exported JPEG amounts/pages/photos remain identical to Build 1864 for identical fixtures.

---

## 5. Workstream D — Specialized screens (LAST, review-first)

- `LiveScreen.kt`: inspect title/Back, Live vs Calendar controls, status contrast and bottom navigation. **Do not alter SET timing, API, collector or caching**.
- `CherryBetterDataScreen.kt`: custom TopAppBar with next-draw subtitle and custom 4-tab bottom bar is intentional; test labels/contrast only. **No formula engine or discovery modifications**.
- `Historical2DTimelineScreen.kt`: **NO CHANGE** because it shares Research UI and navigation. Other Analysis/Summary/Rules design proposals are deferred instead of risking Research.
- `TwoDRulesLibraryScreen.kt` and content/assets: **NO CHANGE** without a new explicit scope; approved rules wording and reader layout protected.
- Welcome/Home and all existing home art: **NO CHANGE**.

---

## 6. Per-screen visual QA checklist

For each changed screen, record a before/after screenshot and verify:

1. Header/back consistent with its *screen family*, never obscures controls/logo; back behavior unchanged.
2. Burmese/English fonts readable, no cut glyphs, unintended truncation, overlap, awkward 1-line overflow or mismatched baselines.
3. Narrow-device width + normal font scale + increased accessibility font scale; long and empty agent/customer names.
4. Cards: consistent surface, border/shadow, corner radius, touch area and internal padding; avoid redundant wrappers.
5. Scope selector: 68dp minimum where standard, visible selected option, correct photo when ID exists; no stale customer portrait after agent switch.
6. Selected aggregate scope (-1) and unselected scope (0) must not show another person's portrait.
7. Action: Save/Cancel/Continue/Export button hierarchy, enabled/disabled states, touch targets and no duplicate FAB.
8. Keyboard open/close, small-screen scroll, bottom bar/FAB interference and dialogs.
9. Empty/loading/error/success states localized; no demo/debug/placeholder language.
10. Tables: right-aligned monetary values, stable digit-width, visible full totals, meaningful semantic win/loss/closed colors.
11. Portrait add/change/remove survives navigation; no accidental photo in transaction rows.
12. Existing list search, category filters, period/session selection, back navigation and screenshots remain functionally correct.

**Screenshots are required for real-device visual sign-off.** Android unit tests/lint alone do not demonstrate layout consistency.

---

## 7. Implementation constraints / code review gates

- **Allowed:** targeted presentation Composables and local layout constants within A/B/C workstreams.
- **Avoid:** broad find/replace; reformatting entire `Screens.kt`; rebuilding shared `Scaffold`/Theme/Typography globally; modifying navigation keys/routes or state; adding dependencies.
- **Screen-specific first:** if a shared component could alter a protected screen, use local wrapper/arguments instead.
- Reuse existing design roles and UI elements; avoid adding a second competing design system.
- Preserve existing business methods, database queries, state flow subscriptions, performance, accessibility and localization.
- Before every commit, inspect `git diff --name-only` (or GitHub compare). Explicitly reject changes in frozen paths, Home/form sections and Research.
- Tests: `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`, then release verification through existing workflow; add UI-contract tests for modified components where practical.
- CI sign-off: successful build, tests and uploaded debug/release APK; then user-visible phone QA and approval required.
- If an approved screen regresses: isolate/revert *the UI-only pass*, never fix it by changing financial logic.
- When `Screens.kt` is changed, compare protected Home/Form code ranges against Build 1864; passing CI is insufficient.

### 7.1 Source audit pointers (as of baseline)

- `LedgerNav.kt` currently declares 47 routes; route names should be checked for active vs legacy use.
- `DashboardLandingCard` is already shared between Agent and Customer Dashboard (minimum height 78dp, white surface, low elevation); **preserve it** unless device evidence shows a mismatch.
- `ScopeDropdown` uses a 68dp minimum selector and optional avatar, including stale-ID guard; do not regress.
- `AgentListRow` and Customer List already use profile pictures; do not duplicate them.
- `InformationCard` is shared; don't rewrite both screens for an already-matched layout.
- `AppScaffold` provides a shared centered bar, but Better Data and 2D Analysis use intentionally specialized chrome.
- `LedgerTheme` already selects Noto Sans Myanmar UI for Myanmar; do not force another font.
- Previous source audit found scattered 14/16/18dp padding and hardcoded colors in some screens. This is a **review signal, not proof of a defect**.

---

## 8. Milestones and stop conditions

| Milestone | Exit gate | Status |
|---|---|---|
| M0 — Plan committed | This plan exists on isolated working branch, no UI changes in same commit | DONE — verified plan-only diff |
| M1 — Management audit and UI changes | A01–A09 checked/fixed or explicitly NO CHANGE; accepted Form untouched; CI+device QA | NOT STARTED |
| M2 — Financial controls/report UI | B01–B11 checked/fixed; numeric totals identical to baseline; CI+device QA | NOT STARTED |
| M3 — Export/preview controls | C01–C04 checked; JPEG pixel/page and money invariants pass; CI+device QA | NOT STARTED |
| M4 — Specialized UI review | Live/Better Data differences reviewed; protected Research/Library untouched | NOT STARTED |
| M5 — Final regression | Cross-screen MM/EN, small device, large font, photo, scroll, finance, export, no protected paths | NOT STARTED |

**Stop and seek explicit scope change** if the only possible fix touches Research, the shared Research/Analysis parent, Home/Form approved regions, global font/theme, domain calculations or renderers. Never silently expand scope.

---

## 9. Persistent progress / handoff log

This section is the **source of truth for future sessions**. After a passed milestone, append:
- Date, phase/IDs and latest approved build number
- Working branch + exact commit SHA + GitHub Actions run link
- Exact files changed (and statement that protected files were excluded)
- Device screenshot findings (MM/EN, narrow width, large font) and any regressions
- Which tasks are completed, which are intentionally NO CHANGE, what remains
- Whether the user has accepted the build

**Initial checkpoint, 2026-11-23:**
- Base Build **1864** = SUCCESS, commit `f48c8ae8a45d584c8d416196a9cd4404916626ff`.
- Plan created before any styling changes, on `feature/design-consistency-from-build1864-20261123`.
- Plan-only commit `2cfb6cadfea66ce0ecd67e2cc3bdfbba0d94ac06` verified against Build 1864: **one added docs Markdown file; zero UI/code changes**.
- Last completed user-visible task = profile photo integration/coverage (Build 1864).
- No design consistency UI tasks have been implemented or accepted yet.
- **Next action:** verify this plan-only commit, then tackle **A01/A02 Agent/Customer Dashboard** first, preserving accepted shared landing-card design. Continue A03/A04 lists and A05/A06 information cards only after checks. Update this log after each accepted APK.

---

## 10. Existing references to read before touching UI

- `docs/ui/AgentCustomerDashboardCardsPilot.md`
- `docs/ui/ConsolidatedBilingualUiRollout.md`
- `docs/ui/TypographyStandardPilot_v1.md`
- `docs/home-premium-polish-from-build1828-20261122.md` (historical protected Home rationale)
- `docs/2D-RESEARCH-WORKSPACE-SPEC.md` (**read-only**; do not change Research)
- `docs/UX-IA-REDESIGN-PLAN.md` (historical proposal, **not authorization** to change routes)

**User decision:** preserve the finished work, protect the special Research gift unconditionally, write the plan into the repository first so future work cannot forget the constraints, and do UI changes one safe phase at a time.
