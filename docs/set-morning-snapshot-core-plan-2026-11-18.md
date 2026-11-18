# Cherry 2D: SET Morning Snapshot Core (Phases 1–2)

## Scope
- Experimental UI-only SET source, developed from successful CI build 1796.
- Luke remains the ONLY authority for `result_1200`, Room, history, analysis, and auto-save.
- No existing LIVE collector, schedule, screen, or Luke source has been changed in this phase.
- Never treat SET snapshots as verified results before the Luke comparison.

## Next phases (not yet implemented)
1. Check SET website access and permitted request frequency from Android.
2. Start SET warm-up at Yangon 11:59 while Luke LIVE continues.
3. At 12:00, hand over **only** the visible LIVE hero to SET if its source timestamp is fresh; otherwise stay on Luke.
4. Keep Luke polling and normal final synchronization active in parallel.
5. After observing a source timestamp strictly later than Yangon 12:01:00, choose the latest recorded snapshot with a source timestamp <= 12:01:00. This may reach the phone after 12:01.
6. If the selected snapshot is not fresh enough (currently at least 12:00:00) or source timestamps conflict, do not claim a SET display result; fall back to Luke.
7. Route SET only to a separate UI presentation state, reusing the existing blinking LIVE number. Never create a Luke feed or database entity from it.
8. On verified Luke `result_1200`, compare and reconcile the UI. Keep comparison diagnostics.
9. Verify offline tests, on-device network behavior, closed days, midnight reset, background transitions, and regression tests.

## Limitations
- Page timestamp may not be an atomic timestamp for Index and Value.
- The cutoff rule has matched only one observed morning so far.
- A 3-second UI refresh does not justify 3-second polling of the SET webpage. Respect SET terms and request limits; do not circumvent access restrictions.
- Fetching the HTML page directly on Android has not been verified on device.
- This branch does not create a production UI result and does not change app behavior.
