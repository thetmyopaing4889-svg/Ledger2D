# Cherry 2D — Home-First Design Consistency Master Plan (Build 1864 baseline / Build 1865 checkpoint)

**Created:** 2026-11-23  
**Status:** Build 1940 P1–P5 source/CI SUCCESS; P6 cumulative repository and source audit recorded. Combined real-device visual/functional QA is still pending; no final acceptance or main merge.  
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

## 2026-11-24 — Layout-preservation correction and regression audit (mandatory before P2)

**User direction:** Upgrade the existing UI, do not redesign its frame structure or relocate content/actions without consent. Premium means compact, decorative, purposeful and Home-quality, not more nested cards or larger empty space. Preserve the original first-view information hierarchy. The user specifically identified the Risk Analysis result layout regression and approved the hand-drawn two-portrait compact scope concept as a deliberate exception.

**References:** Original layout baseline Build 1864 (`f48c8ae8a45d584c8d416196a9cd4404916626ff`); latest verified implementation Build 1929 (`75f1baee85d28b998ca2737c5ab57ad84aa82c7c`), https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45376655161. All CI gates passed; debug artifact 14913045436 and release artifact 14911932245 match head and are not expired at audit. P1 Back fix still needs real-device confirmation. The two new device screenshots illustrate existing risk/history layout problems; their exact installed build is not independently established.

### Layout regression inventory

Compared cumulative Git diff from Build 1864 to P1 Build 1929 (70 commits, 41 changed paths, including 23 production Kotlin paths: 20 modified, 3 new). Reviewed changed production UI hunks and active route context from previous 47-route audit. This is source evidence, not a claim of all device states tested. The original layout is reference only: do not globally revert the branch or undo later valid safety fixes.

| Source / screen family | Observed structural delta or gap | Disposition |
|---|---|---|
| CustomerRiskReviewResults | B11 commit b544e0018a3f3609673b9cd6002b7820c8255523, Build 1915: compact horizontal scope changed to vertical panel with reselect at bottom; best/loss pair stacks below 380dp or high font scale/long amount; three equal-weight tabs replaced by FlowRow inside new panel; summary padding increased | **Confirmed visual regression.** Restore normal-width side-by-side best/loss and original three-tab hierarchy, no outer tab card; use approved compact scope; preserve all risk values, tab actions, drilldowns and semantic colors. Only genuinely necessary accessibility fallback, not blanket 380dp threshold. |
| CustomerReviewScreens | Risk-only five outer selection panels introduced; report selection deliberately left flat; original controls/order retained | Remove redundant per-field frames; decorate original control groups and maintain report/analysis scope/date/period/session behavior. |
| DashboardScreens | Landing card shell and icon decoration changed, original row/order retained; Add Customer heading and agent selector grouped; original agent/customer scope and active workspace selectors still use old flat layout / champagne SelectionCard | Retain useful card decoration without rearrangement; review all active and alternate selectors, including history/result. No new grouping that increases first-view height. |
| Screens: QuickEntryScreen | Continue action moved from content flow to bottom-pinned area; selection pane + selected-person summary added; scroll/IME handling added | Restore baseline placement and concise selection hierarchy unless user separately approves pinned action. Preserve valid keyboard handling and selection callbacks. |
| Screens: BettingScreen | Original identity row and date/session controls wrapped in two new padded panels; IME handling added | Keep original form arrangement and input space, apply decoration without adding unnecessary header height; preserve parser, amounts and keyboard/save behavior. |
| Screens: CommissionScreen / LimitScreen | Commission sections replaced by glass panels; limit has four extra groups and scrolling controls | Compare original control order, grouping, action placement; restore compactness; retain functional validation and scroll/accessibility. |
| Screens: BetHistoryScreen | Standalone date/session and identity wrapped in two new panels; active CustomerBetEntriesWorkspace path differs and remains old | Apply approved shared scope to eligible result pages, keep transaction order and actions. Do not confuse standalone history with active dashboard route. |
| Screens: SettingsScreen | Existing sections restyled, extra section padding and changed action appearance | Preserve original settings order/control placement; assess first-view spacing and valid accessibility. |
| ListWorkspaceScreens | Cards/avatars/add button decoration; InformationCard changed to weighted fields, loading/scroll support, more padding; original photo centered/name left remains visually uneven | Preserve stable identity, loading/scroll and field widths; compact profile hierarchy; no speculative relocation beyond agreed scope. |
| OperationalScreens / TodayLedger | Selection grouped; agent summary rows given weights and new glass group | Preserve order, amount alignment and compactness; do not change totals. |
| AgentDigitTotalsScreens | Selection fields grouped; selected scope wrapped; agent summary replaced with glass panel | Preserve selector and original digit-grid/filter layout; compact scope for result where applicable. |
| AgentStatementScreens | Heading and selector group wrappers; previously separate period/date/session items consolidated | Compare baseline spacing and exact order, eliminate excessive wrapping; retain date and report behavior. |
| AgentPatternRulesScreens | Multiple new panels across closed/limit result controls and standalone selectors | Restore original hierarchy and avoid per-control oversized panels. Maintain status/error colors and rules. |
| CommissionReviewScreen | Existing period/date and agent/session groups consolidated; result scope and summary wrapped | Keep original order, compact scope where applicable, preserve existing agent groups and amount data. |
| CustomerDigitViewsScreen | Two extra outer panels | Preserve original digit grid/sort/filter arrangement, minimize extra space. |
| CustomerStatementScreen | Selector/summary groups replaced; outer glass wrappers | Review actual reachability and original grouping; no redundant nesting. |
| AgentLedgerReportResults / CustomerLedgerReportResults | Only summary/scope surface colors/radius/rim/shadow and background changed; same row/padding/ledger order | Decoration-only candidate to retain; don't redesign existing 3+1 summary or ledger table. Integrate approved scope only where appropriate after checking range/aggregate. |
| AgentSettlementJpegScreen / CustomerSettlementJpegScreen | Selector groups added; preview previous/counter/next original single row replaced by separate counter + equal outlined-button row; rebate controls wrapped; extra toolbar height | Restore baseline single-row paging layout and compact selection, retain valid permissionPending/export guards, localization, renderer freshness checks and busy disables. User did not ask to relocate paging controls. |
| VoucherPreviewPlaceholderScreen | Same paging row -> counter plus button row; new glass toolbar | Restore original compact paging placement while retaining safe export/permission handling. |
| LiveScreen / CherryBetterDataScreen | Background, navigation colors/wrapping and back accent changes; no content order changes; pre-existing calendar title defect | Retain appropriate decorative improvements; fix Calendar title only; keep existing tabs and engines. |
| LedgerNav / shared scaffold / QuickEntryBubble | User-approved cherry art and upper-right default, drag persistence; P1 stable provider/root and shared header/canvas | Retain explicit Bubble exception and P1 safety fix; no other navigation redesign. |
| CherryFinancialChrome / CherryManagementSelectionUi | New wrappers with default 16dp padding and 10–12dp spacing create density risk when nested | Separate decorative surface from layout geometry; expose compact parameters or preserve original geometry. Never blanket-wrap each outlined input or row. |

