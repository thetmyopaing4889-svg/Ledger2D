# Cherry 2D Ledger — consolidated EN/MM UI standard rollout

Baseline: verified experiment Build #939 on `experiment/font-typography-lab-build930`.
Backup: `backup/pre-consolidated-ui-typography-build939-20261104`.

## Intent
One integrated build rather than repeated small font experiments. Preserve all Cherry art, business logic, financial reports, voucher and statement JPEG rendering, database models, live result polling and route keys. **No UI from-scratch rebuild.**

## Changes in this single batch
1. **App-wide font-family selection**: English uses the existing SansSerif; Myanmar uses bundled Noto Sans Myanmar UI. Applied to all 15 Material Typography roles; the pre-existing role **sizes, weights and line heights remain unchanged**. UI elements with explicit custom fonts/styles remain as authored.
2. **Unified selection field**: `ScopeDropdown` defaults to the previously device-verified Settings compact style; maintains minimum field height of 68dp for accessibility, localized label and 14sp value. The full selected names are available in dropdown menus. Agent/Customer landing scopes, other workspaces, Customer List and Settings now share this default.
3. **Narrow workspace fix**: Agent and Customer selectors in the Customer Feature Workspace are stacked full-width in *both* languages. Date and draw chips are separated into their own lines so date/chip text isn't crowded by mixed-language label lengths.
4. **Quick Entry**: Reuses the shared scope selector while preserving all selections and routes.
5. **Forms and DateInput**: Same label/value typography, 68dp minimum height and original validation/DatePicker semantics. Scrolling forms reserve 96dp FAB clearance.
6. **Scope pages**: Scrolling and FAB clearance for agent/customer scope selection.
7. **Settings Myanmar explanatory copy**: Removes inconsistent embedded English while preserving English version and behavior.

## Invariants
- No changes in `core/domain`, data layer, `LedgerViewModel`, result integration, calculations, Agent/Customer report source, settlement/voucher exporters, or JPEG renderers.
- No changes in bottom navigation, Cherry header art, fixed Home card geometry, Quick Entry parser or legal draw/session semantics.
- No deliberate ellipsis of money amounts. Large figures in financial report tables may still require separate horizontal scrolling/width allocation. This batch does not pretend to resolve every table or JPEG layout.
- Screen heights may still differ naturally when a translated sentence has more lines; priorities are legibility, stable controls and scrolling, not artificially identical total page height.

## Acceptance
- All unit tests, lint, debug APK and release APK pass.
- Verify English and Myanmar Home, Settings, Agent/Customer landing, both scope selectors, Quick Entry, Agent/Customer forms, Customer List and a representative report.
- Confirm date selection and AM/PM chips still update working draw, and selected agent/customer IDs still feed correct routes.
- Test 100% and >100% Android accessibility text scale where practical. Check long customer/agent names in dropdown.
- Confirm money calculation and exported JPEG match prior accepted #939 behavior. Do not merge into stable branch until real-device visual review.
