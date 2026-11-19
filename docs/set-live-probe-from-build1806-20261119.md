# SET REAL LIVE Probe — isolated debug APK from Build 1806

## Goal
Prove Android fetches **real** SET Index / Value / source timestamps repeatedly and paints
changes in a LIVE-like preview **without waiting until Myanmar 11:59/12:01**.

## Limits and isolation
- Debug-only SET LIVE Test launcher. The normal Cherry MainActivity launcher is
  disabled **in this debug APK only**; release manifest stays unchanged.
- The debug process uses :setprobe, where LedgerApplication skips production
  Room / WorkManager / winner-auto-save startup. No Luke requests or writes.
- Never fabricate a snapshot, 12:01 cutoff, official result or session settlement.
- Reuse the proven Build 1806 SetSnapshotFetcher / jsoup parser for real HTML.
- User taps Start; one GET immediately, then bounded >=15-second spacing,
  max 16 attempts; stop at any time, on background, 401/403/429 or maximum.
- UI shows true SET 2D calculated from live HTML, Index / Value / source
  update times, market status, source age, last GET timestamp, request latency,
  HTTP response and counts for unchanged vs changed snapshots.
- Source freshness (>90 s or OffHour/Closed) is called out; do not label it live
  when data is stale. Outside exchange hours a successful GET may be unchanged.
- Trace can be copied straight from the screen for debugging.
- Uses no actual SET final result logic; 3s main production polling and
  Yangon 12:01 selector require a separate session-specific test.
- SET rate/access restrictions apply; stop on HTTP 401/403/429.

## Verification
CI: testDebugUnitTest + lintDebug + assembleDebug + assembleRelease.
Inspect the debug APK manifest for only one enabled launcher.
Compare against successful Build 1806 / Build 1796 to check no unrelated
feature code changed. Finally test on a real Android phone while SET is trading.
