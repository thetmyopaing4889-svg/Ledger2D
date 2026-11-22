# Cherry 2D Home visual correction from Build 1828 — 2026-11-22

Base: successful Build 1828, commit 9426ab3a74494f425077bd11d75c9a84002989e7
Branch: fix/home-orb-footer-card-polish-from-build1828-20261122

## Scope
This pass is strictly Home UI styling and Home layout corrections. Do NOT alter
main, Live SET result polling, historical result persistence, 2D Analysis,
Research, Better Data, Rules Library, monetary calculations, or approved text.

## Device screenshot issues
- 1828 Ruby Cherry was still a flat glossy gradient with no curved glass
  reflection; footer text overlapped its floating footprint.
- Agent/customer and weekly cards showed rectangular pale blocks behind their
  content; blurred-image RenderEffect layers were suspect.
- The commission card's bottom row was clipped because parent Column assigned
  a remaining-height weighted constraint instead of natural content height.
- Weekly circular badges had octagonal/inner layered edge artifacts.
- LIVE used repeated ribbon strokes; original target uses broad liquid-silk folds.

## Changes
- Keep successful Header/Settings/date layout from 1828 intact.
- Draw the 76dp ruby sphere with multiple radial optical layers, an upper
  crescent, reflected oval lighting, rim highlights and depth shadow. Preserve
  Scaffold floatingActionButton and the identical Quick Entry callback.
- Reduce false glass rectangles by removing the nested count-card and
  commission-card rectangular overlays; replace heavyweight blur watermarks
  with extremely faint cherry silhouette images plus soft radial halos.
- Render weekly 40dp result badges with a single exact circular Canvas
  gradient and border rather than nested elevated transparent Surfaces.
- Draw LIVE folds as filled asymmetrical curves with transparent highlights.
- Use intrinsic Commission Card height and permit vertical scrolling only if
  the unchanged Home content order cannot fit. Preserve complete money amounts.
  Reduce the cumulative highlight's vertical padding while retaining text.
- Reduce excess shortcut label reserved height and visually lift the footer
  above the Quick Entry orb; original text remains unchanged.
- Update source-level regression checks for the above structure and callbacks.

## QA needed before visual sign-off
GitHub unit tests, lint, debug/release APKs and artifact upload are required.
Then compare a new on-device screenshot at the user's actual font scaling and
display density against the Gemini target: 3D sphere, footer clearance,
count-card squares, commission bottom row, LIVE silk, weekly round badges,
visible header date, Settings tap target. Check 320/360/393/412dp widths,
phone navigation insets, accessibility, and real-world responsiveness.
The source tests do not prove visual parity; do not claim they do.
