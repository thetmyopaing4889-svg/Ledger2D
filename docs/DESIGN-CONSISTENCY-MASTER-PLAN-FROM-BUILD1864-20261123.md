# Cherry 2D — Home-First Design Consistency Master Plan (Build 1864 baseline / Build 1865 checkpoint)

**Created:** 2026-11-23  
**Status:** A01/A02 user-approved Build 1871; A03–A09 passed CI, combined device QA deferred; B01–B03 Build 1886, B04–B05 Build 1890, B06–B07 Build 1899, B08 Build 1903 all CI SUCCESS. B09 Customer Commission/Limit glass UI and tests committed, CI and combined device QA pending. Protected Home/Form/Research, database/domain, and financial math unchanged.  
**Repository:** `thetmyopaing4889-svg/Ledger2D`  
**Approved baseline:** successful Android CI Build **1864**, commit `f48c8ae8a45d584c8d416196a9cd4404916626ff`  
**Base branch:** `fix/profile-photo-coverage-pass-from-build1861-20261123`  
**Working branch:** `feature/design-consistency-from-build1864-20261123`  
**CI baseline:** https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45159707810  
**Latest completed pilot CI:** Build **1865**, commit `1e7d4a375193cd4b5c10289f087cefa35ccde35e`, https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45174179816 — successful (tests/lint/debug/release, both APK artifacts). **Device visual acceptance has not occurred.**  
**Home-first decision:** The approved CURRENT Home appearance is the **primary visual benchmark**, not merely a cherry color reference. Supersedes the prior A01/A02 `NO CHANGE` conclusion.

> **NON-NEGOTIABLE:** Cherry 2D ရဲ့ ပြီးထားပြီးသားအပိုင်းတွေကို မဖျက်၊ မပျက်စေဘဲ UI လိုအပ်ချက်ရှိတဲ့နေရာတွေကိုသာ အဆင့်လိုက်ပြင်ရမယ်။ 2D Analysis → **Research Workspace** ဟာ အသုံးပြုသူအတွက် အထူးလက်ဆောင်ဖြစ်ပြီး **လုံးဝမထိရ**။ `main` မှာ မပြင်ရ။
>
> **Read this document BEFORE every new implementation session, branch switch, or continuation.** Update the checkpoint log here after each verified pass. Do not infer approval from CI alone.

---

## 0. Purpose and scope

- Goal: Carry the **approved/current Home's Cherry Premium visual identity and perceived build quality** into active, non-protected app screens: translucent/frosted surfaces where appropriate, ruby shadow depth, soft blush backgrounds, light rim highlights, cherry icon accents, balanced rounded corners, bilingual typography, spacing, avatars, navigation and feedback. Ensure consistency *with Home first* and then between equivalent Agent/Customer screens.
- This is **UI consistency**, not a rebrand, navigation redesign, data migration, financial behavior change, or rebuild from scratch.
- **Home is the mandatory primary design reference (visual north star)**, not an optional inspiration. Existing accepted Agent/Customer Add/Edit Form supplies a secondary example of a premium glass form treatment. The reference Home/Form screens **remain completely frozen**. Home-first parity is mandatory for all eligible screen families, with density/readability exceptions explicitly documented below.
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

## 0A. APPROVED DECISION — HOME-FIRST PREMIUM DESIGN CONTRACT (2026-11-23)

**User correction:** The earlier plan compared Agent and Customer Dashboards only with each other, declared them matching, and incorrectly treated A01/A02 as `NO CHANGE`. This was **incomplete**. **Home is the primary standard for all other eligible screens.** A01 and A02 must be redesigned to convey the same premium Cherry quality, while keeping their current feature order, names, icon semantics, routing, and readable information density.

### 0A.1 Actual current Home source evidence (read only; preserve exact appearance)

| Home element | Existing code | Extractable design rule |
|---|---|---|
| Main shell / atmosphere | `Screens.kt` `HomeScreen`: `#FCEEF3` background; content gradient `#F6DCE5 → #F9E3EB → #FCEEF3`; sheet top corners 30dp | Warm cherry/blush layered background with a deliberate sense of depth; NOT a generic flat grey/white content screen |
| Stats glass | `HomeCountCard`: `homeRubyShadow(23.dp)`, rounded 23dp, `#EBFFFFFF` white translucency, white 1.5dp rim | One unified translucent glass surface with soft ruby depth; no nested opaque child Card hiding the effect |
| Weekly glass | `HomeWeeklyResults`: `homeRubyShadow(24.dp)`, translucent white glass and white rim, 24dp corners | Spacious high-contrast card with coherent cherry accents; preserve weekly interactions and status colors |
| Commission glass | `HomeCommissionCard.kt`: `homeRubyShadow(19.dp)`, `#EBFFFFFF` surface, 1.5dp white rim | Premium depth on a readable *summary*, not a shadow on each number/transaction |
| Live hero | `HomeLiveAndSummary`: 23dp rounded card, image-backed silk art, angled white bezel, soft shadow, press-scale | Special visual treatment is unique to Home hero; DO NOT copy silk image, giant glow, or hero animation onto ordinary forms/reports |
| Navigation glass | `HomeBottomBar`: blush/white vertical glass gradient, white rim, soft shadow, rounded upper 26dp, dedicated cherry 78dp FAB | Cherry-branded atmosphere and safe insets, but DO NOT replicate Home's unique center orb/bottom dock on inner screens |
| Quick action | `HomeQuickAction`: cherry-tinted rounded/circular light surface, gradient/shadow, 45dp icon area, restrained touch scaling | Lightweight icon treatment and responsive touch affordance, not a permanent bulky 3D button everywhere |
| Detail form (secondary accepted reference) | `CherryProfileFormUi.kt`: soft blush gradient, 24dp gradient/glass pane, shadow and rim, pinned avatar and 54dp bottom actions | Premium form treatment is already accepted; DO NOT edit or wholesale copy its save/crop mechanics |

**Reference source files:** `Screens.kt` (Home-only functions), `HomeCommissionCard.kt`, `HomeGlassDepth.kt`, `HomeMicroAnimations.kt`, `HomeTypography.kt`, and accepted `CherryProfileFormUi.kt`. These are **read-only visual references** in this workstream. `homeRubyShadow` is existing Home code; use a narrowly scoped look-equivalent component for other screens if necessary and do not alter the original helper or accidentally increase cost on scrolling financial lists.

### 0A.2 Explicit visual translation, NOT literal clone

