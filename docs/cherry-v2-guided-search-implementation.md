# Cherry V2 — Guided Search Schedule Implementation Notes

**Status:** Engine-only experiment; **not connected** to user-facing Search, storage or Champion.  
**Baseline:** Build #1641 at `d9fdb5837aa7af72835aaa6b7bf9072669697eb7`.  
**Working branch:** `feature/cherry-discovery-v2-from-build1641`. No changes to original DOC/Gemini, Analysis, Research, or main.

## Why change search ordering?

V2's earlier `CherryV2ExpandableGrammar.expressionAt(index)` was open-ended, but an ascending, undifferentiated raw enumeration explored many shallow or semantically equivalent candidates before visiting useful combinations of previous-draw digits. A finite 30-second search therefore needed more structural diversity early.

## Deterministic, interleaved schedule

`CherryV2GuidedSchedule.expressionAt(slot)` preserves the original first nine base features (slots 0–8). Starting at slot 9, every three slots are interleaved:

1. **SIMPLE:** the original unlimited index grammar, retaining exhaustive eventual coverage of the original symbolic space;
2. **CROSS_LAG:** combinations of top/tail/break from up to six previous confirmed draw slots, plus deeper nested expansions when shallow combinations are exhausted;
3. **NESTED:** three-input arithmetic/nonlinear expression combinations, later expanded with raw grammar subexpressions.

Examples: slot 9 remains `top4`; slot 10 is `(top1+top2)`; slot 11 is `((top1+tail1)+top2)`; slot 280 is `(top1+tail2)`. The useful two-lag `(top1+top2)` expression is therefore seen at guided slot 10 instead of raw grammar index 73, which demonstrates earlier *coverage* for that one pattern, not a verified forecasting advantage. All are deterministic and seekable without a growing in-memory priority queue. The SIMPLE lane maps raw grammar index `i >= 9` to slot `9 + 3*(i-9)`. No raw grammar expression is permanently excluded; scheduled guided candidates can still be behaviorally equivalent to others and are subject to the existing bounded semantic dedup.

**Important:** This is *diversity-guided structural priority*, **not yet data-guided quality ranking or Genetic Programming**. A better search order might find an in-sample 10+ match sooner for some histories, but must not be described as predictive proof or guaranteed better.

## State safety and compatibility

- Search `nextIndex` now means **schedule slot**, not a raw grammar index. The historical scorer still receives the exact expression for that slot and uses fixed offsets across all past draws.
- `CherryV2ExpandableGrammar.VERSION` is advanced to the schedule version **2**. The existing integrity-checked checkpoint codec rejects V1 grammar IDs when decoding a V2 resume checkpoint.
- Previously selected historical streaks remain first-found within one homogeneous versioned scan; future UI integrations must not silently mix cursor versions.
- The 30-second time budget, cooperative cancellation/checkpoint, bounded memory dedup, no future-result access, and selected category semantics are unchanged.

## Required verification / remaining work

- GitHub Android CI must pass the exact new head SHA (unit tests, lint, APK). The unit fixtures include preservation of raw features, interleaving, eventual coverage, a nonconstant synthetic cross-lag 10+ historical match, and deterministic cancellation.
- Before claiming an **actual quality improvement**, benchmark legacy sequential search against this schedule on identical bounded history and identical wall-clock budgets, with a fair replay and held-out/prospective checks. Report candidates/sec, distinct behaviors/sec, earliest 10+ historical fit, CPU/RAM and forward outcomes separately. A synthetic test cannot prove better real-world forecasting.
- Guided search still lacks adaptive multi-armed scheduling or validation-backed priorities. Candidate semantic dedup uses a bounded recent cache, not a lifetime exhaustive behavior store.
- No Android real-device performance results exist yet. No UI/Champion integration authorized or implemented by this change.
