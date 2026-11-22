# Home Premium — Gemini styling fidelity / Build 1826 corrections
Date: 2026-11-22
Base: successful Build 1826 (d2aefc1486b3fcb90ec6586e3d6ecad7c287ce4f)
Fix branch: fix/home-frosted-glass-orb-from-build1826-20261122

## Scope & constraints
- Retain Home's frame hierarchy: illustrated header, three top feature cards, weekly results, compact commission, four quick actions, footer, bottom navigation, and exactly the same labels and callbacks.
- The prior Build 1826 looked flat and had two user-observed regressions: the header date was occluded by the sheet, and the center Cherry orb was cut off at the nav boundary.
- No changes to historical result loading, persistence, commission mathematics, SET/LIVE polling, Better Data, Formula Discovery, Research, Rules Library, or main.

## Approved visual specification
1. Header: increase heroHeight to 55% of display width (198–226dp), restore a less dark overlay (.12/.08), use 24dp sheet overlap, keep full artwork visible behind sheet corners. Keep date text unoccluded. Remove the dark backdrop ring from the ellipsis; keep the real Settings callback and 48dp accessible tap target.
2. Backdrop: draw two low-opacity blurred cherry-mark watermarks only behind Home cards, never over text or results.
3. Glass cards: ~70% white alpha, thin white specular borders and soft shadows for agent/customer count, weekly and commission surfaces. No thick opaque-white coating masking the backdrop.
4. LIVE: deep red/magenta gradients and four broad curved silk ribbons rather than four thin lines; radial glossy signal orb with specular highlights. No new infinite animations.
5. Weekly table: preserve the actual numbers, grid coordinates, and swipe/tap logic. Change only the fixed 40dp result circles to semi-transparent glass badges with white borders and elevation, and glassy left/right arrows.
6. Commission: preserve all amounts, data, labels and full-width large-number fallback. Show the right-side cumulative value in a pale-pink frosted 14dp rounded highlight with 1dp bright border and soft shadow.
7. Quick actions and nav: frosted circular shortcuts, translucent pearly bottom bar with 1dp white highlight. Preserve all five nav destinations and bottom-system-inset behavior.
8. Main Quick Entry orb: preserve real onQuickEntry action, 76dp diameter, 82dp holder, ruby-pink radial-glass sphere, oval white specular reflection and shadow. Crucial: move the orb into Scaffold.floatingActionButton with FabPosition.Center so it is drawn ABOVE the bottom nav without its upper half being clipped by the BottomBar slot; keep BottomBar's height and alignment unchanged.

## Verification
- CI test, lint, debug APK, release APK and artifact upload are mandatory.
- Added source-level unit tests to prevent regressions in exposed clipping path, material alpha, required navigation callbacks and source text.
- Device screenshots on user phone remain required. Check 320/360/393/412dp widths and Burmese/English, both header corners, entire date, Settings click target, whole 3D orb, swipe/weekday badges, commission values, footer visibility, quick-entry navigation and translucent card contrast.
- Because runtime images can't be proven correct by CI, do not call this visually final without a device screenshot.