Existing unchanged active paths (including CustomerBetEntriesWorkspace, CustomerBulkCommissionWorkspace, winning/closed-day/format bodies) still require visual upgrade review, not rollback. Home/Welcome/Form source and Research/timeline/Rules protected; no permission to alter protected content or shared timeline. Keep known calculation/DB/export-rendering/Live/Better Data behavior unchanged.

### Mandatory structure-preserving design contract

- Preserve baseline row/column arrangement, control order, relative position, selection/result separation, action location, visible information and first-view intent. Decoration must follow the frame; do not add size/padding or regroup just to introduce glass.
- Premium style via single translucent surface, blush highlight, restrained ruby depth, light rim, cherry icon decoration, typography and precise alignment. No nested per-field card wrappers, expensive scrolling blur, unnecessary labels or blank space.
- Normal phone width must retain original compact arrangement. Preserve readable long names/amounts, MM/EN, touch targets and large font; only documented necessary adaptive fallback. No clipping, numeric ellipsis or reduced accessibility.
- Changes to layout/position require explicit user approval. Authorized exceptions: Floating Cherry Bubble position/art/drag safety, P1 shared shell/lifecycle, and Premium Compact Scope specified below. Preserve legitimate safety, data correctness, loading and permission improvements; do not mechanically revert old source.

### Approved Premium Compact Scope component (user sketch)

Single low-height Cherry glass capsule, target about 80–90dp at normal device/font scale (not fixed height). Left-to-right: **Agent photo circle with name underneath → decorative graduated cherry chevrons → Customer photo circle with name underneath → right-hand date + AM/PM pill above compact decorated reselect action**. Distinct real portraits loaded with the correct profile kind/ID, white rim, soft ruby depth; no generic text-only joined subtitle. One surface with subtle rim/highlight, no nested large cards. Keep main results immediately below. Avoid shrinking names/amounts to force fit; adaptive wrapping for actual large font/narrow width, preserve accessibility and full date/range meaning. Agent-only, all-customer/all-agent, multi-day/range and both-draw cases must accurately show scope without invented individual photo or session; design these variants consistently and review before claiming complete. Reselect uses existing callback. Do not use this component on unrelated screens without a selection scope. User sketch was reviewed in chat; no image asset is needed in app.

### Compact scope / Risk result source checkpoint — 2026-11-24

- Reusable PremiumCompactScope: separate agent/customer portraits with names underneath, decorative chevrons, date/session pill and compact gradient reselect button in one cherry glass capsule. Real profile IDs only; aggregate uses group glyph; nullable identities and date/session text support future variants. Adaptive layout for narrow/large-font displays, no fixed-height clipping. Only active Risk result wired in this batch; future consumers still pending.
- Original risk hierarchy restored: 12dp summary padding, three equal-weight tabs in a single Row without an added panel, best/loss cards side by side whenever actual amount text fits at current font scale. Removed blanket 380dp threshold; measured-text fallback preserves full amount. Calculation/state block, RiskDigitTable/RiskPlanCard and entire tab content from when(tab) onward compared byte-identical.
- Caller passes separate selected agent/customer names from existing selection state, no new data queries or route changes. Selection page itself remains untouched for P2, except passing names to result.
- Updated B11 and photo coverage source contracts to assert actual scope IDs and original row arrangement rather than obsolete decoration. Two new compact-scope contracts. No Home/Form/Research, Bubble, report ledger, export or engine change.
- CI and actual phone screenshots still required; verify normal-width side-by-side cards, three tabs, name wrapping/aggregate, date/session and reselect, font scale, first-view density. No visual acceptance inferred from source tests.

### Risk controls refinement — 2026-11-24

Build 1931 (2af35cfcebe40ad15d2d02575ae563c610a570b4) CI succeeded, run https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45380705841. Phone screenshot shows separate portraits, compact header and side-by-side money boxes. User explicitly approved the new design mockup for three equal glass segmented tabs and four icon-over-label option cards in a single row (low loss, balance, keep more, custom). Selected ruby gradient, unselected translucent glass, consistent typography and 48dp minimum touch target. Custom retains the full accessible description and existing budget fields below only when selected. Existing planner can return additional middle plan or deduplicate plans, so render every actual plan in original order plus budget; a fifth control or accessibility width/font scale may reflow. No planner changes, no fabricated/hidden options. Only two UI blocks replaced; other result layout and state byte-identical. Two source contracts added and existing layout assertions updated. Scope and other screens unchanged. CI/phone confirmation of new controls pending.

### P2a — primary selection flows (2026-11-24)

- Verified Build 1932 (b739f448351f82b3b1d0ea47ac79759a277498e7), CI SUCCESS run https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45395498901; matching debug 14920069302/release 14920148919 unexpired. User requested combined phone QA later.
- Added opt-in PremiumSelectionControls: field-level translucent white/cherry border/label treatment, selection chip color/shape and cherry primary action. Existing ScopeDropdown and DateInput default styling/logic retained outside opt-in; no global theme or protected Home/Form/Research edits.
- CustomerReview report/analysis, AgentStatement, AgentDigitTotals and CommissionReview selection blocks restored to Build 1864 ordering/grouping (original callbacks/conditions), applying field/control decoration instead of extra enclosing panels. CustomerReview both modes now share same background. Results untouched, including risk tabs/compact scope.
- Agent/Customer scope and active feature-workspace selection blocks opt in; old Champagne wrapper replaced with a compact non-card column. Existing selections, dates, session and actions retained. Result scope and ledger bodies remain for P4. No extra nested wrappers per field.
- Added 2 source contracts and revised obsolete wrapper assertions. Device confirmation pending, including long names, MM/EN, disabled, narrow screens and first-view density. Horizontal scroll behavior on existing period rows retained rather than silently relocating options.
- P2a only: JPEG selectors, standalone/alternate selection and remaining entry/settings/limit/winning selection flows still require scoped P2b review; do not claim P2 completed.

### P2b1 — JPEG and alternate selectors (2026-11-24)

- Build 1933 P2a verified successful, run https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45400593902, debug 14923492854 and release 14924226669 match commit 937a3a79108fd1ad6f0b924d1fc147f9e321c80d. Four P2a selection blocks normalized to original control names match Build 1864 exactly; result tails unchanged.
- Agent/Customer JPEG selectors restore original item hierarchy without per-field outer panels. Shared opt-in fields/chips and primary action style. Preserve current busy guards, original preset/date/range/callbacks; preview source tails byte-identical. Existing scroll behavior retained.
- Standalone agent closed/limit date/session groups restore baseline hierarchy; legacy customer statement retains original selector grouping and period card geometry. Aggregate scope date/session controls opt in without moving actions.
- Existing preview, renderer, financial calculations, Home, Profile Form, Research and navigation unchanged. Updated source contracts preserve business guards. CI and combined phone QA pending.
- P2b2 direct date/session selectors (history, Today Ledger, limit, winning, report, closed days, digit list) still pending. Quick Entry/Add Customer/List/Profile/Settings to P3; entry/result body to P4; preview toolbar and specialized Live/Better Data to P5.

