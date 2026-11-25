# Source coverage repair batch 1
Baseline audit: 69dec78af2e178d199281ec2fd42efac46cdbbd9.

- Correct explicit R triggers for reader IDs 043, 052–054, 076–077, 079–082, 084. Existing stable IDs/output sets retained; current and (for 076/077) born paths use both directions. Prior archives are not rewritten. Remaining expiry and source scope gaps in audit still apply.
- Add reader 037 (10R -> 7 break) with existing duration-free next-draw convention, and reader 057 (94R -> digit 1) with five calendar-day interpretation and existing stop-on-hit born policy. Neither added ID is awarded a whole-library score by this batch. No historical backfill.
- Regression tests: both trigger directions, non-trigger, direct-only source, born completion and expiry, future-input exclusion. CI branch enabled. UI redesign and proposed selection-order changes deferred. No claim of full source coverage or predictive performance.
- Pending: original source clarification for missing/partial rules, exact draw/week horizons, IDs and score semantics, source references and full coverage re-audit. Added image originals were not available in this pass.
