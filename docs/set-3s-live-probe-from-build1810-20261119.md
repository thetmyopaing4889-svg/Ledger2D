# SET LIVE 3s Probe — isolated diagnostic APK (based on successful Build 1810)

## Intent
Compare a bounded **3-second target cadence** against Build 1810's 15-second
Android SET LIVE test on real SET HTML, without waiting for the morning 12:01
cutoff. This is a practical Fetcher/Parser/live-preview experiment, not proof
of successful morning handover or a SET official result.

## Implementation
- Launch as `SET LIVE 3s Test` in a separate DEBUG applicationId:
  `com.myanmar.ledger2d.setliveprobe3s`. Neither the original Cherry app,
  the rulespreview debug app nor the 15s SET test is replaced.
- Reuse **unchanged** successful Build 1810 `SetSnapshotFetcher`, jsoup
  `SetSnapshotHtmlParser`, 2D calculation and source timestamps.
- Tap LIVE Start: GET immediately, target 3000ms *between request starts*,
  with **no concurrent/overlapping fetch**. If one GET takes 3s or longer,
  no second GET is started while it is pending. After completion a 300ms
  minimum gap prevents burst retry.
- Limit to 16 consecutive attempts (approximately 45 seconds for
  fast responses). Stop on user command, Activity STOP/background,
  HTTP 401/403/429, or attempt cap. No access-control evasion.
- Show successful versus failed attempts, source-stamp/value/index changes
  versus SAME snapshots, actual GET start timestamps down to milliseconds,
  receipt timestamps, SET Index, Trading Value, computed UI-only 2D,
  Thai/Yangon source times, market status and source age.
- Copy trace to clipboard for comparison with the 15s video.
- Market closed/stale data must not be misrepresented as current live data.
  More GETs do not necessarily imply more source updates.
- Main application official Luke Result, Room, History, auto-save, Research
  and Better Data are untouched. Debug `:setprobe` process continues to
  avoid starting production WorkManager and database features.
- The source's access rules apply; 3s is the **test target**, not an assertion
  that SET has authorized this cadence.

## Acceptance
- CI `testDebugUnitTest lintDebug assembleDebug assembleRelease` succeeds.
- Download **ledger2d-debug-apk**, *not* the release APK, from the latest
  successful build for this branch.
- On-device test while SET is updating: verify 16 attempts or STOP, HTTP
  status, request start intervals and at least two different source stamps
  if the website updates during observation. If data does not change
  during off-hours, that alone does not mean Fetcher failure.
- This experiment does not yet validate the 11:59 warm-up, 12:01 selector,
  UI handover or final Luke-vs-SET reconciliation.