### P2b2 — direct date/session controls (2026-11-24)

- Build 1934 P2b1 run https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45404311088: unit tests, lint and debug APK passed at checkpoint; release step still running. User explicitly requested continuation, so this cumulative batch uses its latest source commit 813e2a3cc230a8e661e75cde144bc486cad5b6be rather than claiming release verification.
- Opt-in premium date/session controls for standalone digit list, customer limit, winning input/view, scoped winning, legacy report, closed day, bet history and Today Ledger. Existing winner segmented row kept; colors aligned to cherry. Removed added outer date/session wrappers in customer limit, standalone history and Today Ledger only, retaining identity and other result panels for P4.
- Preserved date/session callbacks, weekend handling, winner add/edit/delete, limit validation/save, voucher/edit/delete, totals and result content. Today Ledger body after selector byte-identical. Home, profile forms, Settings, Quick Entry and Research untouched. Existing source contract counts updated for removed wrappers; two additional tests for selector wiring/busy guards. P2 source coverage complete for planned selection families; management selectors assigned to P3 and entry/result panels to P4, preview toolbar and specialized screens P5.
- Combined device QA remains pending at user request, including period horizontal scrolling and field readability, large-font behavior, MM/EN and actual first-view layout. Do not claim visual approval.

### P3 — management design refinement (2026-11-24)

- Verified Build 1935 (f180d30a4142e9336b9d2c6565dc5fc51263a79f), run https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45405927146, all CI steps passed, debug 14926017149/release 14925997151 unexpired and matching head. Build 1934 also passed. P2 source/CI complete; combined device QA pending.
- Correct earlier audit wording: Build 1864 Quick Entry already used weighted Spacer to keep Continue at the bottom. Keep bottom placement with keyboard-safe scroll and restore original 16dp content spacing. Remove newly added outer selection pane, keep original selected-person summary/real photos and route, allow full name wrapping, align controls with P2.
- Add Customer picker restores original heading/selector/action hierarchy without wrapper. Settings retains its three original groups, baseline 14dp/10dp padding/spacing and zero extra padding on language list; unchanged default selection, history update, encrypted backup and language actions.
- Agent/Customer dashboard original card geometry, typography, feature order and callbacks retained; strengthen static ruby highlight/icon depth. Agent/Customer list and profile glass gains subtle highlight, shared selector/search and action colors. Translate mixed Agent/Customer labels in customer list consistently; hide blank phone line. Profile name centered beneath already-centered photo (alignment refinement only), retain all nonblank field values, weighted wrapping, loading/scroll and original footer. No invented fields.
- No Home, protected Profile Form, Research, navigation, Bubble, calculation, storage or export changes. P3 CI/device review pending; not a claim of phone visual acceptance. P4 will integrate approved compact scope on remaining result screens and restore remaining entry/financial result frame geometry.

### P4a — approved compact result scopes and active history (2026-11-24)

- Build 1936 verified CI SUCCESS, commit 0a08cdab9c3ff78688f7dd260eb4cc272581f88c, run https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45411142685, matching debug 14930615335/release 14929876262. P3 source/CI complete, combined phone QA still pending.
- Reuse the approved PremiumCompactScope on agent/customer ledger reports, Agent Digit Totals, Commission Review and active Agent/Customer feature result routes (including history, digits, bulk commission, closed/limit). One actual agent or customer identity per slot, all-scope group glyph for aggregate, exact selected date/period and AM/PM/both draw label. Customer report passes selected agent name from existing state; no new query. Agent totals replaces standalone reselect and duplicate identity/date block with one scope. Preserve existing reselect callbacks and invalid/loading branches.
- Agent/customer report body, numeric tables, pagination, drilldowns, commission filtered totals, selection state and workspace dispatch remain unchanged. Active BetEntriesWorkspace original row geometry, raw source text, selection, edit/delete, voucher and totals preserved; decorate existing surfaces with white rim/cherry totals and localize mixed entry counts. No extra per-row layout wrapper.
- Risk result already approved component, unchanged. No Home, protected forms, Research, engine, DB, navigation or renderer changes. Updated source contracts; two new scope/history guards. CI and combined real-phone QA pending, notably long date ranges/labels and aggregate scope.
- P4b still required: original compact layout for betting/standalone history, commission/limit, closed/limit workspaces, digit result groups, Today Ledger and winning/closed day result decoration. Do not claim entire P4 complete.

### P4b1 — compile repair and compact entry/digit layout (2026-11-24)

- Build 1937 (8ba5e49f3fa988ebeaa327d89c2d7a30448eb889) failed compileDebugKotlin at CustomerStatementResults.kt:26:80, missing agentName parameter. No APK produced. Fix the existing compatibility caller with the actual selected agent's observed name (localized fallback during loading), no change to report calculations or route. Latest fully verified APK remains Build 1936 pending cumulative CI.
- Betting: restore Build 1864 original independent identity row, date item and draw row, without the two extra 16dp panels. Keep IME padding, original input size, backdated switch, preview, parser, quick-format order, footer and submit/edit/late guards. Apply premium field/chip/segment/action decoration only.
- Customer digits: restore baseline summary/tab/search/sort hierarchy without added panels; use premium field and sort-chip decoration. Grid and detail body byte-identical, including winner/closed/special-limit semantic colors.
- Standalone history: remove remaining extra identity wrapper; retain existing photo, date/session and all edit/delete/voucher/total functionality.
- Existing layout source contracts updated and compatibility wiring guarded. No Home/Profile Form/Research/engine/storage/navigation changes. Remaining P4b2 commission/limit, agent pattern workspaces, Today Ledger and winning/closed-day result decoration still pending. CI and combined phone QA pending.

### P4b2 — remaining results (2026-11-24)

- Build 1938 verified CI SUCCESS: https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45422433155; debug 14936267357/release 14936022387 match b4594ca4733628337db9996d4b17867268628dab.
- Standalone commission/limit remove excess enclosing panels and restore original limit spacing/divider, retain IME and original shared Input so protected profile forms remain identical. Agent closed/limit restore baseline layout and original confirmation/state prefix, with premium primary actions and limit fields. Bulk commission retains both modes and validation, using premium existing controls. Today Ledger original single group retained; heading accent only. Winner/closed-day existing cards/actions decorated, original grid and semantic data preserved.
- P4 planned source pass complete, CI and device visual QA pending. No Home, protected profile forms, Research, engine, database, navigation or export changes. P5 next after CI.

### P5 — preview and specialized chrome (2026-11-24)

