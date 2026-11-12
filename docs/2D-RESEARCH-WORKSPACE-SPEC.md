# Ledger2D — 2D Research Workspace (Version 1 implementation contract)

**Status:** Research Atlas redesigned on `feature/2d-research-workspace`;
Android CI/build and real-device acceptance are still unverified. This is a
separate user-directed research tool, not A-09 and not Rules (မူ).
**Reference:** `docs/2D-ANALYSIS-MASTER-SPEC.md`, Part IX, and the user's
subsequent approval to lead implementation on 2026-11-11.
**Acceptance:** Source implemented does not imply tested APK or user phone acceptance.

## 1. Product purpose

Offer a guided, reusable way to ask an exact historical 2D question and inspect
actual eligible draw evidence. Do not create synthetic real-world outcomes,
predict the next result, or claim history-based odds.

Version 1 permits:
- Typed SOURCE conditions: **exact pair 00–99**, **digit present at either position**,
  **tens digit**, **ones digit**, **double 00/11/…/99**, or **tens/ones parity**
  (even/even, even/odd, odd/even, odd/odd).
- Typed TARGET condition with the same choices, or unfiltered discovery of all
  recorded following results. A double is an equality of two positions; a digit
  anywhere counts each qualifying source draw once, including doubles.
- Source scope **AM / PM / ALL**, with same-day AM→PM forcing AM.
- Relationship **same-date morning→evening** or **next scheduled draw**.
- Optional exact 2D target (match query); blank target means discover all following values.
- Historical window **all available** (default), **one week**, **one month**, **custom dates**.
- Read-only results: counts, detailed draw evidence, following-pair frequencies, full
  record list with filters and pagination/expand controls.
- Reverse-pair shortcut sets target to reverse(source); it is not a separate detector.

Later candidate extensions, **not implemented/approved formulas**:
additional named special groups, consecutive multi-result patterns,
composed AND/OR conditions, weekly patterns, saved queries.

## 2. Strict chronological semantics

- Each draw identity is date + MORNING/EVENING.
- Only a confirmed final 2D draw in 00–99 is eligible. Use the app's
  reconciled `Historic2DDataSource.resolve` result stream, respecting its
  authoritative manual-winner priority, deletion locks and current-day boundary.
- Do not use a future draw relative to the exclusive research cutoff.
- Skip weekends and dates **verified** closed. Unknown missing dates are not holidays.
- SAME_DAY_PM requires an AM source and checks PM of the *same calendar date*.
- NEXT_DRAW uses same-day PM after AM, or next eligible weekday AM after PM.
  Friday PM → Monday AM unless Monday is a verified closed date.
- Never compress an unrecorded open draw and assign a later recorded draw as
  the immediate next outcome. Unknown does not mean nonmatch.
- Retain chronological draw identities and exclude repeated same-date/session source rows.
- A selected historical window restricts both source and target. A source whose
  expected target lies outside the window is **OUT_OF_RANGE**, not a nonmatch.
- A target not yet due is **PENDING**, not a missing history record.

## 3. Query and output model

A source occurrence is a *confirmed draw matching the exact source pair* within
the selected source scope and history range. For each such source:
1. Compute its expected following date and session.
2. If expected following is outside the chosen window, OUT_OF_RANGE.
3. Else if it is at/after the cutoff, PENDING.
4. Else if expected recorded result is absent, MISSING.
5. Else if discovering following values (no exact target), OBSERVED.
6. Else compare exact 2D value: MATCH or OTHER.

**sourceCount** = all eligible source occurrences, including those with unknown
or out-of-range following data.

**observedCount** = cases with a genuinely recorded following result.
For exact-match queries:
`observedCount = matchCount + otherCount`.
`sourceCount = observedCount + missingCount + pendingCount + outsideCount`.

The displayed historical match ratio (if shown) is
`matchCount / observedCount`, **not a forecast probability**.
Discovery mode displays the full histogram of recorded following exact pairs.
Every count can be audited against individual date/session and 2D evidence.

## 4. Presentation contract

- Keep Research a dedicated bottom navigation destination beside Analysis,
  Summary and Rules. Do not re-theme or re-layout the approved 4–2–2 Analysis.
- Research Atlas has its own navy/teal semantic palette (light and dark), with
  readable Burmese/English typography and a compact fixed search action.
- Use guided relationship choices, selectable typed source and target criteria,
  reverse shortcut for exact pairs and separate Research history-range selection.
- Query editor and evidence results are *different states*: completing a query
  brings the answer to the top, with a concise query summary and an Edit action.
  Never put the entire form before the results.
- The parent retains in-session Research draft, selection and result-view state
  when switching to Analysis or Summary. Back from results first returns to
  the editor; it must not leave the application.
- Show the answer first: source count, verified following count, exact-match
  count or distinct following-pair count; then data-coverage context.
- Follow with frequency distribution and per-source chronological evidence.
- Match/other/unavailable/all filters; evidence loaded in visible batches.
- A selected, confirmed historical pair is never substituted with a demo result.
- Unavailable, pending and out-of-range cases remain distinct.
- Both Myanmar and English labels use the app's approved typography system.

## 5. Required tests / safety gates

Unit-test same-day matching, discovery frequency, Friday→Monday, verified
closure skipping, unverified missing-slot noncompression, exact cutoff,
selected-window truncation, duplicate rows, invalid inputs, leading-zero pairs,
position/orientation-sensitive tens/ones parity, digit-in-either-position,
doubles and typed criterion-based matching.

Before acceptance:
- Run Android CI unit tests, lint and debug APK assembly on research branch.
- Verify expected/actual counts manually with real historical draw examples.
- Check Burmese and English line wrapping, selected controls and scroll.
- Test the result at morning/afternoon boundaries and after reopening the app.
- Confirm Rules, Analysis A-01–A-08, Summary, LIVE, ledger and winners unchanged.

**No merge to main / release without successful CI and acceptance.**
