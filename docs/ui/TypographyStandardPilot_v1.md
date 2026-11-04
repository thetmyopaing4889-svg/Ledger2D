# Cherry 2D — Opt-in Typography Standard v1 (Pilot)

Baseline: build #930 (commit 4b322b0). Experiment baseline: build #932 (commit e0f4bc0).

## Safety boundary

- English uses the current SansSerif family.
- Myanmar pilot uses Noto Sans Myanmar UI Regular, Medium, SemiBold and Bold from the official Noto Myanmar repository (SIL OFL 1.1). This is distinct from the plain Noto Sans Myanmar font in the Typography Lab.
- Each role has a size, weight and line-height token. No fixed English-to-Myanmar size multiplier is assumed. The first UI-font pilot preserves English nominal size and line height to isolate font metrics.
- **Do not install these tokens globally in MaterialTheme** until each screen is verified.
- For the Settings pilot, only its section headings and explanatory/status text opt in. All layout modifiers, cards, selection fields, radio controls, buttons and their contents remain unchanged.
- Font fitting and text length are **not** a replacement for layout adaptation. Wrapping, narrow selectors, home weekly card and financial amounts are tracked as component/layout issues for later phases.
- JPEG renderers use Canvas px units, not Compose sp, and are excluded.
- Financial numbers must never be intentionally ellipsized in a future layout rollout.

## Candidate role definitions

| Role | Size sp | English line sp | Myanmar line sp | Weight |
| --- | ---: | ---: | ---: | --- |
| App header | 19 | 25 | 25 | SemiBold |
| Section title | 17 | 23 | 23 | Bold |
| Card title | 16 | 23 | 23 | SemiBold |
| Body | 14 | 21 | 21 | Normal |
| Helper | 13 | 19 | 19 | Normal |
| Dropdown label | 13 | 19 | 19 | Normal |
| Dropdown value | 16 | 24 | 24 | Normal |
| Button | 14 | 20 | 20 | Medium |
| Money | 18 | 24 | 24 | Bold |
| Table | 12 | 18 | 18 | Medium |

## Acceptance gate

1. CI unit tests, lint, debug and release APKs succeed.
2. Confirm Settings in English and Myanmar on the same device, same font scale.
3. Verify no glyph clipping, broken lines or inaccessible settings.
4. Verify language switch preserves navigation and control semantics.
5. Do not merge pilot into production without visual review and approval.

## Noto UI pilot correction after rejected #934 screenshots

- #934 Myanmar Settings remains visually unapproved.
- Pilot switches only opted-in text from plain Noto Sans Myanmar to official Noto Sans Myanmar UI.
- Source: notofonts/myanmar gh-pages fonts/NotoSansMyanmarUI/unhinted/ttf. SHA for all four original font blobs verified after upload.
- Existing SIL OFL 1.1 license remains at app/src/main/assets/font-licenses/OFL-NotoSansMyanmar.txt.
- English and Myanmar nominal size and line height are temporarily equal; no presumed optical ratio or auto 1sp shrink.
- App theme, Cards, Dropdowns, Home, Reports, analysis, JPEGs and underlying data/logic are unchanged.
- No production merge until device screenshot comparison verifies acceptable hierarchy, clipping and wrapping.
- FAB overlap and unscrollable Settings are separate layout issues; not claimed fixed by a font change.