1. **Shell:** Inner operational screens should feel cherry-blush rather than flat stock Material, **without replacing their shared centered Header/Back or showing Home's hero artwork**. Preserve keyboard, system bar and FAB behavior.
2. **Elevated action/card:** Use a subtle single-surface frosted/translucent treatment with a restrained white edge and berry-shadow depth for key Dashboard/Card/Summary surfaces. Avoid double backgrounds and heavy blur; test clipping and scroll performance.
3. **Card icons:** Cherry/Wine accent, soft pink icon container, balanced icon/name/chevron alignment. Do not invent new icons or change destination meaning.
4. **Type:** Existing English SansSerif / Noto Sans Myanmar UI and approved type roles remain baseline. Do not shrink Burmese to force an opaque card into 78dp; allow height to grow where 2-line text and font scaling require.
5. **Controls:** Selector label/value, avatar circle, filters and primary actions share shape/spacing/cherry focus. Maintain distinct selected/unselected/disabled/error states and 68dp selector minimum.
6. **Motion:** Only modest touch feedback on eligible action cards, optional after phone QA; no Home-specific bubble/orb/shimmer animations duplicated into each list item. Avoid slow global startup or continuous recomposition.
7. **Content hierarchy:** Premium card at *navigation/summary level*; internal transaction rows, 00–99 grids, ledger amount columns and tabular numeric data remain high-contrast, stable, readable and restrained.
8. **Protection:** Keep Home, accepted Forms, Research and core engine/data logic completely unchanged. A design match must be accomplished **outside** protected source.

### 0A.3 Family-by-family visual target (compared against Home)

| Family / IDs | Current source-level contrast | Home-first target | Limits |
|---|---|---|---|
| **Dashboard A01/A02** | Shared `DashboardLandingCard` is opaque white `ElevatedCard`, medium shape 18dp, 1dp elevation; plain `AppScaffold` theme background. Both dashboards MATCH EACH OTHER but NOT Home's frosted depth. | **P1 REDESIGN REQUIRED:** cherry-blush inner screen atmosphere + restrained Home-like glass surface, ruby shadow, rim highlight, premium icon cell and press affordance. Keep same shared component, icon/title/description/chevron, order and click destinations. | Never touch Home; retain dynamic card height and proper text wrapping; no heavy Home hero effects |
| **Lists A03/A04** | Current `ListWorkspaceScreens.kt`: opaque ElevatedCard/ListItem, avatars 42dp, plain search and add buttons | Cherry glass/soft surface on **list cards and surrounding chrome**; consistent avatar and search/action styling as secondary tier below dashboard | Avoid shadow/blur per item that makes long scroll lag; data order/search unchanged |
| **Information A05/A06** | `InformationCard` is one opaque ElevatedCard; Build 1865 aligned Customer loading/scrolling and weighted detail labels | Premium profile shell and card surface, 104dp avatar, readable detail rows; keep Build 1865 wrapping/scroll fix | Long address/remark, accessibility scale, existing editing and agent rate logic unchanged |
| **Quick Entry A07** | Shared ScopeDropdown and person-photo selection/summary; plain operational shell | Home-inherited cherry/glass **selection group** and premium continue action, stable image/name, no layout crowding | Do not change Home's original Quick Entry FAB, parsing or routing |
| **Settings A08** | OutlinedCard, ScopeDropdown and system controls | Cherry-tinted grouped setting panels and clear button/section hierarchy | Languages/backup/history update functionality stays intact |
| **Add Customer A09** | Standalone agent picker and continue screen | Consistent selection-pane depth, CTA, empty state | Route to protected form unchanged |
| **Financial B01–B11** | Mostly stock AppScaffold, standard cards/filters plus separately themed report tables | Blush atmosphere; premium **section header, selected scope and high-level totals**; consistent cherry icons/controls. Table core stays neutral/clear with semantic money colors | No shadow on every row, no numeric truncation; no calculation/transaction change |
| **Export C01–C04** | Functional selectors and previews on standard operational surfaces | Premium selection/toolbar/button chrome surrounding preview; subtle blush backdrop | Exported JPEG pixels/layout/pagination/math completely frozen |
| **Live / Better Data D** | Specialized navigation and layout for live feed and 4 Better Data tabs | Review contrast/title/chrome for cherry feel only if safe; preserve specialized navigation and mechanics | No Live result timing/SET integrations or formula engine changes |
| **2D Analysis → Research** | Intentionally distinct Research visuals and shared timeline parent | **NO CHANGE, NO EDIT, NO RESTYLE** | Entire shared `Historical2DTimelineScreen.kt` and all Research sources frozen |

**Interpretation:** An existing shared component being identical on two screens is **not** sufficient evidence of Home-first consistency. `NO CHANGE` is allowed only after comparing the component's *visual quality* to Home and explicitly explaining why a functional/readability exception is appropriate.

### 0A.4 Workflow order and proof

- **First implement A01/A02** in a small **Dashboard-only premium pass** after this documentation-only correction is committed. Add tests to protect shared cards/feature routes and preserve accessibility. CI + Android screenshot review.
- Then **revisit A03/A04 and A05/A06** with the same approved premium treatment. Do not undo the Build 1865 Information scrolling, loading, or label-width fix.
- Then A07–A09; only after Management acceptance move to B, C, D family-by-family.
- For each pass save **Home reference screenshot vs target screenshot** at the same device scale, plus Myanmar/English and larger font scale, instead of just comparing Agent vs Customer.
- Home source code is a standard to *read*, not a screen to modify. Prefer limited local style props / dedicated non-Home Compose visual helper over global Theme changes.
- **Build 1865** CI succeeded (unit tests, lint, debug/release APK). That confirms compilation and packaging, **not** that the Dashboard matched Home or that the phone signed off on any Management visuals.

---

## 1. Current design system (reuse, do not redesign globally)

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
- Cards: **dashboard landing cards must be visually upgraded to the current Home premium Cherry Glass language** (A01/A02 are no longer `NO CHANGE`). Keep their shared route/content structure. Forms remain untouched; finance/report tables keep neutral numeric readability and receive premium treatment only on outer panels/summary headers.
- Buttons: align primary/secondary/destructive roles within each screen group, respecting existing device-approved forms and accessibility hit targets.
- Back/top actions: no duplicate Back button, oversized circle, logo overlap, or changed back semantics.
- Feedback: consistent localized loading/empty/error/success visuals without debug text or unnecessary prose.

---

## 2. Workstream A — Agent/Customer management (FIRST)

**Order:** Agent + Customer Dashboards → Lists → Information → Quick Entry → Settings/Add Customer from Home.

