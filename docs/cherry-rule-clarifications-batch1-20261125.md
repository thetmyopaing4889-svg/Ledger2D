# Missing rule clarification — user batch 1 (2026-11-25)
Baseline: Build 1946 passed CI; previous resolution queue d029950956555be45030dc573fa28cf8c5fb6421. This is a requirements record, not implementation. Keep original source wording and prior snapshots unchanged. User clarification takes precedence for future evaluation; no retroactive score or claim of future reliability.

| No. | Canonical ID | User-confirmed meaning / status |
|---|---|---|
| 1 | cherry-001 | Previous day's AM and PM head or tail equality, applicable to both AM/PM of today, one-day lifetime. Example 10 and 15: 1−1=0, or 0, or doubles. User describes alternatives rather than a multi-day sequential chain. |
| 2 | cherry-002 | Same one-day scope as 001. ပြန်ကျ means an identical two-digit result returning within the same week, not digit reversal. |
| 3 | cherry-003 | Same one-day scope as 001. Cross-diagonal example accepted: compare AM head + PM tail with AM tail + PM head. |
| 4 | cherry-018 | User will look for the original source and clarify later; pending. |
| 5 | cherry-022 | User says source unclear; pending, no invented trigger. |
| 6 | cherry-024 | User confirms Monday AM 11, PM 21 is three occurrences of digit 1; remaining Tuesday–Friday in that same week. Both Monday sessions needed. |
| 7 | cherry-025 | Any 9 occurrence in either Monday result, either digit position; lifetime that week. |
| 8 | cherry-026 | Lifetime that week; return of an already drawn exact pair within that same week, not reversed digits. |
| 9 | cherry-027 | Lifetime that week (both branches under the same weekly scope). |
| 10 | cherry-031 | User says ဖယ်မယ် (remove); do not implement. Whether to also remove reader entry or only exclude from automation is not yet explicitly settled. |

## Verified existing terminology
TwoDAnalysisEngine.kt at baseline implements exact-pair repeat events from current-week chronological results; repeated count is occurrence count minus one. Example 12 and 21 are not identical. Reuse definition, never equate R/reverse with ပြန်ကျ.

## Remaining implementation questions
- Rules 001–003: whether previous day means previous trading day across weekends/holidays, and 001 zero-minus-one wrapping; if both head and tail match, clarify use of both. For 002/003 and 026, clarify whether candidate repeat reference set is all results already recorded in the same week or just the triggering result(s); user describes same-week exact repeat but not candidate membership. Whether one-day evidence stops upon AM hit or remains available PM is not explicitly resolved.
- 031: removal scope (reader as well or computation only). Preserve source archive unless explicitly authorized.
- 018/022 unresolved. This commit does not change code, build, ranking, original sources, or score archive. 

## User follow-up (2026-11-25, final clarification of four questions)
- Rules 001–003 may be active only on the following day within the same week; no Friday-to-next-Monday carryover. One-day scope includes AM and PM. Do not assume carryover across a midweek closure or whether an AM hit ends the PM signal; these edge cases have not been explicitly settled.
- 001: subtracting one from zero wraps to nine.
- Exact repeat references: all two-digit results already recorded in the same week, not reversed pairs; use only observations strictly before target.
- 031: exclude from Better Data only. Preserve 2D Analysis reader and original source; do not delete the library entry.
- Batch 1: seven principal rule specifications clarified; 018 and 022 pending; 031 intentionally excluded. No code changes in this clarification commit.

## Final batch 1 disposition
User explicitly excludes pending cherry-018 and cherry-022 from Better Data only, in addition to cherry-031. Preserve all three in the 2D Analysis reader and original source. Seven rules approved in principle, three intentionally excluded. No application code changes in this commit.