- Build 1939 verified CI SUCCESS, commit 12e319cf1a86eb68115e6673d86730b68080d044, run https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45427187217, debug 14937262183/release 14937497115 unexpired and matching head.
- Voucher, Agent and Customer JPEG previews restore the Build 1864 one-row Previous / page count / Next arrangement, separate existing Gallery button below; remove extra toolbar padding and rebate wrapper. Page counter centered with localized text, cherry action styling, original flexible export button height and disabled/loading/permission states retained. Customer rebate field keeps validation and disabled guard. No renderer, document, page-count or export callback changes.
- Live Calendar title now correctly identifies the calendar overview as well as detail; existing Live/Calendar tabs center their labels. Lifecycle, SET/Luke/Room data flow and content unchanged. Better Data header, four tabs, background and engine wiring reviewed; already consistent with the prior specialized design, no further source changes. Research/Rules/shared Timeline frozen.
- P5 source scope complete; CI and cumulative P6 audit plus combined device QA pending (especially narrow-screen page controls and actual Gallery output). No merge to main.

### P6 — cumulative verification and device acceptance gate (2026-11-24)

**Verified implementation:** Build 1940, commit `9f965004962dd7cf4b1b513bb66594cc56357971`, run https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45432610558, job 138581730788. Completed SUCCESS: unit tests, lint, debug APK, formula-discovery historical audit step, release verification and both artifact uploads. Debug artifact 14941435064 and release artifact 14938439968 are unexpired and match this exact implementation commit. The P6 checkpoint is documentation-only and does not modify application code.

**Full-tree baseline comparison** (Build 1864 vs Build 1940; both recursive Git trees complete, not truncated): 409 baseline files, 436 current files; 383 existing blobs identical, 26 modified, 27 added, zero deleted. 81 commits ahead of baseline. Of the 53 changed/added paths, 28 are production UI Kotlin (22 modified, six new), 23 are tests (two modified, 21 new), one is this plan and one is the CI branch trigger. No core, resource/asset, build configuration, renderer/exporter or Research path changed. Exact matched baseline groups: core 69, resources/assets 22, Research/rules/timeline/analysis-name group 16, renderer/exporter/pagination-name group nine, build configuration seven; these groups may overlap. Better Data/discovery name group 42 of 43 unchanged; only CherryBetterDataScreen chrome changed, pre-Scaffold state and content body identical. Live group 49 of 50 unchanged; only LiveScreen UI changed, pre-Scaffold lifecycle/state identical. No deletion, migration or main merge.

**Source checks:** HomeScreen, AgentFormScreen/CustomerFormScreen region, shared Input and betting PreviewCard match baseline exactly. All 47 navigation destinations retain original order; authorized Bubble provider and protected form opt-out are the only navigation wiring changes found. Risk body retains original calculation/selection anchors; the lower plan selector intentionally differs (all actual plans plus budget, existing selected ID and budget fields). Earlier P2–P5 exact scoped comparisons and CI checks remain in this log. P5 three preview pager/bitmap blocks and export/permission preparation blocks match P4; original page controls restored with localized counter. No evidence of new calculation or storage changes in inspected source. These are source/CI checks, not proof of pixel-identical exports, device layout, actual data or speed. Do not claim all runtime paths exercised.

**Combined phone QA — install verified Build 1940 after backing up existing data; use disposable test entries for writes. Compare the same dataset/date/session in both builds, not different live feeds.**

| QA area | Acceptance check | Status |
|---|---|---|
| Home, Welcome, Weekly, profile forms | Approved look unchanged; crop, add/edit, back, save, existing portraits | Pending device |
| Shared chrome/Bubble | Drag, persist across restart/navigation, safe keyboard/edges; return/back; hidden on protected routes | Pending device |
| Dashboard, lists, settings, Quick Entry | Original control order and first-view density; correct photos/names; MM/EN; long names; keyboard, backup and reselect | Pending device |
| Selectors and results | Agent/customer/all scopes, AM/PM/both draws, period/custom range; compact header, no duplicate frames, full amounts, no clipping | Pending device |
| Entry/history/digits | Same parser, validation, totals, entry edit/delete, voucher selection, digit sorting and winner/closed/limit colors | Pending device |
| Report/risk/commission/limits | Same fixture amounts/rates, risk tabs and every plan, custom budget, confirmation/save/remove | Pending device |
| JPEG previews | Previous/counter/next on one row when space allows; swipe, rebate validation, permission grant/deny, duplicate-tap guard, gallery output and compare same-fixture page content | Pending device |
| Live/Calendar | Correct titles on overview/detail, status, result handover and date history; existing lifecycle | Pending device |
| Better Data/Research/Rules | Four tabs, 9:30 inputs, search starts only on button, cancel/exit; startup/navigation performance; protected Research/Rules unchanged | Pending device |
| Accessibility | Narrow screen, long labels, large font, both languages; adaptive reflow and readable numbers | Pending device |

**Remaining risks to verify on phone:** compact header with long multi-day ranges and both-draw badge, five risk options reflow, narrow/large-font JPEG page controls, actual export file content and storage permission, Bubble return/drag behavior, navigation/startup performance. Do not change layout or protected modules without identifying a concrete issue. A source test or green CI cannot replace this device gate. Record screenshots, expected/actual values, build and screen before targeted corrections.

### Updated execution sequence

1. P1 source/CI verified (Build 1929); device Back verification remains open.
2. This source regression audit + document checkpoint, before implementation. No production code changes.
3. Implement compact scope component with explicit variants and baseline risk layout restoration as focused first batch; update old source tests that enforce unwanted wrappers/380dp fallback, preserving real behavior guards. Confirm source and CI, then device proof.
4. P2 selection flows: preserve original control placement/order; remove excess nesting and align decoration across actual active routes. P3 management then P4 results (including remaining scope consumers). P5 export/specialized with original page controls. P6 cumulative comparison and device QA. Continue in grouped scoped batches, never declare visual approval from tests.
5. For every implementation, record baseline vs new structural diff and intended visual change. Any unapproved structural difference is a regression gate. Actual screenshots at same scale (MM/EN and large font) validate first-view density. No main merge.

---

## 2026-11-24 — Revised device-QA plan (P1–P6, supersedes earlier visual completion claims)

**Baseline:** Build 1927, commit `12bc22bf01b28d76fc8f4267af76476d1f7c3637`, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45366314562 — verified CI SUCCESS, both APK artifacts (debug 14907347027, release 14907421794). User submitted ten actual device screenshots and reported premature floating bubble disappearance on Back. This is NOT visual approval. Continue the same isolated working branch; do not merge or edit main.

### Observed device failures and source audit