| ID | Active screen | Source files (candidate) | Check / planned UI-only correction | Preserve |
|---|---|---|---|---|
| A01 | Agent Dashboard | `DashboardScreens.kt`, `DashboardCardTypography.kt` | **REQUIRED PREMIUM UPGRADE:** compare against Home glass, blush atmosphere, ruby shadow, white rim, rounded card, cherry icon and restrained touch feedback; ensure EN/MM text fits | Existing card order, feature labels, route targets, 78dp minimum unless text requires more height |
| A02 | Customer Dashboard | same | **REQUIRED PREMIUM UPGRADE:** same new Home-derived shared premium card as Agent; visually compare with Home, not only A01 | Existing feature keys, names, routing, long descriptions, scrolling |
| A03 | Agent List | `ListWorkspaceScreens.kt` | Compare opaque ElevatedCard with Home glass; premium lightweight list card, tint/rim and avatar 42dp alignment; search/add/empty state | Fast scrolling, search behavior, selection, records, list tap |
| A04 | Customer List | `ListWorkspaceScreens.kt` | Match Home-derived A03 visual tier, agent picker/portrait, phone/long name layout and add action | Agent/customer filtering, add/selection |
| A05 | Agent Information | `ListWorkspaceScreens.kt` | Upgrade opaque detail card to premium profile surface, 104dp avatar, contrast, label/value alignment; retain Build 1865 wrapped detail rows | Details, agent rates, links, scrolling and loading |
| A06 | Customer Information | `ListWorkspaceScreens.kt` | Use same Home-derived profile card tier as A05, preserve Build 1865 stable loading + scroll and long detail rows | Details, customer relationships, editing and photo |
| A07 | Quick Entry | **only** `QuickEntryScreen` / `SelectionField` regions in `Screens.kt` | Premium Home-derived selection-pane surface, proper photo/name overflow and cherry CTA; narrow screen & keyboard | Selected IDs, defaults, navigation, betting behavior, Home Quick Entry design |
| A08 | Settings | **only** `SettingsScreen` region in `Screens.kt`, `CherryBackupSettings.kt` if relevant | Premium grouped cherry panels and accent contrast on default selectors, history and language/backup rows | Locale state, default IDs, backup/restore functionality |
| A09 | Add Customer from Home | `DashboardScreens.kt` | Home-derived selection panel and Continue button styling, empty state, safe padding | Route to existing approved Customer Form |
| A10 | Agent/Customer Add/Edit Form | `Screens.kt` form regions, `CherryProfileFormUi.kt` | **NO CHANGES**; screenshot regression only for avatar/crop, frame, back/save, keyboard | All approved form UI and data behavior |

**Acceptance for A:** Dashboard **and** List/Information/Quick Entry/Settings premium visual language is recognizably descended from the **current Home**, not just mutually identical. Agent/Customer screens still match each other; MM/EN text and avatars are readable, all routes/data preserved, performance stays smooth. Each pass needs Home-vs-screen phone screenshots.

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

**Home-derived financial styling:** use a restrained cherry glass/background treatment on *filters, section shells, selected scope and summary/total cards only*; inner rows, figures, grid cells and tables remain neutral and high-contrast. Do not use a translucent background behind every line. **Financial acceptance:** amounts and totals are unchanged; numeric values are never ellipsized; money columns align consistently; group rows can have avatars, individual transaction/amount rows cannot.

---

## 4. Workstream C — Export and JPEG preview UI (THIRD)

| ID | UI | Candidate file | Check / planned correction |
|---|---|---|---|
| C01 | Voucher selection/preview | `VoucherPreviewPlaceholderScreen.kt` and history entry *UI* | Preview scaling, page navigation, Export/Save action, status dialog, safe layout |
| C02 | Agent settlement picker/preview | `AgentSettlementJpegScreen.kt` | Scope selector, period fields, generate/loading actions, preview page controls |
| C03 | Customer settlement picker/preview | `CustomerSettlementJpegScreen.kt` | Mirror C02 selection/preview geometry; avoid bottom/action clipping |
| C04 | Gallery feedback | UI only | Localized success/error messages, no duplicate action, no silent failure |

**Home-derived export styling:** use cherry-blush pane / button / picker hierarchy in selection and preview chrome, never tint or manipulate the pixels of the exported document. **Hard rule:** Do **not** edit `VoucherRenderer.kt`, `AgentSettlementJpegRenderer.kt`, `CustomerSettlementJpegRenderer.kt`, exporter or pagination/math classes. Validate exported JPEG amounts/pages/photos remain identical to Build 1864 for identical fixtures.

---

## 5. Workstream D — Specialized screens (LAST, review-first)

- `LiveScreen.kt`: inspect title/Back, Live vs Calendar controls, status contrast and bottom navigation. **Do not alter SET timing, API, collector or caching**.
- `CherryBetterDataScreen.kt`: custom TopAppBar with next-draw subtitle and custom 4-tab bottom bar is intentional; test labels/contrast only. **No formula engine or discovery modifications**.
- `Historical2DTimelineScreen.kt`: **NO CHANGE** because it shares Research UI and navigation. Other Analysis/Summary/Rules design proposals are deferred instead of risking Research.
- `TwoDRulesLibraryScreen.kt` and content/assets: **NO CHANGE** without a new explicit scope; approved rules wording and reader layout protected.
- Welcome/Home and all existing home art: **NO CHANGE**.

---

## 6. Per-screen visual QA checklist

For each changed screen, record a **current Home reference screenshot** and the screen's before/after screenshot; verify:

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
13. Against current Home: cherry/blush backdrop, premium single glass surface, softened ruby shadow, white rim and radius feel consistent in the **appropriate** component tier.
14. Dashboard and List cards do not look like unrelated stock Material white tiles; verify translucent surface is visible against blush and does not introduce opaque nested layers.
15. Performance: long Lists/Reports do not stutter from per-row Blur/Shadow/Animation; no Home orb/silk assets copied into standard screens.
16. Home, Form, Research frozen: compare changed path list and protected code ranges; no global theme/typography/regression.

**Screenshots are required for real-device visual sign-off.** Android unit tests/lint alone do not demonstrate layout consistency.

---

## 7. Implementation constraints / code review gates

- **Allowed:** targeted Home-derived premium presentation (background, glass card surface, white rim, ruby depth, local icon/CTA treatments) in eligible A/B/C screen components and local layout constants. **Home source and UI still frozen.**
- **Avoid:** broad find/replace; reformatting entire `Screens.kt`; rebuilding shared `Scaffold`/Theme/Typography globally; modifying navigation keys/routes or state; adding dependencies.
- **Screen-specific first:** if a shared component could alter a protected screen, use local wrapper/arguments instead.
- Reuse existing design roles and UI elements; avoid adding a second competing design system. Do not declare a screen consistent solely because its Agent/Customer equivalent matches; **Home comparison is mandatory**.
- Preserve existing business methods, database queries, state flow subscriptions, performance, accessibility and localization.
- Before every commit, inspect `git diff --name-only` (or GitHub compare). Explicitly reject changes in frozen paths, Home/form sections and Research.
- Tests: `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`, then release verification through existing workflow; add UI-contract tests for modified components where practical.
- CI sign-off: successful build, tests and uploaded debug/release APK; then user-visible phone QA and approval required.
- If an approved screen regresses: isolate/revert *the UI-only pass*, never fix it by changing financial logic.
- When `Screens.kt` is changed, compare protected Home/Form code ranges against Build 1864; passing CI is insufficient.

### 7.1 Source audit pointers (as of baseline)

