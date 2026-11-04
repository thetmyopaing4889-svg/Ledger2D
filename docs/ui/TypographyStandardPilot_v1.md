# Cherry 2D — Opt-in Typography Standard v1 (Pilot)

Baseline: build #930 (commit 4b322b0). Experiment baseline: build #932 (commit e0f4bc0).

## Safety boundary

- English uses the current SansSerif family.
- Myanmar pilot uses the bundled OFL-licensed Noto Sans Myanmar variable font from the isolated lab.
- Each text role has a nominal size, weight and per-language line height.
- **Do not install these tokens globally in MaterialTheme** until each screen is verified.
- For the Settings pilot, only its section headings and explanatory/status text opt in. All layout modifiers, cards, selection fields, radio controls, buttons and their contents remain unchanged.
- Font fitting and text length are **not** a replacement for layout adaptation. Wrapping, narrow selectors, home weekly card and financial amounts are tracked as component/layout issues for later phases.
- JPEG renderers use Canvas px units, not Compose sp, and are excluded.
- Financial numbers must never be intentionally ellipsized in a future layout rollout.

## Candidate role definitions

| Role | Size sp | English line sp | Myanmar line sp | Weight |
| --- | ---: | ---: | ---: | --- |
| App header | 19 | 25 | 27 | SemiBold |
| Section title | 17 | 23 | 26 | Bold |
| Card title | 16 | 23 | 25 | SemiBold |
| Body | 14 | 21 | 23 | Normal |
| Helper | 13 | 19 | 21 | Normal |
| Dropdown label | 13 | 19 | 20 | Normal |
| Dropdown value | 16 | 24 | 26 | Normal |
| Button | 14 | 20 | 22 | Medium |
| Money | 18 | 24 | 26 | Bold |
| Table | 12 | 18 | 20 | Medium |

## Acceptance gate

1. CI unit tests, lint, debug and release APKs succeed.
2. Confirm Settings in English and Myanmar on the same device, same font scale.
3. Verify no glyph clipping, broken lines or inaccessible settings.
4. Verify language switch preserves navigation and control semantics.
5. Do not merge pilot into production without visual review and approval.
