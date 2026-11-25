# Partial coverage implementation batch 5 — Build 1966 baseline
Parent bb41166502fc814abd9e453ba595099646539976, CI run 45635635978 succeeded including unit tests/lint/release verification/uploads. User-approved batch 5 decisions recorded at 3e353b21cae4e538371ba9b9cbba4f32ad9bd345.

- 41/42: 80R→1264 and 67R→8123 including doubles, 16 pairs, same-week Born only, stop on first observed hit. Remove duplicate Current and old five-working-day Born branches.
- 43–47: 74R→8 digit, 48R→6 tail, 71R→2 digit, 15R→4 digit, 13R→3 break. Existing immediate Current implementations match approved scope; verify all exact pair sets, both trigger orientations, absence of stale/Born evidence.
- 48: literal mob group 17/20/80/56/51 now independent weekly Born doubles; separate existing 1267 wheel. No implicit R. Each stops on its own hit.
- 49: literal AM 75 opens next two calendar dates only. Use scoped start date to prevent same-day PM from prematurely retiring the rule. No separate repeat prerequisite (approved). 50 remains fully excluded from Better Data; reader intact.
- New and updated tests cover current/Born boundaries, exact pair sets, no-R negatives, independent retirements, 75's start/expiry, and Ko Khant exclusion.
- Previous batch 31–40 checked against reader, implementation and successful CI. All 50 partial dispositions now addressed in code or verified existing code: 49 retained, 1 excluded. Latest commit CI pending; no phone testing, no claim of complete source-to-score integration or predictive performance.
- Next gate: audit all 98 source dispositions, reconcile changed IDs and historical scores prospectively, resolve documented earlier ambiguity, check runtime performance before premium UI redesign. No source assets, archives, main, Home, Research, LIVE or Math edits.