- `LedgerNav.kt` currently declares 47 routes; route names should be checked for active vs legacy use.
- `DashboardLandingCard` is already shared between Agent and Customer Dashboard (minimum height 78dp, opaque white surface, 1dp elevation); preserve the **shared composable's functional contract**, but its **flat styling needs upgrading toward Home premium glass**. A01/A02 `NO CHANGE` was formally withdrawn on 2026-11-23.
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
| M1 — Home-first Management audit and UI changes | Home-derived A01–A09 visual design and tests; protected Form untouched; CI+combined device QA | SOURCE/CI DONE — A01/A02 user-approved Build 1871; A03–A06 passed Build 1874; A07–A09 passed Build 1881; combined device QA deferred |
| M2 — Financial controls/report UI | B01–B11 checked/fixed; numeric totals identical to baseline; CI+combined device QA | IN PROGRESS — B01–B03 CI Build 1886; B04–B05 CI Build 1890; B06–B07 CI Build 1899; B08 CI Build 1903; B09 source/tests committed awaiting CI; B10–B11 not started |
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

**Financial Home-first B09 Customer Commission/Limit chrome — 2026-11-24 (UI/TEST COMMITTED; CI AND COMBINED DEVICE QA PENDING):**
- **Verified baseline:** Build **1903**, commit `73bf353bfa2bb1a6270edb83d43b2dfe41e61cda`, Actions `45335177641` SUCCESS; Release and Debug APK artifacts uploaded, not expired. Work performed on the latest `feature/design-consistency-from-build1864-20261123` only. `main` untouched.
- **Exact source scope:** `Screens.kt`, commit `7c1949eae72978eaf57dc6dde7ca407ba0845ec1`, **ONLY** the adjacent `CommissionScreen` and `LimitScreen` bodies changed. Bytewise comparison against the Build 1903 source verified all code **before CommissionScreen and after LimitScreen** unchanged; this protects Home/Weekly, forms with crop/drafts, Bet forms/history, other finance UI, BetterData/Research etc.
- **Customer Commission:** Existing customer query, `commissionRateBasisPoints / 100.0` display, digits/decimal 6-character input filtering, zero to 100 BigDecimal validation, `vm.updateCommission(id, value)` save and Return callback preserved. Cherry-blush background, keyboard-safe vertical scrolling, customer identity and percent-input glass group and a second glass explanation panel. Original shared `FormActions` still calls the exact save action and respects the same enabled predicate.
- **Customer Limit:** Existing date/session state and `vm.allLimit(id,date,session)` + `vm.specialLimits(id,date,session)` flows preserved; four low-cost glass outer panels: selected customer identity, date/session chips, all-digit limit, specific-digit special limit. Same `vm.updateAllLimit`, `vm.addSpecialLimit` (digit, amount, value clearing), `BetParser.validDigit` + positive amount guard, `vm.removeSpecialLimit` confirm dialog and per-special list output. The per-row status/background remains original semantic secondaryContainer; no new blur/per-digit card changes. Keyboard inset and horizontally scrollable session chips improve usability.
- **Regression tests:** `CustomerCommissionLimitHomeChromeContractTest.kt`, commit `fee273755d1168cbb4b9d7db00194a963f794c62`, four tests checking basis-point/display-validation/Save, customer/session-scoped limit sources, Delete/Confirm and guarded special-limit actions, and protected CustomerForm/Home/Bet interfaces. The tests do not assert real Android device visuals or numerical fixture equivalence.
- **Master Design constraint:** Home's premium Cherry glass is the benchmark. No change to shared `FormColumn`, `FormActions`, `CherryProfileFormUi`, crop support, global Theme, Domain, Room, Live/SET, Research, BetterData engine, report tables, exports or JPEG. Maintain default App font and small ruby shadow only; no heavy effect per row.
- **QA gate:** new CI running, outcome not yet verified. Even with a green APK, user specifically requested one final **combined phone review** across Management and Financial UI; this pass has **no individual phone approval**. On phone, verify Commission percent at 0, 15, 100 and malformed values, scrolling with keyboard; Limit clear/default state, both sessions, custom digit, invalid amount, saved/removed row and dialog; check font scale and narrow-width buttons.
- **Next:** after B09 green CI, proceed to **B10 Agent Closed/Limit** and **B11 Risk Analysis** as separate scoped changes, then perform comprehensive final phone QA. Do not refactor calculation or persistence logic as part of a visual pass.

**Financial Home-first B08 Commission Review chrome — 2026-11-24 (CI SUCCESS Build 1903; COMBINED DEVICE QA DEFERRED):**
- **Verified starting point:** Build **1899** (`2e56d0d04ce9651ab5e9a318609427767ba0c528`), Actions `45332560306` completed SUCCESS, APK artifacts debug/release. Continue only branch `feature/design-consistency-from-build1864-20261123`, NEVER `main`. The user explicitly said check and continue.
- **UI implementation:** `CommissionReviewScreen.kt`, commits `0ee2ae2844c569aeb8ba0c12b7621486cc1966e5` and subsequent transparent clickable agent header follow-up: decorate **only** report selector and results presentation. Adds blush to selector and results `LazyColumn`; 22dp, white-rimmed translucent `CherryFinancialPanel` groups period/date picker and Agent/AM-PM selection; glass result identity/reselect and commission total. Expandable **agent-group outer Surface** is translucent with 20dp radius, light shadow and transparent clickable interior (preserves full row touch). Day and individual customer data rows retain their original neutral amounts, order and expansion.
- **Math/data invariants:** unchanged `vm.commissionLedger(today)`, `StatementPeriods.range/move`, all-time range `StatementRange(min date..today)`, `CommissionLedgerCalculator.select(allRows, range, agentId, draw)`, `CommissionLedgerCalculator.total(rows)`, morning/evening totals, per-agent, daily and customer line totals. Unchanged currency formatter and right alignment, dropdown/profile avatar, validation guard, `resultPage` back handling, group/day open toggles.
- **Test:** `CommissionReviewHomeChromeContractTest.kt`, commit `8f4bb4a8637f28959291193c0656bcb3611ba294`, four source-contract tests on selection/state, commission summary equations, expanded agent card and unmodified day-money rows; these assertions do **not** replace APK/device verification.
- **Protected:** No changes to Home, Forms, accepted Dashboard, Quick Entry, prior Financial B01–B07 screens, 2D Research/`Historical2DTimelineScreen.kt`, BETTER DATA, Live SET, domain/Room and financial calculations, export/JPEG/voucher code, or shared Theme/navigation. Existing `CherryFinancialChrome.kt` reused without changes. Diff restricted to the commission screen, contract tests, this plan.
- **CI/QA gate UPDATE:** Build **1903**, commit `73bf353bfa2bb1a6270edb83d43b2dfe41e61cda`, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45335177641 completed **SUCCESS** with Unit Tests/Lint, Debug APK, Release Verification and both uploaded artifacts. Device acceptance remains deferred for one user-requested combined review: date picker, all-time, agent totals, expandable groups, long MMK amounts and scrolling.
- **Next:** after B08 green CI, narrow B09 Customer Commission/Limit, B10 Agent Limit and B11 Risk UI work separately; no calculation refactor or phone visual approval inference.