| Evidence | Source-level finding | Required action |
|---|---|---|
| Better Data and 2D Rules header/dock differ from operational pages | Better Data uses its own Scaffold/TopAppBar/NavigationBar; timeline owns separate chrome shared with protected Research | Align eligible chrome without changing tabs or engine. Freeze timeline/Research and approved Rules reader/content; document any necessary boundary approval separately. |
| Entered transactions selection/result retain old flat styling and champagne border | Active CustomerFeatureWorkspaceScreen -> SelectionCard -> CustomerBetEntriesWorkspace differs from standalone BetHistoryScreen already restyled | Audit active picker + result + edit + voucher entry as one flow, not just standalone screen. |
| Customer report selector flat; JPEG selector bulky | CustomerReviewWorkspace conditionally styles only analysis, export adds outer panel per outlined field | One cohesive grouped selector surface, consistent fields/filters and compact spacing. Do not change date/session/scope calculations. |
| Customer information avatar centered but name left, list selector/add affordance mismatched | InformationCard and CustomerListWorkspaceScreen use different hierarchy and stock controls | Align profile identity and list/action hierarchy, preserve photo/id/edit and optional fields. |
| Live/Calendar different header/title; calendar list says 2D LIVE | title only switches when calendar detail selected; bottom bar stock material | Fix title for entire calendar tab; preserve result statuses, dates, polling and handover. |
| Bubble visible on existing eligible pages, excluded on others; disappears before page exits | Global provider observes current route; outgoing shared scaffold may recompute eligibility and conditionally replace its entire root before navigation finishes | Route/destination-local eligibility, stable shared root and lifecycle. Keep existing exclusions, position persistence, drag bounds and artwork; no delay/animation workaround. Device reproduction required. |

### Source route coverage (47 declarations, not 47 unique screen states)

- Home/welcome (2): reference only; no edits.
- Live, Better Data, historical2d, historical2dLegacy (4): specialized UI; current Timeline and Research remain frozen, legacy only if reachable. Better Data shell only.
- CommissionReview, todayLedger, quickEntry, settings (4): shared selection/summary/status, keyboard and backup UI.
- agents, agentDashboard, agentWorkspace/{feature}, agentList, agentInfo/{id}, agentScope/{feature}, allAgentFeature/{feature} (7): dashboard and every active total/report/closed/limit variant; alias reachability recorded.
- customerDashboard, customerList, customerListForAgent/{agentId}, customerInfo/{id}, customerWorkspace/{feature}, customerScope/{feature}, allCustomerScope/{agentId}/{feature}, addCustomerHome (8): list/info/selection/history/report/analysis/digits/bulk commission plus alternate scopes.
- agentForm/{id}, customerForm/{agentId}/{id} (2): approved body/crop/save/header unchanged; shared bubble lifecycle only.
- agentSettlementJpeg, customerSettlementJpeg, voucherPreview/{customerId}/{date}/{session} (3): selectors, loading, paging, export and permission feedback; renderer/pixels remain protected.
- analysis/{id}, bet/{agentId}/{customerId}, betHistory/{customerId}, betEdit/{entryId}, digitList/{agentId}/{customerId}, total/{agentId}, closed/{agentId}, format, commission/{id}, limit/{id}, agentLimit/{id}, report/{scope}/{id} (12): check active and legacy direct destinations with actual call chains; avoid unnecessary edits to unreachable fallbacks.
- winning, winning/input, winning/view, closedDays, winning/{scope}/{id} (5): old controls/selection/result views and date/session flow.
- For each route record active/alias/legacy, screen/function, selection/result/empty/loading/error/edit states, shared components, changes or reason for NO CHANGE, CI and device evidence. **Never treat route enumeration as proof of visual coverage.**

### Canonical Home-first operational UI contract

1. One shared cherry-blush canvas (`#F6DCE5 → #F9E3EB → #FCEEF3`), consistent inner header (centered title, deep cherry accents, pale blush/white glass), consistent spacing and back affordance. Protected Home/Welcome/Profile Form/Research retain their accepted appearance; Better Data's functional subtitle and specialized reader/navigation may be documented exceptions. No global Theme rewrite.
2. Use a single glass section per meaningful selection group; no per-field redundant nested cards. Shared avatar picker/field/date/period/session chips, alignment, disabled and error states, min touch targets, natural Burmese wrapping; show all choices via wrapping or clearly scrollable controls. Keep date and session state untouched.
3. Shared card/action hierarchy: 22–24dp glass group, restrained ruby elevation, clear type and contrast, one primary action; secondary/destructive actions remain semantically distinct. No expensive blur on lists; do not apply the same decorative card to every row.
4. Results: scope header and summary group visually consistent with picker, compact high-contrast ledger rows and aligned amounts; keep sort/order, edit/delete, money and exported content unchanged.
5. Bubble: existing 48dp Home artwork, reserved header slot, safe drag/persistence; no new bubble on previously excluded screens. No premature disappearance on Back and no hidden header actions. No forced slow transitions, no new background work.
6. Protect original Home, Form body/crop/header, Research/timeline/rules content, financial/core/DB/export pixels and Better Data/Live engines. The user has authorized narrow shared chrome and Bubble Back fix: `LedgerNav.kt` may change only provider/lifecycle scope, NOT destinations, arguments, callbacks, back-stack operations or transition duration. Protected profile form chrome must have an explicit exemption if shared chrome changes.

### P1 source checkpoint — 2026-11-24 (CI/device QA pending)

- Plan-only commit `8aebdd61b30002a610165370222801eaac05dfb4` recorded first; Build 1927 remains verified recovery APK.
- Stable Quick Entry provider independent of current route; Home and Welcome explicitly override with null within their own destinations. This addresses the source-level race where an outgoing screen could lose the bubble early. Existing exclusions inside individual scaffolds retained; no destination, argument, callback, transition duration or back-stack operation changed.
- OperationalScaffold now has a single stable Box root, static Home-derived blush canvas and pale blush header with deep cherry title/Back; overlay conditional only, not the entire screen. Profile form destinations explicitly opt out of new shared chrome and retain previous white header/canvas and bubble eligibility. Specialized self-owned Scaffolds unaffected until P5. Local body backgrounds may still override shared canvas; P2–P5 must review actual content.
- Updated bubble source contract for stable navigation provider, preserved exclusions, original artwork/drag/prefs and reserved slot. Added test for shared shell and profile exemptions. No actual device transition test has run.
- Source comparison confirms Screens.kt from DarkStatusBarsEffect onward byte-identical. Next: verify CI/APK, then P2 selection flows. Confirm Back on actual device; source repair is not proof that the visual bug is gone.

### Implementation and acceptance gates

| Phase | Scope | Acceptance |
|---|---|---|
| P1 — Shared shell and bubble | Canonical operational header/canvas, stable scaffold structure, destination-local bubble eligibility; preserve protected forms | Source contracts for route/eligibility, stable host, header/standalone FAB, protected Home/Form/Research; actual Back from eligible page to Home, eligible-to-eligible and excluded page, keyboard/drag/save. |
| P2 — Selection flows | Agent/Customer workspace and scope selectors, report/analysis, digit totals/views, commission review, settlement and standalone selectors; consolidate nested surfaces and align inputs | Actual active routes plus selection/result pairs; MM/EN, narrow/large font, disabled/empty, avatar and date/session controls. |
| P3 — Management | Agent/Customer dashboards/list/info, Quick Entry/Add Customer, Settings | Profile photo/name/optional info alignment, correct routes, long names, coherent cards/actions, no changes to accepted form mechanics. |
| P4 — Results | BetEntriesWorkspace actual active history, financial results/totals, bulk commission, winning, closed days, closed/limit, betting/history | Preserve numeric and semantic states; fixture totals, correct ID/date/session, scroll and save/edit/delete behavior. |
| P5 — Specialized/export | Live/Calendar, Better Data, JPEG selection/preview polish, review Analysis/Rules boundary | Preserve engines and renderers; actual screenshots for tabs/headers, pages/export/permission. Research and shared Timeline not edited without separate permission. |
| P6 — Full regression | Re-audit all 47 route declarations and reachable internal states, protected hashes, CI, combined phone QA | User screenshot/device acceptance is mandatory; CI alone cannot close visual QA. |

