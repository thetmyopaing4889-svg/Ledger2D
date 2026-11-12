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
- Historical window **all available** (default), **last 7 inclusive calendar dates**, **last 30 inclusive calendar dates**, or **custom inclusive dates**. The shortcuts are not unannounced rolling calendar-week/month rules.
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


## 6. Evidence readability refinement (2026-11-12)

- For **two or more distinct following pairs with identical observed counts**, show
  compact four-column number tiles with each pair and its count. Showing
  multiple identical full-length bars suggests a comparison that is not there.
- For **unequal counts**, retain scaled comparison bars, sorted by count and pair.
  One distinct pair also uses the bar presentation. Limit the initial display
  to 12 tiles or 6 bars, with explicit expand-all control.
- Preserve the existing high-legibility dated **Card** evidence view. Add
  an optional **Compact** evidence view for longer studies. Both present
  source date/session/pair, expected target session/pair, and the same
  status. Cross-day target dates remain visible.
- Evidence filters, newest-first ordering and 25-item incremental loading
  apply to either view. Toggling view never changes source results.
- Regression fixtures reconstruct the six screenshot entries for
  `AM 12 → PM 21` and assert 6 source cases, 6 known outcomes,
  0 matches, 6 other outcomes, and six frequencies of 1. This is a
  test of computation against provided visible records—not independent
  verification of the phone's Room database provenance.
- Gate this change on the feature branch's Android CI compilation,
  unit tests, lint, debug and release APK tasks. Keep PR #30 a draft.


## 7. Target-matched frequency versus all-outcome frequency (2026-11-12)

When the user specifies a TARGET condition (exact pair, digit, tens/ones,
double or parity), the Frequency panel has two explicitly selectable scopes:

- **Matching pairs / ကိုက်ညီသောဂဏန်းတွဲ** (default): count only confirmed
  following draw pairs whose evidence status is MATCH. Their frequencies
  must add up to **matchCount**, not observedCount.
- **All following outcomes / နောက်ထွက်သမျှ**: count all recorded eligible
  following draw pairs, including both MATCH and OTHER. These frequencies
  add up to **observedCount** and are the original discovery histogram.

Queries without a target continue to show all observed following outcomes.
A zero-match query displays an explicit no-matches note in the matching
view but retains the All outcomes tab for exploration. Missing, pending
and out-of-range entries contribute to neither histogram.

Switching tabs resets expanded frequency pagination; it never changes
the research query, evidence statuses or summary counts. Both scopes
retain the existing visualization rule: **equal counts → compact tiles;
unequal counts → bars**. Green bars were never removed globally.

New tests verify digit-7→digit-3 matched pairs (including leading zero
and double 77), the all-outcome fallback when zero matches, repeated
matched-pair counts and discovery without a target.


## 8. Independent quality audit (2026-11-12)

- **Android app theme, not system theme:** Research Atlas must select its
  light/dark palette from the active `MaterialTheme.colorScheme`, since the
  Ledger2D `LedgerTheme(darkTheme=…)` application setting can differ from the
  phone's Android appearance. Standalone navy/teal Research identity remains.
- **One historical eligibility rule:** The hero's verified-result count and the
  research engine must both use `TwoDResearchEngine.eligibleHistoryCount` /
  shared `eligibleDraws` logic: exactly two digits, open working date,
  strictly before research cutoff and unique date+session slots. This is
  necessary to keep the visible history count aligned with the engine.
- **Frequency invariants:** For every typed target, histogram sum of
  matching outcomes = `matchCount`; histogram sum of all observed
  following results = `observedCount`. Missing, pending and outside-range
  cases are excluded from both; doubles create one source occurrence.
- **Limits of verification:** Unit tests and GitHub Android CI validate
  deterministic software and assembled APKs, not the independent provenance
  of all 2D values in a user's phone database. Real-device tests must still
  verify the app-theme override, source-to-evidence traceability, target
  filter behavior and historical record completeness.
- **Release gate:** Keep PR #30 as a draft targeting the isolated Analysis
  feature branch. Main remains unmerged until acceptance.


## 9. Research Academy — bilingual in-app guide (2026-11-12)

User explicitly approved assistant-led implementation of an accessible,
interactive Research Guide, without redesigning the accepted Research Atlas UI.

**Entry points:** a help (?) control in the Research editor hero, a discreet
"Guide" link next to Quick Start, and the same help (?) control in the Evidence
hero. These all open the same Research Academy screen. There is no automatic
first-run pop-up and no new bottom navigation tab.

**Lessons (expandable, bilingual Burmese/English):**
1. Get started in four steps: relationship → source → optional target → period.
2. Six condition types: exact pair, any-position digit, tens, ones, double,
   ordered tens/ones parity, with leading-zero and double examples.
3. AM/PM/ALL source scope; same-day AM→PM; next scheduled draw; verified
   closures versus truly unavailable expected results.
4. Target switch, reverse shortcut, inclusive 7-/30-day/custom history windows.
5. Source / known outcome / match / different / missing / pending / outside
   range, and historically observed match-rate denominator.
6. Target-matched versus all-outcome frequency, equal-count tile versus
   unequal-count bar presentation, dated evidence and card/compact toggle.

**Try it:** six immutable study templates (12→21, digit-7→digit-3,
double→discovery, odd/odd→discovery, tens-7→ones-3, odd/odd→even/even)
generate ordinary validated `TwoDResearchQuery` objects and execute on the
same reconciled verified historic stream. They contain **no fake draw outcomes
or pre-filled summary results**. Ready/loaded history is required to run.
Selecting an example leaves the guide and enters Research Evidence directly.

**State, back & reliability:** Opening the guide does not mutate the current
study or form. Both device/system Back and the toolbar Back close Guide first,
returning to the prior editor or result. Closing the Guide footer behaves the
same. Switching tabs and back preserves in-session Guide/Research state.
A one-tap example deliberately replaces only the current study query and resets
its filter/pagination. No changes to Analysis A-01–A-08, Summary, Rules or
persistent 2D historical data.

**Quality gates:** Unit tests cover unique stable example IDs, bilingual
content, valid typed source/target definitions, real query construction,
same-day scope, disabled fake outcomes, and study-state reset. Android CI
must pass on the guide-enabled branch; finally inspect handset Myanmar/English
line wrapping, light/dark guide cards, both Back routes, and one-tap true
history results. Keep PR #30 in draft until device acceptance.