**Financial Home-first B06–B07 Ledger Report chrome — 2026-11-24 (CI SUCCESS Build 1899; COMBINED PHONE QA DEFERRED):**
- **Base / branch:** Continue from successful Build **1890**, commit `9f02c0a5fb6ff4da9c7345036d7107da00fd81c0`, on `feature/design-consistency-from-build1864-20261123`. Do **not** touch `main`. Home is the *primary* visual reference; Report money grids are not generic glass cards.
- **B06 Agent Report selection:** `AgentStatementScreens.kt`, commit `8f9fe89a34834746b37a1a443e904c2f5849941d`. Only `AgentStatementWorkspace` modified: add Home-blush to the selection LazyColumn; group title + actual Agent picker/portrait into one `CherryFinancialPanel`, and period chips/date controls/AM-PM selection into a second glass pane. **Keep** selected-agent fallback, `StatementPeriods.range/move`, valid guard, result-page Back and `AgentLedgerReportResults` route. Other functions in this file untouched.
- **B07 Customer Report selection and actual summary:** `CustomerStatementScreen.kt`, commits `b3eb1a49de34bff728179b4a2415ef6907dc27b7` and `5ef1086780b019f67bf6f07bc0e30ae799c3a2ff`. In `CustomerStatementScreen`: blush list backdrop, one glass pane for Agent/Customer selectors and portraits, another around Date/Period/Draw controls; existing `StatementDateField` and closed-days/linked-customer logic, query `vm.customerStatement` unchanged. The *actually-used* legacy `StatementTotalCard` got a Home glass outer wrapper with the same original 16dp effective inner padding, rows and money semantics unchanged. **Not** the export/JPEG screen or calculations.
- **B06 Agent Report results:** `AgentLedgerReportResults.kt`, commit `5a3f5791fae14c735004d5a719e11dd2f219b4c6`. Home-blush scroll atmosphere, 22dp translucent ruby-glass outer identity Header with 1.5dp white rim; `AgentReportSummary` outer shell is blush glass with **original metric card/settlement colored panels and original 11dp dense padding preserved**. No edits to `AgentMetricCard`, `AgentClearingCard`, `AgentReportTable`, detail sheet, money wrapping, grouping, or paginated totals.
- **B07 Customer Report results:** `CustomerLedgerReportResults.kt`, commit `efcf045b9ec882fc82b8dd45ac3903bedd3445f3`: mirror Agent's header and summary outer glass, blush background; original `StatementMetric`, `SettlementMetric`, numeric table/scroll, customer grouping, paginate/search, Win/Loss color treatment and data queries unchanged.
- **Tests:** `LedgerReportHomeChromeContractTest.kt`, commits `6ceb5381ff7801541459fcaa167309685f525a92` and `0d574d5d0ac3b189eaee9c8a602d1d669b5d9bd1`, five source contracts for both selectors/routes, data-flow/period semantics, both Results outer shells and the actual Customer `StatementTotalCard` wrapper, with settlement/metric presence and semantic red/green ink. These tests **do not certify** real-device visual layout or monetary data equality on fixtures.
- **Protected areas:** Home/Weekly, all A01–A09 management UI, 2D Research/`Historical2DTimelineScreen.kt`, Forms, BetterData/Live/SET, Room/domain/rounding/basis points, report tables and data aggregators, JPEG/voucher export and global Theme/Nav remain untouched. No heavy backdrop blur or animation added.
- **CI/device gate UPDATE:** Build **1899**, commit `2e56d0d04ce9651ab5e9a318609427767ba0c528`, GitHub Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45332560306 **SUCCESS** (unit tests/lint, debug and release verification, both artifacts present). **User device visual approval remains deferred** for combined review. Verify Agent/Customer selectors, English/Myanmar long names, 3-up monetary metrics without clipping, table horizontal scroll, period/date/draw toggles, actual invoice/settlement totals, page switches and report detail.
- **Next after verified green CI:** B08 Commission Review, then B09/B10 limits and B11 risk, each a separate targeted UI pass. Phone QA still a combined sign-off gate, not automatic after CI.

**Financial Home-first B04–B05 digit presentation pilot — 2026-11-24 (CI SUCCESS Build 1890; COMBINED PHONE QA DEFERRED):**
- **Working branch / tested baseline:** `feature/design-consistency-from-build1864-20261123`, based on successful **Build 1886** (SHA `228846643610cbe389b8a469d3769887e0081a1c`, GitHub Actions `45323773686`, both APK artifacts present). Do not change or merge `main`.
- **B04 Agent Digit Totals:** commit `172f365f9151b265da477957bae69cc8c3eb0aa9`, only `AgentDigitTotalsScreens.kt`. Adds Home-blush to outer LazyColumn, `CherryFinancialPanel` around Agent/Date/Session selector and around result identity, and replaces only the **summary card wrapper** of `DigitTotalsSummary` with the same glass panel. Preserves `allAgents` (-1) avatar rules, exact `ScopeDropdown` and Date/Session handlers, selected-result page/Back, lazy list state, `DigitTotalsAgentCard` expand/collapse, horizontal `DigitTotalsTable`, `AgentDigitTotalsAggregator.combine`, `Math.addExact` money and commission equations, all row text and number alignment.
- **B05 Customer 00–99 Digit Views:** commit `199d875b14357ba5dce8f97476f2091fa340d3eb`, only `CustomerDigitViewsScreen.kt`. Adds two **outer** Home glass panels: header/total/count summary (stake/non-stake/all) and view tab/search/sort controls. **No new background or shadow around each of the 100 digit cells**; existing 3-column STAKED / 5-column ALL, `DigitViewCard` function, digit-number/amount text, winner/closed/special-limit/stake semantic colors, status badges, sorting, filtering, effective-limit/percent math and detail dialog remain unchanged.
- **Test addition:** `DigitViewsHomeChromeContractTest.kt` commit `b4d3a8a1060b4e0d44ef3bf0e7f41aee8a53c729`: asserts unchanged data source, aggregate math, selected agent, 00–99 cell semantics, grid sizing and absence of glass inside table/cells. Source tests complement but cannot replace Android screenshot/performance acceptance.
- **Frozen files:** Home/Weekly card, A01–A09 (including accepted Dashboard, Add/Edit Form, Quick Entry, Settings, List/Info), 2D Research and its shared `Historical2DTimelineScreen.kt`, Better Data engine, Live/SET timing, all DB/domain calculations, JPEG renderers, and global Theme/navigation untouched. Shared `CherryFinancialChrome.kt` **reused unchanged**.
- **Risk/QA:** The 3-up count tiles and 3-up filter tabs are now inside padded panels; verify on narrow phones and Myanmar enlarged font. Financial cells and totals must remain high-contrast and unclipped, 5-column grid must still fill width, scroll smoothly, support status color meanings, and values must exactly match Build 1886. If visual density suffers, narrow the *outer panel* padding only; do not shrink or truncate currency/digit text.
- **CI state UPDATE:** Build **1890**, commit `9f02c0a5fb6ff4da9c7345036d7107da00fd81c0`, GitHub Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45329162902 completed **SUCCESS**, with unit tests, lint, debug APK, release verification and both APK artifacts. **Phone QA deferred** per user's explicit combined-review decision; do not mark the digit visuals approved on a device. B06/B07 Agent/Customer ledger report chrome is the next separate scoped change.