Batch related changes per phase with one atomic commit and CI, source review before push. Record exact diff and build artifacts; no cosmetic edits to unreachable code merely to increase coverage. Phase cannot be marked visually done until phone proof. P1 source checkpoint recorded above; P2–P6 not started.

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
4. User-approved batching: group related low-risk UI changes into a single scoped commit and CI run after individual source review; preserve the same test gates. Android CI debug+lint+unit tests+release APK → combined phone screenshot comparison → acceptance or local revert. Keep business logic changes separate and do not batch across protected boundaries.
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
| M2 — Financial controls/report UI | B01–B11 checked/fixed; monetary source formulas unchanged; CI+combined device QA | SOURCE/CI DONE — B01–B08 CI success through Build 1903; B09 Build 1907; B10 Build 1910; B11 Build 1915 CI SUCCESS; combined phone QA pending |
| M3 — Export/preview controls | C01–C04 checked; JPEG pixel/page and money invariants pass; CI+device QA | SOURCE/CI DONE — C01 Build 1919, C02 Build 1922, C03+C04 Build 1923 CI SUCCESS; combined phone QA pending |
| M4 — Specialized UI review | Live/Better Data differences reviewed; protected Research/Library untouched | SOURCE/CI DONE — Build 1924 SUCCESS, both APK artifacts verified; combined phone QA pending |
| M5 — Final regression | Cross-screen MM/EN, small device, large font, photo, scroll, finance, export, no protected paths | STATIC AUDIT DONE — Build 1924 CI SUCCESS; real-device regression and user acceptance pending |

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

**Build 1926 CI repair — 2026-11-24 (rerun pending):**
- Build 1926, commit `af01b71f8ae7b73132caebb1aa7827a6d8bbb849`, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45364586599: Kotlin compilation succeeded; 1,018 tests ran, one failed at CherryProfileIntegrationContractTest.kt:33. All four new bubble contracts passed. Release/APK uploads skipped due to unit-test failure.
- Failure was the old source assertion `QuickEntryFab(globalQuickEntry)`, superseded by the shared overlay call. Update only the integration test to verify the eligibility gate and new overlay invocation, and explicitly retain bubble eligibility on both profile forms. Existing gallery/save/backup checks remain intact.
- No app source, resource, Home/Form/Research or navigation changes in this repair. Verify all tests/lint/debug/release APK before device QA.

**User-approved floating Quick Entry bubble follow-up — 2026-11-24 (CI/device QA pending):**
- Continue from verified Build 1924 plus documentation-only M5 checkpoint `c79b7daf62fb7651489bb1d6485a280a6b1570e6`. User explicitly authorized a scoped change to the existing shared OperationalScaffold Quick Entry host, not a redesign of protected Home/Form content.
- Reuse original `R.drawable.btn_cherry_orb` in a compact 48dp circle, frosted rim and localized soft halo blur/shadow (no whole-screen blur or repeated animation). Home QuickCenterFab and original artwork untouched.
- Overlay stays draggable throughout safe content bounds, starting in a reserved trailing header slot. Other header actions remain and screen-specific FABs remain in their original Scaffold slot. Existing eligibility gate, Quick Entry navigation and excluded screens unchanged. Header clearance prevents dragging over other header actions. Safe drawing/IME insets and normalized coordinates handle resize; only the bubble accepts pointer input.
- Versioned position keys intentionally ignore the previous bottom-right offsets on first upgrade. Save normalized position once per completed/cancelled drag; shared preference listener syncs retained screens. No ledger data changes.
- Four new unit/source tests: default/upgrade, clamping/header clearance, resize/keyboard, shared scaffold eligibility and original action/artwork. Existing protected screen source from DarkStatusBarsEffect onward verified byte-identical. Actual phone QA required for header alignment, long titles, small screens, keyboard, drag/tap, saved position, save/confirm and independent FABs, old Android blur fallback.
- Next: verify combined CI and both APKs, then combined device QA as requested. No main merge.

**M4 verified CI and M5 final static regression audit — 2026-11-24 (device QA pending):**
- Build **1924**, commit `36900b614bafe4dc8f5b82056b610ec19ae859b2`, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45358556073. Verified completed SUCCESS: unit tests/lint/debug APK, formula-discovery audit workflow step, release verification, debug/release uploads. Debug artifact `14904241963`, release artifact `14903513846`, both unexpired at check and match this commit. This is the tested APK candidate.
- Baseline Build 1864 `f48c8ae8a45d584c8d416196a9cd4404916626ff` -> Build 1924 comparison: 65 commits ahead, 0 behind; complete non-truncated Git tree manifests, 409 -> 425 blobs; 37 changed paths (16 additions, 21 modifications, 0 deletions). 21 production Kotlin paths, 14 test Kotlin paths (13 new contract files containing 50 @Test declarations, one existing profile coverage test updated), one workflow path and one plan path. Workflow change only appends the dedicated working branch to push triggers; no test/build commands changed.
- Protected blob audit: 214 baseline paths matched by core, main assets/res, Home/Welcome/Form, Research/Rules/Timeline, navigation, JPEG renderer/exporter/calculator, Live coordinator and Better Data engine/entrypoint protection criteria; all 214 identical Git blob hashes. No changes outside feature UI/test/docs and the branch trigger. No asset or resource changes, no deleted paths, no migration.
- Mixed file `Screens.kt`: exact top-level function-span comparison baseline -> head found changes only in QuickEntryScreen, BettingScreen, CommissionScreen, LimitScreen, BetHistoryScreen, SettingsScreen. HomeScreen and all Home helpers (Weekly, Live hero, footer, quick actions/dock), AgentFormScreen, CustomerFormScreen, FormColumn/FormActions, AppScaffold/OperationalScaffold identical. No new top-level functions in this file. Navigation routes unchanged.
- Reviewed cumulative patch hunks across all changed feature UI paths, including monetary callback/validation wiring, report calculations and scope identity, original export calls and freshness guard, Live lifecycle and SET projection, Better Data four tabs and state/engine/snapshot body. C04 permission/loading guards and C03 busy-state guards are intentional presentation safety changes. Existing profile test edits adapt to whitespace and renamed local customer variable, retain real identity assertions; no test deletion. No new confirmed blocking source regression in this static audit. Not proof of runtime correctness, identical exported JPEG bytes or visual acceptance.
- Batch workflow result: C03+C04 Build 1923 and M4 Build 1924 each used one commit/build. No app code changes are required solely to record M5. This documentation-only checkpoint must not be confused with a new APK validation.
- **Combined phone QA before acceptance:** Install Build 1924 on actual device (back up real data first); use safe test data for any financial writes. Check Home/Weekly and add/edit agent/customer crop/save/back against accepted reference; A01–A09 dashboard/list/info/quick entry/settings, MM and EN, long names, switch agent/customer (no stale photo/id), small screen and enlarged font, scroll/keyboard/FAB. B01–B11 verify identical totals/rates, dates/sessions, report/drilldown, closed/limit, risk, entry edit/history and saved amounts against same fixture. C01–C04 check JPEG page order, photos, totals/pixels for same fixture, customer rebate, real Gallery success/failure, permission and duplicate taps. M4 verify Live and Calendar, closed-day/status and result handover, Better Data four tabs/history, trigger-only formula search, startup/navigation responsiveness; open Research/Rules only to confirm unaffected. Record screenshots and regressions by screen; device QA cannot be substituted by CI.
- **Exit condition:** M5 static audit complete; Build 1924 CI candidate ready for combined device QA. Final acceptance and any needed narrowly scoped fixes remain outstanding. Do not merge main; no user approval inferred from CI.

