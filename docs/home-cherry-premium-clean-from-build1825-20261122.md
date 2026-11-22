# Cherry Premium Clean — Home UI upgrade from Build 1825
Date: 2026-11-22
Base: Build 1825 / e91f3c17ffc60e5a14da405756705f02b8843506
Branch: feature/home-cherry-premium-clean-from-build1825-20261122

## User-approved visual scope
Upgrade only Cherry 2D Home UI. Keep all destinations, financial calculations, data/results, Better Data, 2D Analysis, Rules Library, LIVE and Research untouched.

### Header
- Keep the original cherry_header_art artwork. It is a background asset and does not require re-creating realistic cherries or blossoms in Compose.
- Eliminate the transparent/white wedge by removing the header's independent bottom rounded clip and drawing its full illustration under the upper 30dp content-sheet corners. Sheet overlap = 36dp.
- Remove the non-clickable notification icon/red badge, which had covered the illustrated cherry.
- Center "Cherry 2D" and Burmese/English subtitle in clean typography, with a small plain display-only date, no deceptive calendar chevron/card.
- Replace the large gear with a small accessible More (ellipsis) control that opens the exact existing Settings callback.

### Cards
- LIVE retains the existing onLive callback and has a static illustrated satin-wave gradient, glassy highlight and 3D-like signal indicator. No new heavy animation/blur.
- Agent / customer count cards use soft translucent pink-white surfaces; numeric contrast stays high.
- Weekly Results card retains swipe, arrows, selection and all data; change only container appearance.
- Commission card retains every number, wide-mode formatting and detailed navigation; change only soft surface/border/shadow.
- Quick shortcuts use a mild glossy round icon surface, preserving all four actions.

### Bottom navigation
- Keep the five real navigation destinations and the platform bottom inset handling.
- Use a soft floating-glass-looking gradient bar with separated rounded circular nav icons.
- Emphasize Quick Entry with a 76dp glossy Cherry sphere (static gradient + highlight + shadow); preserve original callback and press-state feedback.

### Performance & regression
- Never use realtime blur, offscreen bitmaps, network image fetches, or new infinite animations.
- Header geometry is responsive, and LIVE indicator/font sizes adapt for widths <360dp; all buttons keep real callbacks.
- Keep build/test/lint/debug and release APK verification.
- Manual device QA: 320/360/393/412dp widths, both Burmese/English, status/nav-bar safe area, icon taps, top sheet seam on both sides, weekly table row visibility and financial amounts on small screens, dark icon contrast, Settings, Quick Entry, LIVE, results and all shortcuts.
- CI build success alone does not prove pixel-perfect rendering; compare actual screenshots against approved reference before declaring visual acceptance.