**Financial Home-first B01–B03 pilot — 2026-11-24 (CI SUCCESS Build 1886; DEVICE QA DEFERRED):**
- **Accepted CI baseline:** Build **1881**, commit `447af3d16be8f41717644f70d104edd985754903`; GitHub Actions `45316563623` completed **SUCCESS**, debug and release APK uploaded. Build 1881 adds only an updated plan over working Build 1880. The user requested continuing work and reviewing all changed screens together later. **A03–A09 have no user phone visual acceptance yet.**
- **New isolated chrome:** `CherryFinancialChrome.kt`, commit `267aa2a79f7883b4a07ce604e123586e3817442f`: a Home-blush background and 22dp glass panel with translucent white surface, white rim and static low-cost ruby shadow; apply only to outer selectors / **agent group** cards, never every transaction, digit or amount.
- **B03 Today Ledger:** `OperationalScreens.kt`, commit `3cc8775b3cb3ab5cd636a86b92829cf9cde8fb54`: glass Date/Session filter pane; glass per-agent outer group; customer rows remain transparent within the same group, no new person portraits in individual transaction lines. Agent name can wrap alongside amount. **No change** to `vm.agentTotals`, `vm.customerEntries`, `flatMap{it.lines}.sumOf{it.amount}`, session filters, totals or bottom navigation.
- **B01 Betting Add/Edit:** targeted **only** `BettingScreen` function in `Screens.kt`, commit `a1dd29c84f7523d22ebd6cc1b79a6787260ff6b6`. Add Home-blush background and keyboard inset to existing screen container, glass Agent/Customer identity strip and combined Date/Session panel, subtle cherry-styled pinned confirm bar. **Preserve** selection IDs, exact parse/preview, backdated checks, edit/confirm calls, validation, input formatting, all result/error UI.
- **B02 Bet History:** targeted **only** `BetHistoryScreen` function in `Screens.kt` (same commit): Home-blush background and glass Customer identity + Date/Session panels. **Neutral per-transaction ElevatedCard remains unchanged**, as do submission ordering, edit/delete, voucher selection, totals, printable preview logic, and history detail dialog.
- **Regression guard:** `FinancialChromeContractTest.kt`, commit `eeea93c9c43750bef2f1e5a61a95bccbee83ee08`, source-contract tests for preserved routes/calculations and **no per-row blur**. Source checks do not prove real-device visual quality.
- **Out of scope / frozen:** Home and all approved A01–A09 except the explicitly located Betting/History function segments in shared `Screens.kt`; forms including `CherryProfileFormUi.kt`; Research including `Historical2DTimelineScreen.kt`; all Better Data/SET/Live; Room/domain/formulas; exports and JPEG renderers; global `Theme.kt`/navigation. Verify function-range source identity and git changed-path list before claiming the scope is clean.
- **Quality gate UPDATE:** Build **1886**, commit `228846643610cbe389b8a469d3769887e0081a1c`, GitHub Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45323773686 **SUCCESS** (unit tests, lint, debug APK + release verification, both artifacts). User phone comparison is **DEFERRED**, not silently approved. Keep money amount text, 00–99 grid and transaction rows untouched. Combined phone QA will include keyboard-visible Confirm, long names/amounts, both Myanmar/English, period/session chip selection, stale Agent/Customer checks, scrolling, empty ledgers and voucher routes.
- **Next:** verify B01–B03 CI before proceeding to **B04 Agent Digit Totals / B05 Customer 00–99**. Later financial report and commission UI remains a separate narrow pass, no calculation refactor.

**Combined-acceptance decision and A07–A09 selection pilot — 2026-11-24:**
- **User decision:** after the successful **Build 1874** A03–A06 List/Information pass, the user explicitly said **continue and review all screens together later** ("ဆက်လုပ်မယ်ကွာ နောက်မှ ပေါင်းပြီးစစ်မယ်"). This changes **only the phone visual sign-off cadence**, NOT the requirement to run CI/tests and preserve working Build baselines after each code pass. **Do not mark A03–A09 visually accepted until that combined review.**
- **Latest verified baseline before new code:** Build **1874**, commit `da985f45e9ed259aa779eb6aab47f9705de8d860`, GitHub Actions `45192745336` SUCCESS (unit tests, lint, debug and release APK artifacts). A01/A02 Dashboard remain accepted from Build 1871.
- **A07 Quick Entry implementation:** `Screens.kt` **ONLY `QuickEntryScreen` function**: same remembered agent/customer defaults and validation, same scoped customers, same `SelectionField`/ScopeDropdown and both profile photos, unchanged `onBet(agentId, customerId)` route and disabled guard. New single glass selection pane, blush background, selected-person summary, cherry primary 56dp action pinned outside an independently scrollable selection column, and keyboard insets. Home's actual Quick Entry orb and hero code frozen.
- **A08 Settings implementation:** `Screens.kt` **ONLY `SettingsScreen` function**: Home-blush backdrop, glass grouped Default Agent/Customer selectors, History update feedback group, and localized Language radio group. All `language.setDefaultAgent/Customer`, `vm::updateHistory`, Working/Success/Error status, `language.set("my"/"en")` calls unchanged. `CherryBackupSettings()` is **used as-is**; the encrypted backup/restore implementation and dialogs are untouched. Keep 96dp FAB clearance.
- **A09 Add Customer picker implementation:** `DashboardScreens.kt` **ONLY `AddCustomerFromHomeScreen` function**: one Home-derived glass agent selector pane, cherry 54dp continue action; preserve default-agent restoration, real person photo, `onCreate(selected)` and `selected > 0L` guard. Approved customer form itself not changed.
- **New isolated styling:** `CherryManagementSelectionUi.kt` contains low-cost `cherryManagementScreenBackground`, `CherryManagementSelectionPane`, `CherryManagementSelectionSummary`, `CherryManagementPrimaryButton` **only for Management screens**; no global Material Theme, Home shadow helper, heavy blur or continuous motion changes.
- **Tests:** `ManagementSelectionUiContractTest.kt` guards A07–A09 action/state contracts, glass treatment and lightweight rendering. `CherryProfileCoverageTest.kt` source-match checks made whitespace insensitive after Kotlin formatting; **the two avatars per selector and selected Customer summary requirement remain enforced**.
- **Safety review scope:** changes restricted to these exact operational functions (not Home/Form sections in `Screens.kt`), new isolated helper, two test files and this plan. `Research` / `Historical2DTimelineScreen.kt`, Home, Forms, approved Dashboard landing cards, A03–A06 List/Info, Live, Better Data, domain, database, exporter, JPEG renderers, and navigation remain unchanged.
- **CI + phone gate:** GitHub Actions for A07–A09 is pending at this checkpoint. Even with CI success, phone verification will be **combined later**, per user: Home → Agent/Customer Dashboard → List → Information → Quick Entry → Settings → Add Customer; compare MM/EN, long names, 68dp selectors, scrolling, opened keyboard, touch buttons, photo selection, state/route, history update, backup actions, Form handoff and no Research changes.
- **Next work:** inspect and fix only CI-reported compile/test issues; once CI success, record exact new build/artifacts, then proceed to next Home-first workstream without claiming phone acceptance, preserving Build 1874 as a fallback. Final combined review remains an explicit user acceptance gate.