**M3 verified CI and M4 Specialized UI batch — 2026-11-24 (M4 CI/device QA pending):**
- Build **1923**, commit `30734009d4b107720e6420330d7d1b930035f4d8`, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45356064601, verified SUCCESS: tests, lint, debug/release APK and both uploads passed; debug artifact `14903052518`, release artifact `14902203051`, unexpired at check. M1–M3 source/CI complete (24 scoped items).
- M4 reviewed active routes: `LedgerNav.kt` live -> `LiveScreen`, betterData -> `BetterDataScreen` -> `CherryBetterDataScreen`. Both specialized layouts intentional. Live red result hero and semantic result/number cards are retained, no duplicate glass on data cells; only surrounding blush gradient, stronger cherry navigation colors and wrapping closed-day notice/tab labels. Live collector lifecycle, SET display projection, calendar selection and hero functions compared unchanged.
- Better Data already uses cherry accents, dedicated next-draw subtitle, history and four-tab navigation. Minimal chrome-only adjustments: Home blush canvas, pale blush top bar, cherry Back icon with localized accessibility label, darker inactive tabs and two-line centered labels. Entire content body and pre-Scaffold state/engine/snapshot code verified identical to Build 1923.
- Historical2DTimelineScreen, Research, Rules Library, Home, Forms, Welcome, LedgerNav, global theme, engine, data sources and financial/JPEG paths unchanged. Specialized UI is not a copy of Home's unique hero/dock. No global styles or new animations.
- `SpecializedHomeChromeContractTest.kt`: two source contracts protect Live lifecycle and navigation plus Better Data four tabs and engine/snapshot wiring. One combined commit/build, CI not yet verified.
- Next: verify M4 CI/APK then M5 final baseline-to-head path and protected-region regression audit. Combined phone QA remains required, including Live vs Calendar, result/closed-day status, all Better Data tabs at narrow width and large font, back/history, trigger-only formula search and no new startup delay. Do not merge main.

**C02 verified CI and combined C03/C04 export UI batch — 2026-11-24 (combined CI/device QA pending):**
- Build **1922**, commit `1975074335f9fca0d7689cc2d86d2e94f3fbb6e5`, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45353364198, verified SUCCESS: unit tests, lint, debug/release APK and both uploads passed. Debug artifact `14900639997`, release artifact `14900714542`, both unexpired at check.
- C03: Customer settlement mirrors C02: four selector panels (agent, customer, period, date/session/range); Home blush background, preview rebate panel and page/action toolbar; flexible button heights and scrollable session chips. Selection inputs disabled during generation to avoid changing scope while loading. Rebate inputs disabled during export/permission request. Original range, generation callback, rebate calculation and bitmap preview lifecycle checked byte-identical before C04.
- C04: Voucher, Agent and Customer preview use consistent localized Gallery dialog titles/OK, existing success/failure messages, explicit permission-pending guard and saving/permission progress text. Original exporter calls, agent stale-document verification, permission requirement and renderer outputs preserved. If a settlement renderer is unavailable after permission, show retry feedback rather than silent no-op.
- Combined source test `SettlementAndGalleryHomeChromeContractTest.kt`: three contracts covering customer selector/rebate/preview, all Gallery feedback guards and original exporter/freshness checks. Existing renderer/pagination/money tests retained. C03/C04 committed atomically in one branch update for one CI run, no main merge.
- Next gate: verify combined CI and both APKs, fix any failure minimally; then M4 specialized UI review. Combined device QA pending: actual Gallery success/failure/permission/duplicate taps, MM/EN and large font, small screens, rebate validation and correct exported page totals/pixels. Protected Home/Form/Research, Better Data, renderer/exporter and domain/DB not modified.

**C01 verified CI and C02 Agent settlement Home-first pass — 2026-11-24 (C02 CI/device QA pending):**
- Build **1919**, commit `e4ef7ebbef9e12202af3726e4b9a58b8244df8ef`, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45350881246, independently verified SUCCESS: tests, lint, release verification and both APK uploads passed. Debug artifact `14900970956`, release artifact `14900312535`, both unexpired at check.
- C02 UI commit `5e31e44985972dc71cd2245afff77b65107a94a8`: `AgentSettlementJpegScreen.kt` applies Home blush background and three selector glass panels (agent, preset, date/session/range). Existing horizontal period chips, scrollable session chips, flexible minimum-height generate button. Preview mirrors C01 with one glass toolbar, localized page counter, equal-width navigation and localized flexible-height Gallery action.
- Date-range calculations, agent selection state, generation callback, export callback including stale-document comparison, permissions, bitmap preview lifecycle and ContentScale.Fit preserved. Checked generation/export/bitmap blocks byte-for-byte. No renderer/exporter/layout/pagination/math changes.
- C02 source test commit `59ef59d57c9b4f3e7ffda5deacb716701de3cac4`: three contracts for scope and period, neutral preview and pagination, duplicate/stale export guards. Existing settlement JPEG tests retained.
- Gate: verify latest C02 CI and both APKs; then C03 Customer settlement picker/preview. Combined phone QA pending: all five presets, small screens, large MM/EN font, page swipe/navigation, correct date ranges and agent, real Gallery save and preview freshness rejection. C03–C04 not started. No main merge.

