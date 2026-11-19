# Cherry 2D — SET Intermission Morning LIVE Integration (from successful Build 1811)

## Observed evidence
- Build 1806 did not show an eligible morning SET LIVE result on Nov 19. That log only contained a later post-cutoff snapshot and did not establish why earlier GETs were absent.
- The separate Build 1810 15s test showed repeated SET snapshots. Build 1811 proved 16/16 HTTP 200 responses in a ~45-second 3s target foreground test, with 11 changes, but did not prove official morning handover or permission to poll indefinitely.
- Nov 18 Colab: Yangon source stamp 12:00:53, Intermission, 2D 60; received by phone/test at 12:01:19, matched Luke on that observed day.

## Eligibility, authority and safety
- SET is UI-only: never feed SET numbers to Luke, Room, history, analysis, winning numbers, settlement or auto-save. Luke remains the sole official final and takes precedence.
- Open Cherry LIVE: SET warm-up 11:59 Yangon; show fresh SET LIVE from 12:00, otherwise Luke fallback. Keep Luke polling unchanged.
- A SET UI cutoff candidate must satisfy BOTH: Market Status exactly Intermission AND SET Source Update Yangon time between 12:00:00 and 12:01:00 inclusive.
- Choose the ELIGIBLE observation closest to 12:01 without crossing it (maximum eligible source time). The phone receive time must not control the selection.
- A newly observed source stamp strictly beyond 12:01:00 seals previously recorded eligible observations, but does NOT itself count as a result.
- Actual SET snapshots can regress to older timestamps or mix Index/Value with unchanged source timestamps. Deduplicate same values and quarantine contradictory stamp, preferring the closest earlier unambiguous Intermission if available. Do not invent a result if none.
- Morning UI result is labeled a SET preview while awaiting Luke, not an official Updated indicator. Luke confirms/corrects; only Luke persists.
- Only bounded foreground SET requests while the LIVE screen is visible, within 11:59–12:04; 3s target starts sequentially, with backoff and stop on HTTP 401/403/429. Do not bypass SET access controls.
- Same-day SET UI-only persisted selection is valid to restore ONLY if Intermission and its source stamp meets the cutoff rule. Invalid older saved results are refused.

## Packaging / regression
- Full Cherry LIVE main launcher (not the standalone 3s test). Debug package suffix .setintegrationtest installs alongside all existing Cherry/probe apps. Main is never edited.
- Release identity unchanged. Release artifact is CI verification only, generally unsigned; install the debug APK for testing.
- Preserve Research Workspace, Better Data, formulas, Luke, Room, Ledger and all existing features.
- Tests: actual Colab example, Open vs Intermission, inclusive cutoff, source regressions, contradictory same stamp, result persistence and correction, source parser Open2 normalization, closed day and lifecycle.
- CI: testDebugUnitTest, lintDebug, assembleDebug and assembleRelease.
- On-device 11:59–12:04/actual Luke final validation remains required; the 3s probe test alone is not proof of successful morning integration.