**Management Home-first List/Information implementation checkpoint — 2026-11-23 (A03–A06, CI SUCCESS Build 1874; DEVICE QA DEFERRED for combined review):**
- **Starting accepted build:** Build **1871** `52b8e287c1adda4784d7267f9165254d02b91ea3`; GitHub Actions `45187181133` SUCCESS (unit tests, lint, debug and release APK artifacts), and **user explicitly accepted the A01/A02 Dashboard design** in chat ("အဆင်ပြေတယ် ဆက်လုပ်မယ်"). Dashboard UI is now protected from incidental changes.
- **A03/A04 code commit:** `f622c8798dc7726ba3e3ce06e97b470bac4de38b` updates ONLY `ListWorkspaceScreens.kt` for Home-blush backgrounds on Agent and Customer lists, a lightweight glass list pane (20dp rounded, #EBFFFFFF translucent, white 1.5dp rim, 3dp ruby shadow), transparent underlying `ListItem`, ruby chevron, 42dp existing avatar, soft white agent search field and cherry-outlined Add buttons. Preserve original Agent name/phone/address search, customer scope/selected-agent fallback, item order, callbacks and disabled/empty states.
- **A05/A06 in same code commit:** Both Agent and Customer Information still share `InformationCard`, now with a 24dp translucent profile pane, light 5dp ruby shadow, and Home-blush page background; 104dp avatar, title accent, scroll, load-state, 0.38/0.62 weighted label/value layout and Agent footer rate/date reporting preserved. Earlier Build 1865 fixes are deliberately retained.
- **Test commit:** `1fd09c7695ee8e605ecd955d7e64e2bc50680a1f` extends `ManagementUiConsistencyContractTest.kt`: maintain portrait/list callbacks, check Home-style backgrounds, glass card single-surface, no heavy Home blur, Agent/Customer selection, profile scroll/long value labels.
- **Scope restriction:** No changes in `DashboardScreens.kt`, `Screens.kt` (Home and Forms), `CherryProfileFormUi.kt`, `Historical2DTimelineScreen.kt`, any 2D Research code, Better Data, Live, domain/database, JPEG renderer, Theme, navigation or data. Do not reformat these in a styling pass.
- **QA gate:** Source changes and static contract tests committed; **the new A03–A06 UI has not passed CI or phone sign-off yet.** Test scroll of many agents/customers for smoothness; display long Myanmar names, different font scales and 0/invalid agent selection; inspect how transparent `ListItem` renders inside glass and verify touched entire row; verify Amount and rate values unchanged.
- **Next:** verify new GitHub Android CI run / APK artifacts, fix only verified compile or test issues if necessary. After green CI and user phone approval of A03–A06, proceed A07 Quick Entry, A08 Settings and A09 Add Customer selection screen.

**Home-first Dashboard implementation checkpoint — 2026-11-23 (A01/A02, CI SUCCESS / USER DEVICE APPROVED Build 1871):**
- **Branch:** `feature/design-consistency-from-build1864-20261123`, based on Build 1865 accepted-to-build source; do not merge to `main`.
- **A01/A02 UI commit:** `4c1fb549cbb0d150efbccab7d1b2a5444b31126e` modifies `DashboardScreens.kt` only, replacing the opaque low-elevation dashboard landing card with a shared Home-like `Box` glass pane: `#EBFFFFFF` translucent surface, 22dp radius, soft cherry/ruby-tinted 5dp native Compose shadow, 1.5dp white rim, gentle inner highlight, 42dp pink-gradient icon cell. Both landing lists get a Home-blush vertical gradient `#F6DCE5 → #F9E3EB → #FCEEF3`. Cards retain 78dp minimum, natural text-driven growth, original labels/icons/feature order and `onFeature(action.key)` routing.
- **Performance rationale:** `shadow` only, no expensive `BlurMaskFilter`/Home orb/silk/counter animation copied per card; `clickable` retains standard ripple/tap semantics.
- **Contract tests:** `ManagementUiConsistencyContractTest.kt` extended to assert both dashboards use exactly one shared component, preserved 78dp minimum, glass surface, border, shadow, route click, background gradient and role typography. Test/fix commits: `a177d6ba2108a1046f4a85229ac332e23c8d218c` and `59a36c46f002b23bae910dd6b8341cb32ecb39a4`.
- **Not modified:** Home `Screens.kt` / HomeGlassDepth / HomeCommissionCard and all Form / Research / Historical2DTimeline / Rules / BetterData / Live / database / domain / JPEG / LedgerNav / global theme files.
- **Status update:** After the original checkpoint, Build **1871** completed SUCCESS, with all Android test/lint/debug/release steps and both APK artifacts. The user subsequently confirmed **the Dashboard design is acceptable**. This is explicit user visual acceptance; individual font-scale/device corner cases are still worth ongoing QA. Preserve Dashboard code as an approved baseline.
- **Historical next action (COMPLETED):** Build 1871 verified and user accepted Dashboard. Next scoped stage is A03–A06 list/detail premium surfaces, while preserving Build 1865 loading/scrolling and Build 1871 Dashboard.

**Home-first design re-audit / corrected decision — 2026-11-23 (DOCUMENTATION UPDATE ONLY):**
- **Authoritative user direction:** The **CURRENT approved Home Design**, with translucent glass, soft ruby shadows, blush atmosphere, white highlight borders, premium rounded geometry and tasteful feedback, becomes the **PRIMARY reference for other eligible screens**. Cross-Agent/Customer similarity is not sufficient. This corrects the former overly narrow Dashboard pilot conclusion.
- **Code evidence revisited:** `Screens.kt` Home glass components / BottomBar / action cards; `HomeCommissionCard.kt`, `HomeGlassDepth.kt`, `HomeMicroAnimations.kt`, `CherryProfileFormUi.kt` (read-only), `DashboardScreens.kt`, `DashboardCardTypography.kt`, `ListWorkspaceScreens.kt`, Commission/Agent+Customer statement and JPEG-picker UI, `CherryBetterDataScreen.kt`, `LiveScreen.kt`. Source-based review only; **not a phone image/screenshot inspection**.
- **A01/A02 REOPENED:** Dashboard shared `DashboardLandingCard` still has white opaque 1dp-elevation Material Card, while Home has frosted glass with edge highlight and ruby depth. **Change decision from `NO CHANGE` to `PREMIUM REDESIGN REQUIRED`; no implementation made in this plan-only revision.**
- **A03/A04/A05/A06 NEED HOME VISUAL RE-AUDIT:** Earlier Build 1865 list/detail code fixes remain committed; their **functional/structure improvements are not rejected**, but their opaque card visual style is not yet Home-aligned. They must be revisited **after** the approved premium dashboard pass.
- **Build 1865 state:** GitHub Actions run `45174179816` **COMPLETED SUCCESS** on `1e7d4a375193cd4b5c10289f087cefa35ccde35e`; unit tests, lint, debug APK and release APK checks/uploads all succeeded. **No user device approval / screenshot sign-off recorded.** No new APK is required for this documentation-only update.
- **Protected scope:** Home itself and accepted Forms never change; `Historical2DTimelineScreen.kt` and all 2D Research UI/assets/logic are frozen. Also preserve Live timing, Better Data engine, domain finance, transaction rows and JPEG content.
- **Next actionable implementation:** A01/A02 premium dashboard cards/background in a narrow pass, with a real Home reference comparison. Afterwards inspect A03–A06 premium styling without losing Build 1865 loading/scrolling/label wrapping. Then A07–A09. Each pass needs tests, Android CI and user phone visual feedback.
- **Handoff precedence:** This 2026-11-23 Home-first correction supersedes earlier `NO CHANGE` text in historical pilot records. Do not implement further UI from an old plan interpretation.

**Historical management pilot checkpoint, 2026-11-23 (A01–A06, NOT YET DEVICE-ACCEPTED; A01/A02 `NO CHANGE` CONCLUSION SUPERSEDED by Home-first decision):**
- **A01/A02 Agent/Customer Dashboard:** reviewed `DashboardLandingCard` and `docs/ui/AgentCustomerDashboardCardsPilot.md`. Both use the same approved card component, padding, icon/chevron, 78dp minimum and opt-in bilingual typography. **NO CODE CHANGE**; preserve earlier user-approved appearance. Real-device bilingual screenshots still needed.
- **A03 Agent List:** already uses the shared `ElevatedCard + ListItem`, a 42dp avatar, a Chevron, Search and Add action. **NO CODE CHANGE** in this first pass.
- **A04 Customer List:** same avatar/card structure already present; updated the decorative trailing Chevron with a localized accessibility content description only; no filtering, click route or list data changes.
- **A05/A06 Agent/Customer Information:** both use the same `InformationCard`. Customer Information now has a stable shared `AppScaffold` + loading progress on null (like Agent Information), scrollable details (like Agent), and the shared detail rows use weighted label/value columns with 12dp separation and right-aligned values for long strings. Agent and Customer profile photo sizes remain 104dp. Text content, record lookup, edit/routing callbacks are unchanged.
- **Files changed:** `feature/main/ListWorkspaceScreens.kt`, plus `app/src/test/java/com/myanmar/ledger2d/feature/main/ManagementUiConsistencyContractTest.kt` source-contract checks for dashboards and list/detail invariants.
- **Explicitly NOT changed:** `DashboardScreens.kt`, `DashboardCardTypography.kt`, Home, any form/crop/photo persistence source, all Research/Analysis sources (including `Historical2DTimelineScreen.kt`), Live, Better Data, reports, database, domain, JPEG renderers/exporters, `LedgerNav.kt`, and global `Theme.kt`.
- **Test state:** Source diff reviewed, CI not yet completed at this checkpoint. Do NOT mark as accepted/finished from source-only review. Android build, Burmese/English screenshots, long strings, larger font scales, phone Back and scrolling remain to be verified.
- **Historical next step (NOW SUPERSEDED):** The original request was to run CI and sign off A01–A06. **Actual CI Build 1865 succeeded, but Home-first A01/A02 redesign is now required before any dashboard visual sign-off.**

**Initial checkpoint, 2026-11-23:**
- Base Build **1864** = SUCCESS, commit `f48c8ae8a45d584c8d416196a9cd4404916626ff`.
- Plan created before any styling changes, on `feature/design-consistency-from-build1864-20261123`.
- Plan-only commit `2cfb6cadfea66ce0ecd67e2cc3bdfbba0d94ac06` verified against Build 1864: **one added docs Markdown file; zero UI/code changes**.
- Last completed user-visible task = profile photo integration/coverage (Build 1864).
- No design consistency UI tasks have been implemented or accepted yet.
- **Next action:** verify this plan-only commit, then tackle **A01/A02 Agent/Customer Dashboard** first, preserving accepted shared landing-card design. Continue A03/A04 lists and A05/A06 information cards only after checks. Update this log after each accepted APK.

---

## 10. Existing references to read before touching UI

- `Screens.kt` Home components (`HomeScreen`, `HomeCountCard`, `HomeWeeklyResults`, `HomeBottomBar`, `HomeQuickAction`) — **read-only Home benchmark**
- `HomeCommissionCard.kt`, `HomeGlassDepth.kt`, `HomeTypography.kt`, `HomeMicroAnimations.kt` — **read-only Home benchmark**
- `CherryProfileFormUi.kt` — **read-only accepted premium form reference**
- `docs/ui/AgentCustomerDashboardCardsPilot.md` (historical same-family pilot; **DOES NOT OVERRIDE Home-first rule**)
- `docs/ui/ConsolidatedBilingualUiRollout.md`
- `docs/ui/TypographyStandardPilot_v1.md`
- `docs/home-premium-polish-from-build1828-20261122.md` (historical protected Home rationale)
- `docs/2D-RESEARCH-WORKSPACE-SPEC.md` (**read-only**; do not change Research)
- `docs/UX-IA-REDESIGN-PLAN.md` (historical proposal, **not authorization** to change routes)

**Latest user decision, 2026-11-23:** **Home is the single primary premium design reference** for every eligible remaining screen, not Agent/Customer parity alone. Re-audit and revise this master plan **BEFORE any new UI edit**. Preserve finished work, protect the special Research gift unconditionally, never edit `main`, keep Build 1865 as tested fallback and implement one isolated premium UI pass at a time. This supersedes older A01/A02 `NO CHANGE` design guidance.