**B11 verified CI and C01 Voucher preview Home-first pass — 2026-11-24 (C01 CI/device QA pending):**
- Build **1915**, commit `a4594f6c7fcdd0b8385dd738fc106e150f871a59`, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45347770993, independently verified SUCCESS: unit tests, lint, release verification and both APK uploads passed. Debug artifact `14899685558`, release artifact `14899840101`, both not expired at check. B01–B11 source/CI complete; combined phone QA remains pending.
- C01 preview commit `8d0cde13f2e4747661a36d23c9180271bb878f11`: `VoucherPreviewPlaceholderScreen.kt` Home blush background, one glass toolbar, separate page counter and equal-width previous/next buttons, localized flexible-height Gallery export button. Existing neutral bitmap canvas, ContentScale.Fit, renderer.render(index), bitmap disposal, pageCount, permission, single-export guard, result message and back protection preserved; preview bitmap block and export callback compared byte-for-byte.
- C01 history commit `e15428a20bdab14c5a55c5f44795a417b8e0c944`: only voucher selection summary typography and flexible minimum height of preview action in `Screens.kt`; entry rows, selection IDs, total, order and routing unchanged.
- C01 test commit `0c9d6652ee531a0124df861e9e20ea32fa875da1`: three source contracts for selection/total, preview page lifecycle and export guard. Existing renderer/pagination tests retained. No JPEG renderer/exporter/layout/pixel or monetary logic files modified.
- Gate: verify latest C01 CI and both APK artifacts; then C02 Agent settlement picker/preview. Phone QA combined later: long MM/EN text, large font, small portrait/landscape screen, page navigation and swipes, actual Gallery save, permissions and preview fit; compare baseline exported pixels and page totals. C02–C04 not started. Do not merge main.

**B10 verified CI and B11 Risk Analysis Home-first pass — 2026-11-24 (B11 CI/device QA pending):**
- B10 Build **1910**, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45345427834, baseline commit `ac96bda929beab8d32035dfc5445040eedb889da`, independently verified SUCCESS: unit tests, lint, debug APK, release APK verification and both uploads passed. Debug artifact `14897908339` and release artifact `14898018197` were not expired when checked.
- B11 selection commit `07a05290b5a9ee75c11ea63829afe1408db423c5`: risk-only Home blush background and five outer glass groups in shared `CustomerReviewScreens.kt`; report mode retains original visible selection content and result routing. Agent/customer avatars, selected scope/date/draw and enabled/back actions preserved.
- B11 result commit `b544e0018a3f3609673b9cd6002b7820c8255523`: `CustomerRiskReviewResults.kt` uses three outer Home glass groups (identity, summary, tab selector), wrap-capable tab chips, full-width identity and adaptive stacked money cards for narrow width, large font or long amounts. Profit/loss semantic colors retained, neutral per-digit tables unchanged. Risk calculation/load state and planner calls verified byte-identical.
- B11 test commit `8ac8f5e117e5478bdacad064a5f96d4e6c84c7db`: three source contracts for selection/report separation, scope/calculation/action wiring and adaptive money layout. CI has not yet been verified. Source contracts do not substitute for device QA.
- Next gate: verify latest B11 CI conclusion and both APK artifacts, repair minimal failures if any, then proceed to C01–C04 export/preview audit. Combined phone QA still pending: long names, large font, MM/EN, narrow screen, full numeric values, selection navigation, neutral table readability and saved-rate results. No main merge or changes to protected Home/Form/Research, Better Data, domain/DB, or export in B11.

**B09 regression fix and B10 Agent Closed/Limit Home Glass pilot — 2026-11-24 (CI and combined phone QA pending):**
- **B09 build recheck outcome:** Builds 1904–1906 **FAILED** during `app:testDebugUnitTest`, not Kotlin compile. Build **1906**, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45339679313 log: `CherryProfileCoverageTest.customerAndAgentOperationalHeadersNeverUseTransactionRows` failed at old `CherryProfileCoverageTest.kt:36`. The test matched the prior exact string `c?.name` and whitespace in `CommissionScreen`; B09 renamed its local UI binding to `customer?.name` while preserving the actual Customer ID and Profile. No Money/DB error reported.
- **B09 targeted test repair:** `CherryProfileCoverageTest.kt`, commit `27b63d2e4df46fe92f5cb0db286ba776c5cb1c8a`, replaces only the two obsolete source substring assertions for Commission and Limit with a whitespace-normalized, function-scoped proof that real `CherryProfileIdentity(vm, ProfileKind.CUSTOMER, id, customer?.name)` and `limitCustomer?.name` are still attached. Other avatar assertions remain strict. Build **1907**, commit `27b63d2e4df46fe92f5cb0db286ba776c5cb1c8a`, Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45344606787, verified **SUCCESS** after Unit Tests/Lint, Release APK verification, and Debug/Release APK upload; both artifacts exist and were not expired when checked.
- **B10 narrow UI source:** `AgentPatternRulesScreens.kt`, commit `cfb0b986a72d5cafe100eecd755c4ba194718235`. `AgentClosedPatternWorkspace` has only 3 lightweight outer `CherryFinancialPanel` groups: Agent identity/date/session context, `RulePatternPicker`/bulk close/reopen controls, and current closed-number list. `AgentLimitPatternWorkspace` has 4 outer glass groups: Agent identity/context, all-number limit input/save, special-digit limit picker/bulk save/remove, configured special-limit listing. Both standalone screens gain a Home-blush `LazyColumn` and one shared glass Date/Session selector pane each. The original picker `RulePatternPicker` and per-digit preview cells are **byte-identical** to Build 1907, with no per-cell shadow.
- **B10 preserved financial/security contracts:** No change to `ClosedDigitSelector.select`, QuickFormat options/preview, agent/date/draw `key` and `vm.closedNumbers/agentAllLimit/agentSpecialLimits` data sources, valid/invalid warnings, `vm.updateClosedDigitGroup` and `vm.removeClosedNumber`, `vm.updateAgentAllLimit`, `vm.updateAgentLimitGroup`, `vm.removeAgentSpecialLimit`, number/amount >0 guard, confirmation dialogs and state reset, and active/current entry separation. No agent/customer limit math, background sync, Room/domain or transaction edits.
- **New source test:** `AgentClosedLimitHomeChromeContractTest.kt`, commit `dd2ce058de8217b888fdb4662e50118e60c10a1b`, four source contracts verify scope, 3/4+1+1 outer glass panels, closed/limit CRUD and digit-selection invariants, two Date/Session standalone selectors and no per-cell heavy blur. These tests are not independent numerical fixture validation or device visual approval.
- **Diff:** Changes since post-B09 Build 1906 are only `AgentPatternRulesScreens.kt`, `AgentClosedLimitHomeChromeContractTest.kt` and corrected `CherryProfileCoverageTest.kt` plus this doc. `AgentLimitScreen.kt` remains an unchanged delegation to the standalone limit screen. No edits to Home/Weekly, accepted Management UI, profile crop/forms, Research/`Historical2DTimelineScreen.kt`, Better Data, SET Live, other financial screens, domain/DB or export.
- **Quality gate:** Build **1910**, GitHub Actions https://github.com/thetmyopaing4889-svg/Ledger2D/actions/runs/45345427834, is the B10 code+test+plan checkpoint; **CI and APK remain unverified**. Do not claim success until both are checked. If CI fails, inspect the failing job log and make a minimal source/test fix before continuing to B11. User explicitly deferred all B09/B10 and prior screen phone styling acceptance until one later combined verification. On phone confirm long Burmese labels, small screens, both Agent Profile identities, AM/PM and date switch resets, no blocked bulk action buttons, preview 00–99 digits, confirmation/delete dialogs, saved limits and closed numbers matching baseline.

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
