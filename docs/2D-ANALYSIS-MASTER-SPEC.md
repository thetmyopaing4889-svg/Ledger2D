# Cherry's 2D Ledger — 2D Analysis Master Specification

> **Status:** Eight-method redesign **authorized and implemented on isolated experimental branch** `feature/2d-analysis-timeline-redesign`; CI regression and real-device acceptance still required before merge.  
> **Version:** 0.2 — 2026-11-07  
> **Repository branch at creation:** `experiment/font-typography-lab-build930`  
> **Baseline code reviewed:** Build #963, commit `522a37c8637ad847677931756bcdef916693f343` (CI success).  
> **Purpose:** Transfer the complete *why, what, definitions, examples, open questions and decisions* across conversations and eventually into implementation tests. **This document is the working source of truth; implementation was subsequently authorized explicitly on 2026-11-07 for the isolated redesign branch, not for merging into the baseline without acceptance.**

## Reading guide — important

1. Read **Part I (why redesign)** before interpreting the analysis rules. The existing UI must not be treated as the desired specification.
2. Read **Part II (data and timing)** before implementing any method.
3. Every method in **Part III** has **Confirmed** instructions, illustrative examples, and **Pending** choices. Never silently convert a pending choice into a product rule.
4. In follow-up conversations, append new rules; **do not compress old rules** into a one-line summary or delete the worked examples.
5. User explicitly said: collect all useful checks first, **then** organize them into features, **then** decide first views / UI, **then** implement. Do not prematurely fit methods into the existing navigation labels.
6. User-specified "မူ" (Rules) is reserved for later. **Do not change it** under this redesign.
7. Historical observation, including a frequent pattern or a missing digit, is **not** a guarantee of future outcome. Do not market observed ratios as predictive certainty.

---

# Part I — Project background and why redesign exists

## I.1 Existing app

Cherry's 2D Ledger is an Android ledger app for Myanmar 2D sales agents, customers and dai (agents). It includes transaction entry, customer / agent management, per-session commission and payout snapshots, reports, vouchers and live/historical result handling. The analysis discussed here is **historical 2D result analysis**, not the separate customer-specific financial risk analysis.

The existing historical 2D Analysis has already been built. Around Build #961–#963 it presents five bottom destinations: **Insights, Explorer, Patterns, Missing, မူ (Rules)** with an upper-right period/session filter. Existing analytics include draw counts, 00–99 frequencies, timelines, tens/ones bars, reverse-pair and double displays, and missing-number lists. A source merger reads saved daily results and historical results; current-day analysis must use confirmed final results, not provisional live values.

This project is **not** an initial request to build a screen from scratch. It is a **reconsideration after the user opened each feature on a real phone and shared first-view screenshots on 2026-11-06**.

## I.2 Why the built version was rejected as insufficient

### Fundamental problem: wrong information priorities

The user found that the application could display data but often did not give a meaningful service to a Myanmar 2D user. For example, displaying "10 draws this week; no 2-digit number repeated" as a hero insight is not necessarily useful; a player can already see such a fact, and knowing it alone does not resolve a concrete question. Showing four "top" 2-digit numbers each observed once, visually elevating the first one, can be misleading: it implies significance despite a tie.

Similarly, a grid of 00–99 with mostly 0× entries, or a list of 90 not-yet-seen 2-digit numbers, can consume most of the first screen without communicating the things the target user actually cares about. The issue is **not** that missing numbers or frequency are inherently useless. The issue is **what is inspected, for what time scope, how it is connected to practical Myanmar 2D reasoning, and what is shown first**.

The user corrected the earlier assistant's tendency to dismiss missing/frequency analyses or replace them wholesale. **Missing numbers matter** when framed as (for example) a digit absent in a prior week, whether it has returned this week, and what happened in comparable historical weeks.

### Visual/interaction problem

The slight improvement in gradient styling did not meet the user's intended premium design. Cards were perceived as rectangular outlines on a background with a little shadow rather than clearly elevated, separated, layered surfaces. Some small cards nested in large cards had no meaningful depth. Giant gradient headers and low-value lists used prime first-view space. The user wants **distinct, properly layered/elevated cards**, a compact but readable hierarchy, and MM/EN layouts that fit. Styling alone cannot fix information priority.

Do not claim the old UI is "premium" merely because it uses gradients, border, or small shadow; evaluate actual on-device screenshots.

## I.3 User's real-world analysis frame

Myanmar 2D normally runs Monday–Friday, morning and evening (up to ten draw occasions in a normal week; closures/holidays change this). Many 2D players habitually reason **by current calendar week**: "this week doubles have not appeared", "power has not appeared", "one-digit numbers kept changing", "an even/odd class has appeared repeatedly", etc.

**Central product direction:**

- Focus on the **current target day/session** (မနက်အကြိမ် / ညနေအကြိမ်).
- For many methods, the natural observational unit is the **Monday–Friday week**; take only already-completed, confirmed results before the target result.
- Some checks require comparison with **the preceding week**; other checks require **same weekday and same AM/PM session** in history; still others require **matching ordered within-week patterns**. These are distinct scopes.
- Produce *answers to concrete user checks*, not generic charts just because charts exist.
- Allow appropriate evidence/drill-down; do not imply that history forces future outcomes.
- A separately discussed **user-directed research/testing workspace** may become its own analysis feature. This idea does **not** replace the checks the user is now teaching, and feature grouping is **not decided**.
- "မူ" stays untouched until separately instructed.

## I.4 Agreed workflow before changing source code

1. User describes each practical Myanmar 2D checking method in their own terms and with examples.
2. Assistant restates exact conditions, identifies ambiguous edge cases, and asks only relevant questions. **The user confirms** the rule.
3. Save the method **verbatim in meaning**, with detailed worked examples, matching and non-matching cases, data scope, expected output, and pending decisions.
4. Only after all methods are collected: decide which belong together in a feature and which should be separate.
5. Decide valuable first-view answers, navigation, cards and interactions.
6. Then implement minimally, with automated tests and regression review; preserve existing unrelated app functionality.
7. Keep a decision/change log for future refinements.

No analysis code/UI change is authorized merely by adding or editing this document.

---

# Part II — Shared vocabulary, data, and computation constraints

## II.1 Time / draw session

- A standard week is Monday–Friday, **morning and evening**, max 10 occasions; public holidays may remove some or all occasions.
- The app user prefers **မနက်အကြိမ် / ညနေအကြိမ်** rather than technical "session" on user-facing Burmese screens.
- Morning normal boundary 12:01 and evening 16:30 Myanmar time. Analyze confirmed stored final results; **do not count a provisional/live value** as confirmed.
- For a target occasion that has not happened, do **not** include its own future result in "already happened this week".
- Missing/unavailable records and true scheduled no-draw holidays **must not silently become a 00 result or be mistaken for a negative observation**.
- Where data sufficiency is uncertain, mark the condition as pending/insufficient instead of fabricating an outcome.

## II.2 Single digit vs 2D number

- **တစ်လုံးဂဏန်း (digit):** 0 through 9, regardless of whether used in the tens (ထိပ်) or ones (ပိတ်) place when evaluating an appearance (unless a specific method says otherwise).
- **2D number (နှစ်လုံးတွဲ):** 00 through 99, e.g. 39.
- Example confirmed by user: `14 → 26 → 35` uses the six single digits 1,4,2,6,3,5, **none repeating** among these draws.
- Example: `14 → 45 → 65` shows a **single-digit carry/reappearance between consecutive results**: 4 in 14/45; then 5 in 45/65. It is *not* the same digit repeating in all three results, and not the same 2D pair repeating.
- Patterns based on sequential appearance of digits are **candidate methods/examples**, not yet a separately confirmed, fully specified check in this register.

## II.3 Even/odd classes and special 2D groups

- User explicitly names **စုံစုံ, မမ, စုံမ, မစုံ** as a four-class classification used for 2D draw results.
- User also names **အပူး, ပါဝါ, နက္ခတ်, ညီအကို** as relevant special sets.
- **Do not invent the exact mapping, counts, overlaps or exclusions** of special sets. Inspect existing parser/domain definitions and have user validate them before implementing the analysis engine. Even for the parity labels, verify tens/ones orientation and terminology rather than silently assuming.
- Whether a single result may count in more than one special set is an open rule; avoid summing potentially overlapping category counts as if mutually exclusive.

## II.4 Three different history questions

These must **never** be collapsed into one generic "last N results" query:

1. **A-01: previous-week digit missing / following-week return** (week-to-week).
2. **A-02: target weekday+session history** (e.g. compare *Monday Morning only* to past *Monday Morning* results).
3. **A-03: strict ordered four-class sequence of the current week and historical matching weeks**, followed by looking at the next draw/class.

## II.5 Test/data integrity guardrails

- Match actual draws and their date/session identities; avoid duplicates across daily and imported history.
- Historical sample size and unavailable/incomplete weeks must be visible to developers/tests. Do not assume the source contains a complete week merely because some rows exist.
- For ties in "most" or "least", keep all tied results rather than elevating an arbitrary digit/number.
- Separate descriptive historical occurrence from prediction; a long absence does not by itself raise the chance of appearing next.
- Pending definitions below are **not instructions for implementation**.

---

# Part III — Method register (detailed requirements)

## A-01 — Previous-week missing single digits and their return

**State:** Core intent confirmed. A-01 gained a **high-priority returning-digit partner-frequency check**, explicitly approved on 2026-11-07. **Counting convention confirmed: OPTION A — only the FIRST confirmed return 2D draw in the immediately following week, one observation per historical missing-week episode.** Holiday/incomplete-history handling and lookback horizon remain pending.  
**Scope:** Prior completed Monday–Friday week → current week confirmed results → past historical weeks with same missing digit.

### A-01.1 User's question

"အရင်အပတ်က Mon to Fri ဘာတစ်လုံးဂဏန်းပျောက်ခဲ့သလဲ? အဲ့ဒီပျောက်ဂဏန်း ဒီအပတ် ထွက်ပြီလား? အရင် History မှာ အဲဒီဂဏန်း ပျောက်ခဲ့ဖူးလား၊ နောက်တစ်ပတ် တန်းပြန်ပေါ်ခဲ့ဖူးလား?"

### A-01.2 Definition (confirmed)

A digit 0–9 is **ပျောက်ဂဏန်း** for a week if it **did not appear anywhere** (neither tens nor ones) in that week's observed 2D results. The user's explicit example: if 0 did not appear in **any of the 10 completed draws last week**, say **"0 ပျောက်"**. This is about a **single digit**, not an absent two-digit number such as 00 or 39.

Multiple digits can be absent; the method must inspect each digit, not assume at most one.

### A-01.3 Exact inspection stages (confirmed)

**Stage 1 — Last week**
1. Identify the prior Monday–Friday week (do not bridge two calendar weeks).
2. Read the completed, confirmed 2D results.
3. For each digit 0–9, mark whether it occurs in any draw (either position).
4. List digits that did not occur. If none, the user has no previous-week missing digits (presentation is a later UI decision).
5. Distinguish truly absent digits from results that cannot be verified as recorded (pending detail).

**Stage 2 — Current week**
1. For **each** digit found missing last week, inspect the current week's confirmed results **up to the target session**.
2. Determine if the digit has reappeared.
3. If yes, identify first observed return: weekday/date, morning/evening, actual 2D result, and digit placement (tens or ones).
4. If not yet, report that it **has not appeared yet among the confirmed current-week results**; do not label that as an outcome for the whole unfinished week.

**Stage 3 — Historic comparisons**
1. Find historical completed weeks where **the same digit** was absent for the whole week.
2. For each qualifying week, inspect the **immediately following week**.
3. Record whether the digit appeared in that next week; if so, when it **first returned** (weekday, AM/PM, 2D number).
4. Summarize number of historically missing weeks, number returning the following week, and number absent again **only among weeks with sufficient verified following-week data**.
5. If following-week data is unavailable/incomplete, treat as **unknown**, not "failed to return".

### A-01.3a HIGH-PRIORITY — Partner digit most frequent when a missing digit returns

**USER APPROVED on 2026-11-07:** When a previously missing **single digit** returns after having been absent for a completed week, also inspect historical episodes of that digit missing for a week and returning in the following week to answer: **"ဘယ်ဂဏန်းနဲ့ အများဆုံးတွဲပြီး ပြန်ပေါ်လဲ?"**. Give the most common **companion single digit** prominence in the answer, rather than only presenting missing/return counts.

**Illustrative, NOT real history:**
- Previously missing digit = **0**.
- Suppose five historical episodes have full 2D return outcomes **03, 07, 03, 05, 03**.
- Accompanying digits = **3, 7, 3, 5, 3**, so **3** is the most frequently paired digit in this *illustrative* set, **3/5 observations**.
- The 0 can appear as tens or ones (03 and 30 each pair 0 with 3); preserve original full 2D value, date and occasion as supporting evidence.
- A double such as 00 has companion digit 0, and must not be silently excluded from a count of exact returned 2D values.
- If companion digits tie for highest observed count, show all tied digits with counts; no arbitrary winner.
- This is the frequency of a digit **paired on the 2D return outcomes after a missing week**, NOT global most frequent digit across all historical draws, and NOT a claim about likely next outcomes.

**Confirmed counting rule — OPTION A (2026-11-07):**
1. Identify a completed historical Monday–Friday week in which a particular digit `d` (0–9) was entirely missing.
2. Inspect the **immediately following week** in chronological order (date and AM before PM). Find the **FIRST eligible confirmed 2D result that contains `d`**.
3. Extract the *other* digit of that first return result as `d`'s historical companion; count **ONE observation for that missing-week → following-week return episode**. A return result of 03 for missing 0 contributes companion 3 once; return 30 also contributes companion 3 once. A double 00 contributes companion 0.
4. **Ignore later 2D results containing `d` in the same following week for this companion-frequency ranking**. Do not use every appearance in that week; do not double-count a historical episode.
5. Aggregate first-return companions **across distinct qualifying historical episodes**, rank by observed count, preserve ties, and show eligible episode count and original return dates/sessions/results.
6. If the following week has **no observed return** or has insufficient confirmed source records to verify the first return, **do not contribute a companion observation**; distinguish confirmed non-return from incomplete/unknown as decided under A-01 data completeness policy.

**User's explicit counterexample:** Previously missing digit 0; next week's sequence includes Mon AM=03, Tue PM=07 and Thu AM=05. **ONLY 03 counts** for the partner ranking (companion 3 ×1); **07 and 05 must not enter this ranking**, despite also containing 0. This particular rule is about historical first-return companion frequencies and does not discard other current-week appearance information outside this ranking.

**Presentation priority (CONFIRMED):** Prominently display the top accompanying digit(s), observed count, and the supporting full result(s), alongside the previous-week missing digit and its current-week/historical return status. Exact UI details remain to be designed.

### A-01.4 Worked example — illustrative, NOT actual source data

- Prior week: 10 confirmed results; digit **0** appears in no tens or ones position → **"0 ပျောက်"**.
- Current week: Wed AM confirmed result is **40** and this is the first appearance of 0 → returned Wed AM in the **ones** position.
- History may contain other absent-0 weeks; inspect the immediately subsequent week for the first reappearance and whether it happened at all.

**Negative example:** If prior week's result includes **70** even once, 0 was **not missing**, even if no result had tens digit 0.

### A-01.5 Pending; do not invent

- How to assess a shortened week due to true exchange holidays versus incomplete records, both for the previous week and the history population.
- **RESOLVED (shared history range):** Default **all available verified history**, with optional user-selected one-week/one-month/custom dates. Cross-boundary missing-week→next-week episode eligibility remains pending.
- Whether a historically missing digit should still be compared when its following week is partially recorded, and how prominently sample sizes should be displayed.
- Date/session evidence for restored/imported results when availability is uncertain.
- **Q-39 RESOLVED:** Only the first confirmed 2D return result in the immediately following week counts; one partner observation per historical missing-week episode. Later same-week appearances are ignored for this ranking.

---

## A-02 — Same weekday + same morning/evening: frequency/class comparisons

**State:** Five checks, *exact matching day/session scope*, and **single-digit frequency counting each tens/ones position separately (55 contributes 5 twice)** confirmed by user. Historical horizon and other details pending.  
**Scope:** Historical results with **the same weekday AND morning/evening** as the target session. This is **not** the current week's cumulative tally.

### A-02.1 User's question and explicit correction

For a user looking at **Mon morning**, inspect **historical Mon mornings**. For **Wed evening**, inspect **historical Wed evenings**. Do not take all historical Mon–Fri draws, all mornings, or this week's previous results as substitutes.

### A-02.2 Five checks (confirmed)

**A-02.a — 2D number frequency (00–99)**
- Which complete two-digit number (e.g. **39**) occurred most frequently in this matched historical population?
- Show counts, preserve ties, and provide records supporting a number.

**A-02.b — Single-digit frequency (0–9)**
- Which digit occurred most and which occurred least?
- Distinguish the one-digit answer from a two-digit result.
- **CONFIRMED:** Count **digit positions**, not unique digits per draw. A result `55` contributes **two occurrences of single digit 5**; `03` contributes **one 0 and one 3**. This combined 0–9 frequency policy matches A-04. Do NOT confuse it with A-05's distinct-digit new/old tracking (first 11 = one *new* digit).

**A-02.c — Four odd/even classes**
- Compare historical frequencies of **စုံစုံ, မမ, စုံမ, မစုံ**.
- Identify the most observed class; retain ties.
- Verify exact tens/ones order against user/business definitions.

**A-02.d — Special-number groups**
- Compare appearance counts of **အပူး, ပါဝါ, နက္ခတ်, ညီအကို** in the matched historical sample.
- The user has NOT yet given authoritative mapping for every group in this discussion. Validate those sets before coding.
- Handle potential overlapping group membership explicitly.

**A-02.e — Tens and ones maxima**
- Compute most-observed **ထိပ် (tens)** digit and most-observed **ပိတ် (ones)** digit **separately**.
- Example 39: tens=3, ones=9.
- Retain all ties. Do not combine tens and ones to create one misleading leaderboard.

### A-02.3 Worked scope examples

| Current target | Include from history | Exclude |
|---|---|---|
| Mon AM | past Monday morning results | Monday evening, other weekdays |
| Mon PM | past Monday evening results | Monday morning, other weekdays |
| Wed AM | past Wednesday morning results | Wednesday evening, other weekdays |
| Fri PM | past Friday evening results | Friday morning, other weekdays |

**Illustrative only:** In the selected Mon AM history, if number 39 occurs three times and 45 twice, 39 outranks 45 (unless another number ties or exceeds it). Actual statistics must come from the data, not the example.

### A-02.4 Pending; do not invent

- **RESOLVED (shared history range):** Default **all available verified history**, with optional one-week/one-month/custom date selection; exact shortcut semantics and sample eligibility at range boundaries remain pending.
- Should the target draw be excluded when already completed? The user framed the check for the current target session; no look-ahead must be introduced.
- **RESOLVED:** 55 counts **twice** for single digit 5 (tens + ones positions).
- Does least frequent include digits at zero occurrences?
- Exact boundaries of all special groups; treatment of overlap.
- Handling an empty/small matched sample and available-data vs full-schedule distinction.

---

## A-03 — Strict chronological weekly even/odd-pattern matching

**State:** Confirmed strict weekday+AM/PM slot identity, exact four-class ordered pattern matching, minimum four finalized draws, weekly boundary, no-match behavior, and **exclusion of an entire week containing a verified closed day from A-03 pattern matching** (user explicit correction 2026-11-07). Unknown-result gaps cannot be silently compressed. Cross-period completeness and other boundary details still require tests.  
**Scope:** Current Monday–Friday week's **confirmed chronological four-class sequence** BEFORE the target session, matched to historical within-week sequences, then inspect each match's **next result**.

### A-03.1 User's question

At the current target session, take already finished draws this week, convert each to **စုံစုံ / မမ / စုံမ / မစုံ**, and check whether history contains a week whose **ordered sequence is exactly the same**. If yes, what class came **immediately after** that sequence in the historical week? If no history match, **do not display that check/result**.

### A-03.2 Conditions confirmed by the user

1. **Only within a Monday–Friday week**. Never splice Fri PM of one week into Mon AM of the next.
2. **At least four confirmed draws must already exist** before the target session. Earlier than four, this check is not eligible.
3. **Order is mandatory:** equal category totals in a different order are **NOT** a match.
4. The observed sequence grows as new draws complete this week. It is **not fixed at four**; e.g., Wed AM=4 prior draws, Wed PM=5 prior draws, continuing through Fri PM=9 prior draws in an ordinary fully open week.
5. **LATEST USER CORRECTION (2026-11-07):** A-03 is **strict weekday and AM/PM slot-aligned pattern matching**; a week containing a **verified closed day is NOT included as an A-03 historical matching candidate**. Do **not** skip a closed date then shift Wednesday's parity class into Tuesday's missing slot or compare sequences with different day/session identities. When the current week itself includes such a verified closed day, do not claim an A-03 exact-week match under this rule. Continue analyzing available results in the *other* methods under the shared closure/missing-result policy. In a normal eligible open week A-03 still requires at least four confirmed preceding draws.
6. For matching historical weeks, inspect the **next actual result/class following the matching prefix**; show it only when that follow-up result is available and valid.
7. If **no matching historic sequence**, user explicitly says **do not show this check's result**. Do not manufacture a fallback forecast.

### A-03.3 Ordinary open-week progression (confirmed example)

| Target session | Confirmed preceding draws this week | Eligible? |
|---|---:|---|
| Mon AM | 0 | No |
| Mon PM | 1 | No |
| Tue AM | 2 | No |
| Tue PM | 3 | No |
| **Wed AM** | **4** | **Yes** |
| Wed PM | 5 | Yes |
| Thu AM | 6 | Yes |
| Thu PM | 7 | Yes |
| Fri AM | 8 | Yes |
| Fri PM | 9 | Yes |

These values apply to a **normal week without verified closure dates**. By the latest A-03-specific correction, a week containing a verified closure is **excluded from strict weekly pattern matching**, not compressed or shifted to preserve a four-result prefix. Other analysis methods can still inspect that week's available results.

### A-03.4 Exact-order example

Current week, four chronological completed classes:

1. စုံစုံ
2. မမ
3. စုံမ
4. မစုံ

**Matching historical candidate:**
1. စုံစုံ → 2. မမ → 3. စုံမ → 4. မစုံ — **MATCH**.

**Non-matching historical candidate:**
1. မမ → 2. စုံစုံ → 3. မစုံ → 4. စုံမ — **NO MATCH**, despite each class occurring once.

**Another explicit user example:** if Mon AM, Mon PM, Tue AM, Tue PM were **စုံစုံ four consecutive times**, find historical within-week occurrences of that same **four-consecutive** ordered pattern and inspect their following session's category.

### A-03.5 Subsequent historical outcome — illustrative, NOT actual historical data

Suppose the current four-class prefix matches three historical weeks:

- History week A: next draw class **စုံစုံ**
- History week B: next draw class **မမ**
- History week C: next draw class **စုံစုံ**

Then, among **three matched weeks with known next draws**, the descriptive breakdown is **စုံစုံ=2, မမ=1**, with dates and result details available for inspection. It is NOT a claimed 2/3 chance of future စုံစုံ.

If a matched historical week lacks a valid next draw, do not invent an outcome or count it as the opposite outcome.

### A-03.6 Strict day/session/pattern identity and closed-week exclusion — USER CONFIRMED 2026-11-07

The user explicitly corrected the prior suggestion of comparing condensed real-draw parity sequences: **"match ဖြစ်ဖို့ဆို နေ့ကော result ကော ထပ်တူကျမှ ရမှာလေ ပိတ်ရက်ရှိတဲ့ အပတ်ဆို မပါတော့ဘူးပေါ့ကွ ဒါက pattern ကို စစ်တဲ့အပိုင်း"**.

**Confirmed matching dimensions:**
1. The **same weekday** (Monday vs Monday, Tuesday vs Tuesday...) must correspond.
2. The **same draw session** (morning vs morning, evening vs evening) must correspond.
3. The result's **same four-class parity pattern** (စုံစုံ / မမ / စုံမ / မစုံ) must match in the same slot and chronological sequence. Because this is the *pattern check*, exact full 2D values need not be identical as long as their corresponding parity class matches. Exact 2D result matching is separately required by A-08.
4. A **verified closed-day week is excluded from A-03's week-to-week pattern-matching population**. Do not move/renumber/shift later draw results to fill a holiday slot, and do not count a holiday as missing evidence. Other methods should continue to analyze available non-closed dates in that week.
5. An **unknown result on a potentially open day is NOT a verified holiday**. Continue preserving and displaying available results, but do not substitute another slot to produce a false exact pattern match.

**Illustrative** current open week: Mon AM စုံစုံ, Mon PM မမ, Tue AM စုံမ, Tue PM မစုံ. Historical open week A has **the same four pattern classes at those exact four weekday/session slots** → MATCH; historical week B has Mon AM စုံစုံ, Mon PM မမ but Tuesday is a verified closure → **excluded from A-03** (no date shifting). Historical open week C with identical class *totals* but a different weekday/session mapping → NO MATCH.

**Previous A-03 holiday example superseded:** The earlier suggestion to skip a holiday and resume an A-03 exact-prefix match after collecting four real draws was too loose for the newly confirmed **strict slot correspondence** rule. Preserve the other methods' useful partial-history evaluation. Do not infer pattern matches by compacting confirmed closure gaps.

**Still pending:** Historic matching **week-start prefix** versus searching a matching contiguous segment later within a full week (Q-09), and exact policy for unverified missing data in an otherwise open week (Q-08). The **history lookback** remains all available verified data by default with user week/month/custom filters as already approved.

### A-03.7 Presentation intent (not yet designed)

The user wants **a practical analysis of the current target session**, based on matching *real* weekly sequences, not an attractive but unrelated historical chart. If no matches, the requested behavior is **omit the result**; broader UI empty-state choices are not approved yet.

---

## A-04 — Previous week's completed draw distribution and remaining digits

**State:** Core categories, exact same-week **ပြန်ကျ**, previous-week **ကျန်ဂဏန်း = still-unseen single digits in 0–9**, and **A-04 0–9 frequency = count each tens/ones position (55 yields two 5s)** are **confirmed**. **ပြန်ကျ repeat-event count is confirmed for 2, 3, 4+ appearances** (repeat count = complete-pair occurrences within this week minus its first appearance); some data-completeness/display details remain pending. **Do not code yet.**  
**Scope:** The **entire previous Monday–Friday week**, including its completed AM and PM draws that have verified final results. **Do not confuse with A-02**, which filters to *the same weekday and same session* as the current target. **Do not confuse with A-01**, which continues from previous-week missing single digits into *current-week reappearance* and historical next-week behavior.

### A-04.1 User's actual request (captured from conversation)

> "ပြီးခဲ့အပတ်ရဲ့ ဂဏန်းထွက်ရှိမှုအခြေအနေ 0 to 9 အကြိမ်ရေ, စုံစုံ မမ စုံမ မစုံ အကြိမ်ရေ, ပါဝါ ညီကို အပူး နက္ခတ်.ပြန်ကျ....ထွက်ရှိမှု, ကျန်ဂဏန်း"

The user explicitly **confirmed ပြန်ကျ** as **the exact same two-digit number appearing at least twice within one Monday–Friday week, regardless of which days or AM/PM sessions**: e.g. Monday **01**, Tuesday **01** or Monday AM **01**, Monday PM **01**. It does **not** require consecutive draw occasions. **"ကျန်ဂဏန်း"** has now been explicitly clarified as **single digits 0–9 not present in either position in any of the previous week’s confirmed draw results**. "ညီကို" may be the user's shorter term for an existing "ညီအကို" group; verify the synonym before coding.

### A-04.2 Components explicitly requested by user

1. **0–9 digit appearance counts for the previous week.** Examine the week's 2D results and report occurrence statistics for individual digits 0 through 9. This concerns single digits, not the 00–99 2D pair frequency. **CONFIRMED:** Count each digit **position**; `55` contributes **2** to digit 5's 0–9 occurrence frequency (one for tens, one for ones). `03` contributes one 0 and one 3. This combined 0–9 frequency count is different from A-05's distinct-digit new/old and remaining-digit sets.
2. **Four even/odd class counts for the previous week:** စုံစုံ, မမ, စုံမ, မစုံ — how often each category appeared in the week's draws. Validate the exact classification orientation and rules before implementing.
3. **Special number-group appearances for the previous week:** ပါဝါ, ညီကို/ညီအကို, အပူး, နက္ခတ် and **ပြန်ကျ**. Count/report how each appeared. **ပြန်ကျ (confirmed core meaning):** repeat occurrence of an **identical 2D pair**, as with **01 on Monday, then 01 again on Tuesday**. This is different from a single 0 or 1 appearing in another pair; it concerns identical complete 2D values. The two occurrences need not be consecutive in the draw sequence and can take place on the same day or different days. A reversed pair like 01 and 10 is not identical, so it is not an exact-number recurrence. The exact membership definitions for the other named special groups remain pending; inspect existing verified app definitions and confirm with user. Do not assume the special groups are mutually exclusive.
4. **ကျန်ဂဏန်း (remaining numbers) — CONFIRMED 2026-11-07:** For the entire preceding Monday–Friday week, collect the **unique digits 0–9** that occurred in *either the tens or ones position* in its confirmed 2D draw results. Return the **complement within 0–9** (the single digits that never appeared at all during that previous week). **Do not calculate the complement of 00–99**. An identical-digit result such as `44` means digit 4 appeared, so it is NOT a remaining digit. If all ten single digits appeared, the remaining set is empty. For an incomplete/uncertain week, describe coverage clearly; do not claim absence from missing records.

   **Illustrative:** Previous week's observed single digits are `0, 1, 2, 3, 5, 7, 9`, so **ကျန်ဂဏန်း = 4, 6, 8**. This is the same *single-digit unseen* concept as A-05, but **A-04 uses the finished previous week**, whereas **A-05 tracks the current week dynamically after the first verified draw**. This is not the same as A-06's 00–99 pair absence ranking.

### A-04.2a Worked example of ပြန်ကျ — user supplied

- **Monday draw: 01**
- **Tuesday draw: 01**  
  The exact same **01** has appeared again in the same reporting week; this qualifies as **"ပြန်ကျ"**.
- This is a **two-digit identity** check. A recurrence of digit 0 or 1 by itself is **not** sufficient to claim that the full 01 result repeated.
- **User-confirmed: same day counts.** Monday AM **01** → Monday PM **01** = **ပြန်ကျ**.
- **User-confirmed: any day/occasion within the same week counts.** Monday **01** → Thursday **01**, with other intervening results, also qualifies.
- **User-confirmed: no cross-week merging.** Previous-week summary evaluates that single Monday–Friday week; repeating the number in a different week is not a recurrence *within that weekly summary*.
- **Logical exclusion of non-identical pairs:** **01** followed by **10** is not the same exact two-digit number and is not "ပြန်ကျ" under this definition.
- **USER CONFIRMED (2026-11-07):** For a given exact two-digit number `n` in one week, **ပြန်ကျ count = max(0, occurrencesOf(n) - 1)**. Its **first** appearance is **NOT** counted as ပြန်ကျ; each additional appearance is a separate repeat event. For **01** observed Mon AM, Wed PM, and Fri AM, report **"01 ပြန်ကျ ၂ ကြိမ်"**, not three. Example counts: one occurrence→0; two→1; three→2; four→3. This rule is identical for A-04's previous week and A-07's current week and is **independent of day/AM/PM adjacency**. Reset when the week changes, and do not count 01→10 as a repeat. The UI choice of additionally showing original total appearances remains separate from the confirmed repeat count.

**Negative distinction (logical, not a separate agreed formula):** Monday **01**, Tuesday **02** share the single digit 0 but **not** the identical two-digit number 01; this is not the demonstrated ပြန်ကျ case.

### A-04.3 Important distinction in business purpose

- **A-04:** "What exactly occurred / was absent during the completed **previous week**?" — a standalone structured, useful previous-week state review.
- **A-01:** "What **single digit was missing last week**; did it return **this week**; and how did it behave after historically missing weeks?" — an inter-week follow-up/history comparison.
- **A-02:** "Among historical draws matching the **current target's weekday AND morning/evening**, what numbers/digits/classes/groups occurred most?" — a different history population.
- **A-03:** "Does the **chronological four-class pattern** of this week match an ordered historical week prefix, and what came after?" — a strict pattern search.

Never combine A-04 totals with A-02 same-session counts. **The user has now explicitly confirmed** that A-04 "remaining" means previously unseen **single digits 0–9** for the completed previous week.

### A-04.4 Data safety and suggested verification cases (NOT yet approved formulas)

- Explicitly identify the Monday–Friday date range and how many *verified final draws* exist in that range, without assuming 10 draws when holidays occur.
- Distinguish a real closure from an unknown/missing source record. Incomplete history cannot prove a digit was absent.
- Preserve ties; avoid visually inflating one of equally frequent categories.
- Example for scope only: if last week's verified results include 14, 26, 35, all three results belong to **last week's** data regardless of which weekdays produced them. **No frequency or group classification formula is implied by this example.**
- Displaying what happened last week does not establish what will happen this week.

### A-04.5 Specific open questions for user

- **Q-13 RESOLVED (counting):** **ပြန်ကျ = every occurrence after the first appearance of the exact same 2D number within one week**; 01 occurring 3 times → ပြန်ကျ 2 times. Count per-number repeat events; additional visual reporting of total original occurrences can be decided during UI design.
- **Q-14 RESOLVED:** **"ကျန်ဂဏန်း" = digits 0–9 not appearing anywhere (tens or ones) in last week’s confirmed results.** Not remaining 00–99 pairs.
- **Q-15 PARTLY RESOLVED:** For the combined 0–9 frequency **`55` counts twice** (each digit position separately). Whether to additionally show tens and ones in separate subviews is a pending presentation choice; A-02.e already separately requires tens and ones maxima.
- **Q-16:** Confirm definitions/overlap of the four parity classes and five named special groups, including whether **"ညီကို"** equals **"ညီအကို"**.
- **Q-17:** Which incomplete/holiday weeks are eligible for last-week review, and how should missing records be represented?
- **Q-18:** Whether the default view should show all group counts, only groups that appeared, and where/when to show drill-down — **UI not decided yet**.

**Feature assignment:** undecided. **Code change:** none. **Next action:** clarify remaining data completeness and optional visual presentation details; `ပြန်ကျ` repeat-event count is now confirmed. Do not implement unconfirmed rules.

---

## A-05 — Current-week single-digit flow: new and previously seen digits

**State:** User confirmed the initial draw trigger, the meaning of unobserved remaining digits (ကျန်ဂဏန်း) **for A-05**, the original four-draw example, and special single-digit handling for doubles such as 11. Other presentation/history/edge-case policies remain **pending**. Do not implement or assign to a UI feature until all essential rules are settled.  
**Scope:** The current Monday–Friday week's **chronologically ordered confirmed 2D results**, considering only earlier results in the **same week** when deciding whether a digit is new/old. This is a *within-week sequential flow*, not A-04's previous-week review and not A-03's historical four-class matching.

### A-05.1 User's exact intent

User described **"ဂဏန်းစီးဆင်းမှု အခြေအနေ — အသစ်+အသစ် လား အသစ်+အဟောင်းလား"** and provided:

> "mon- 12 03 tue 02 35 ဆိုရင် mon အသစ်+အသစ် နဲ့ အသစ်+အသစ် တစ်နေကုန် ဂဏန်းအသစ်များထွက်သည် tue အဟောင်း+အဟောင်း နဲ့ အဟောင်း+အသစ် မနက်ပိုင်းတွင် အဟောင်းပြန်ထွက်ပြီး ညနေပိုင်းတွင် အဟောင်းတစ်လုံး အသစ်တစ်လုံး ထွက်သွားသည်"

This method evaluates whether **the individual digits in each 2D result** have appeared in earlier completed results of the ongoing week. It does **not** require the whole 2D pair to have appeared before (that is A-04 ပြန်ကျ). "New" is relative to the previously observed digits **in the week**, not just the preceding draw or day.

### A-05.2 Worked example — USER-SUPPLIED, preserve exactly

| Occasion | Confirmed 2D result | Previously observed digits in week, before this result | First digit | Second digit | New/old flow classification |
|---|---|---|---|---|---|
| Monday AM | **12** | none | 1 **အသစ်** | 2 **အသစ်** | **အသစ် + အသစ်** |
| Monday PM | **03** | 1, 2 | 0 **အသစ်** | 3 **အသစ်** | **အသစ် + အသစ်** |
| Tuesday AM | **02** | 0, 1, 2, 3 | 0 **အဟောင်း** | 2 **အဟောင်း** | **အဟောင်း + အဟောင်း** |
| Tuesday PM | **35** | 0, 1, 2, 3 | 3 **အဟောင်း** | 5 **အသစ်** | **အဟောင်း + အသစ်** |

Narrative interpretation:
- **Monday:** Both morning and evening drew **two never-before-seen digits** in that week. Across the day, all four digit appearances introduced new digits.
- **Tuesday morning:** Both 0 and 2 had already appeared on Monday; hence **အဟောင်း+အဟောင်း**, even though complete 2D number **02** had not appeared before.
- **Tuesday evening:** 3 had appeared Monday PM in 03, whereas 5 was new; hence **အဟောင်း+အသစ်**.
- After Tuesday PM, the **observed unique single digits** are **0, 1, 2, 3, 5**; those **not yet observed** among 0–9 are **4, 6, 7, 8, 9**. This is background data for the new/old classification, not a predictive statement.

### A-05.2a Start condition and remaining single digits — USER CONFIRMED

**Eligibility / activation:** This checking method does **NOT** run before the first confirmed 2D draw of the current week. It begins **immediately after the first result becomes confirmed**, even if only one morning result is available. A Monday morning example does not imply the session must literally be Monday when a holiday closes the market; the trigger is the **first real completed draw within the week**.

**ကျန်ဂဏန်း (for this method):** The single-digit values among **0–9 that have never appeared in any confirmed 2D result so far in the CURRENT week** (in either tens or ones). After each new confirmed draw, remove newly seen digits from the remaining set. Already seen digits must not return to the remaining list just because absent in a later draw.

**Exact user examples:**

1. **Before Monday morning's draw:** This A-05 check is **inactive** — not an empty "all 10 remaining" analysis screen.
2. **Monday AM result = 12:** 1 and 2 are both newly seen → **အသစ် + အသစ်**. The user explicitly gives remaining digits as **`34567890`** (set {0,3,4,5,6,7,8,9}; display order may be finalized later).
3. **Alternatively, first confirmed result = 11 (အပူး):** digit **1 appears as ONE distinct single digit**. The classification is **"1 အသစ်"**, not a fabricated "two different new digits" or two counted appearances. The user explicitly gives remaining digits as **`234567890`** (set {0,2,3,4,5,6,7,8,9}).
4. Following the four-draw example 12 → 03 → 02 → 35, remaining digits are {4,6,7,8,9}; 0,1,2,3,5 have appeared during the week.

**Critical separation:** The confirmed meaning of **ကျန်ဂဏန်း** here is **A-05's current-week remaining single digits**. Do **NOT** silently assume that the same term in **A-04 previous-week review** is fully defined; ask whether the intended scopes/units are identical.

### A-05.3 Algorithm interpretation (core rules user confirmed; remaining choices pending)

1. Start a new single-digit seen-set at the **beginning of each Monday–Friday week**. The earlier week must not mark digits as old in a new week. **Do not activate A-05 until at least one verified current-week result has been saved**.
2. In ascending chronological order, consider each **verified finalized** result that already occurred within the same week.
3. For the currently inspected result, compare its tens and ones digits with **the seen-set from *prior* results**, not with only the immediately preceding result.
4. Label a digit **အသစ်** when absent from that prior seen-set; label **အဟောင်း** when present.
5. For results containing **two distinct digits**, combine the labels in the result's order. The user specifically demonstrated **အသစ်+အသစ်**, **အဟောင်း+အဟောင်း**, and **အဟောင်း+အသစ်**; handling **အသစ်+အဟောင်း** in display summaries remains pending. For **doubles such as 11**, classify and count the **one distinct digit only**: if previously unseen, user explicitly calls it **အသစ်** (singular). Do not count a first 11 as two new single-digit values.
6. After inspecting the result, add each **distinct digit value** present to the seen-set (so 11 adds **only digit 1 once**). This permits a new digit in one result to be old in a later result, morning or evening and across days. Recompute **ကျန်ဂဏန်း = {0–9} minus seen-set** immediately after each confirmed result.
7. Produce a readable **draw-by-draw and day-by-day flow explanation**, grounded in the actual digits and their appearance history (e.g. "Monday both sessions contained two new digits"; "Tuesday morning both old, evening one old and one new"). **Also show the remaining digit set after each confirmed result, or at minimum the current remaining set**; exact placement/visualization will be decided later.
8. Do not infer this week's unseen digit is more likely to appear in the next result.

### A-05.4 Negative and contrast cases

- **Tuesday 02** is **အဟောင်း+အဟောင်း** even if full 2D pair `02` never appeared earlier; the single digits **0 and 2** did.
- A digit that was seen on **Monday**, absent on some later sessions, and then appears again on **Friday** remains **အဟောင်း** (prior appearances in the *entire ongoing week*, not only in adjacent draws).
- A digit seen **only in last week's** results must **not** automatically count as old for the beginning of this week; weekly status resets.
- **Confirmed double handling:** If the week's first result is **11**, count **digit 1 once** and classify **1 အသစ်** (not as two separate new digits); remaining set excludes 1 and includes all other digits. If an already-seen digit appears later in a double, the precise wording/presentation can be validated before UI implementation.

### A-05.5 Pending points requiring explicit user guidance

- **Q-19 (resolved for first appearance):** User confirmed first **11** = single digit **1 အသစ်**, count digit 1 **once**, not အသစ်+အသစ်. Remaining digits **234567890**. **Pending minor wording:** if 1 was previously seen, use a one-digit old label for 11 (rather than two duplicated labels)? Confirm if needed at UI design.
- **Q-20:** Should **အသစ်+အဟောင်း** and **အဟောင်း+အသစ်** be separate classes depending on tens/ones position, or one combined "one new, one old" class? User's precise order in example is preserved, but whether to merge categories in summary charts is undecided.
- **Q-21:** Is A-05 intended only for explanation of the current week's observed sequence, or also to find similar new/old flow patterns in historical weeks? No history matching was requested for A-05 yet; don't add it on assumption.
- **Q-22:** User **confirmed that ကျန်ဂဏန်း must also be shown** for A-05, as unobserved 0–9 digits in the current week. Exact first-view hierarchy remains open: draw flow, day explanation, remaining-digit placement, counts, latest streak etc. Decide with user after all checks.
- **Q-23:** How should actual holiday closures and historical-record gaps be displayed? Do not assume missing draws are completed; do not interpolate a flow.
  
**Feature assignment:** undecided. **Code change:** none. **Next action:** continue collecting user's remaining methods; do not guess classification formulas or UI layout.

---

## A-06 — Longest-currently-absent 2D pairs and single digits, by morning/evening/both

**State:** The six requested output categories and **absence counted in relevant draw occasions**, not elapsed calendar days, are **confirmed by the user**. Morning-only, evening-only and combined results each maintain independent absence tracking. History range now **CONFIRMED** (all available verified history by default, optional one-week/one-month/custom window). Never-seen candidates, record gaps, truncated streaks and layout remain open; do NOT invent them in code.  
**Scope:** Historical confirmed 2D draw records, using three **separate observation streams**: (1) morning-only, (2) evening-only, (3) combined morning+evening chronological results. This is distinct from A-01 previous-week missing digits and A-04 previous-week category summary.

### A-06.1 User's explicit instruction

> "စစ်ဆေးနည်း၆ မနက်ပိုင်းသီးသန့် ပျောက်တာအကြာဆုံး ဂဏန်းတွဲ (ဥပမာ ၅၆) နဲ့ ပျောက်တာအကြာဆုံးဂဏန်း(ဥပမာ 0) ညနေသီးသန့် ပျောက်ဂဏန်းတွဲနဲ့ ဂဏန်း ပြီးတော့ နှစ်ကြိမ်လုံး ပျောက်ဂဏန်းတွဲနဲ့ ဂဏန်း"

Interpret this as **three independent missing-duration examinations**, **each** covering both complete 2D values 00–99 and single digits 0–9.

### A-06.1a Follow-up clarification — USER CONFIRMED

> "တစ်ရက်ကို နှစ်ကြိမ်ထွက်တယ်လေကွာ 56 က မင်း ဥပမာအတိုင်း မနက်မှာ မထွက်တာ ၁၀၀ကြိမ် ဖြစ်ရင် မနက်မထွက်တဲ့ ပျောက်စာရင်းဝင်မှာ ဖြစ်ပြီး ညနေမှာ ထွက်ရင်တော့ ညနေပျောက်စာရင်း မဝင်ဘူးပေါ့ကွာ မနက်ည အတွက်ကျတော့လဲ ညနေထွက်ထားတော့ ပျောက်စာရင်း မဝင်ဘူးပေါ့"

**Exact meaning:** A full 2D pair such as 56 can be **100 morning results absent** and be eligible for the morning-only missing list, while having **appeared in the evening** and therefore **not being currently absent in the evening-only or combined list immediately after that evening result**. These three assessments are independent; an evening hit must never erase the morning-only absence streak.

**The measure is counts of relevant completed result occasions**, not the number of calendar days:
- Morning-only: count **completed morning draws since last morning appearance** of the number/digit.
- Evening-only: count **completed evening draws since last evening appearance**.
- Both combined: count **completed morning and evening draws in chronological order since last appearance in either**.
- A hit in the selected stream resets its current consecutive absence count in that stream. A hit outside the selected stream does **not** reset its absence count.
- The example excludes the value from the PM and combined **currently-absent list at the time of that PM hit**; it does **not** mean that a value can never appear in those lists at future dates after additional missed draws.

For the single-digit version, appearance means **any full 2D result containing that digit in either position**, as distinguished from the complete-pair check.

The user confirmed the meaning clearly and does **not** want another speculative question asking whether absence is measured in days versus draws. The particular history depth/never-seen handling and final layout can be handled later.

### A-06.2 Exact six output questions — USER SPECIFIED

| Stream / filter | Full 2D-number question | Single-digit question |
|---|---|---|
| **Morning only (မနက်သီးသန့်)** | Among 00–99 pairs, which complete value has been absent the longest **from morning results**? Example candidate: **56**. | Among 0–9 digits, which digit has been absent the longest **from morning results**? Example candidate: **0**. |
| **Evening only (ညနေသီးသန့်)** | Among 00–99 pairs, which complete value has been absent the longest **from evening results**? | Among 0–9 digits, which digit has been absent the longest **from evening results**? |
| **Both combined (မနက်+ညနေ)** | Among 00–99 pairs, which complete value has been absent the longest when **both morning and evening results** are considered? | Among 0–9 digits, which digit has been absent the longest when **both morning and evening results** are considered? |

**Do not accidentally reuse morning-only ranking for evening-only or both.** The three sets of draw occasions and their "last seen" points differ.

### A-06.3 Necessary distinctions

- **ဂဏန်းတွဲ** refers to the **exact two-digit result**, e.g., **56**. Seeing 65 is **not** seeing 56 (unless the user introduces a new reversal convention).
- **ဂဏန်း** refers to a **single 0–9 digit**; seeing digit 0 within 20 or 03 normally constitutes an appearance for a one-digit check, whether tens or ones. The user has explicitly distinguished digits from pairs in prior methods.
- **"ပျောက်တာအကြာဆုံး"** here means the **largest ongoing consecutive count of completed relevant draw occasions without that digit/pair**, not the smallest lifetime frequency nor elapsed calendar days. A number can have appeared frequently historically but still currently be missing across many recent relevant draws.
- Morning-only results should not reset morning-only absence due to an evening appearance; evening-only similarly should not be reset by a morning appearance. A **combined** stream can be reset by an appearance in either.
- **Not a prediction**: longest absence alone does not establish a higher future chance.

### A-06.4 Illustrative edge scenarios (not factual history)

**Example 1 — USER-GIVEN morning vs evening distinction:** Suppose **56** has not appeared in the last **100 confirmed morning draws**, so it is in the **morning-only missing list** (100 missed mornings). **56 then appears in an evening result**. Immediately after that appearance it is **not in the evening-only current-absence list** and **not in the combined current-absence list**, because that evening was a hit. The morning-only 100-morning absence remains intact. **Do not merge the streams or reset morning absence on an evening hit.**

**Example 2 — pair vs digit:** If 56 has not appeared recently but 59 has, digit 5 has appeared while complete pair 56 has not. They must have separate absence calculations.

**Example 3 — ties:** If several candidate values have equally long absence by the approved metric, the method must not arbitrarily highlight only one without revealing the tie.

### A-06.5 Required data/metric decisions (NOT confirmed yet)

1. **Q-24 — RESOLVED by user:** Missing duration is **consecutive relevant completed result counts** (morning-only mornings, evening-only evenings, combined both), **not calendar days**. Example: 56 missing in 100 mornings but appearing that evening stays missing in AM-only, not currently missing in PM/combined immediately after the hit.
2. **Q-25 — Lookback boundary:** Should this be calculated over **all historical records available**, a specified number of months, or a user-selectable date range? What if a digit/pair has **never** been seen in available history?
3. **Q-26 — Starting point / target session:** For the current target session, does analysis use only **previously completed results** (recommended for avoiding look-ahead) and when should a latest-result update refresh the ranking? Need explicit target timing.
4. **Q-27 — Data sufficiency:** What if the source contains missing records, true holidays or import gaps? Do not count an unavailable draw as a verified nonappearance; preserve any inability to know the true absence length.
5. **Q-28 — Desired user answer:** Should the result expose only the single longest candidate, **all ties**, a top N shortlist, last-seen date/time and the exact number of intervening relevant draws, or a full sortable table? UI grouping remains undecided.
6. **Q-29 — Intended scope:** The user's example clearly concerns **absence still ongoing as of the latest confirmed result** for each stream, rather than a longest already-ended streak in old history. Treat current ongoing absence as the primary meaning; do not add a separate all-time historical streak leaderboard without a new request.

### A-06.6 Relationship to other approved methods

- **A-01:** Detect **digits absent for an entire previous calendar week** and whether they returned next week; week-to-week History comparison.
- **A-04:** Report the previous week's frequencies and groups, including its remaining-number definition (still pending).
- **A-05:** Track **new/old digits and remaining 0–9 digits within the CURRENT week**, activated after the first draw of that week.
- **A-06:** Evaluate **ongoing absence measured in relevant completed-draw counts** for both full 2D pairs and single digits over **morning-only, evening-only, both** observation streams. Historical depth defaults to **all available verified history**, but user can select a shorter range; disclose when this truncates any current-absence measurement.

**Feature assignment:** undecided; do not add six separate screens simply because there are six questions.  
**Code status:** not implemented or approved.  
**Next action:** Continue receiving the user's analysis methods. The absence **unit and independent AM/PM/combined reset behavior are settled**. Remaining edge choices can be addressed during the later implementation specification stage without re-asking the already answered days-versus-draws question.

---

## A-07 — Current-week occurrence status of special 2D groups

**State:** User confirmed **current-week scope**, **five groups**, **start after first finalized weekly result**, and **ပြန်ကျ repeat-event counting** (first exact 2D occurrence uncounted; each subsequent one counts, so 01 occurring three times gives two repeat events). The presentation for other categories and data-quality cases remains pending. **Documentation only; no code/UI feature assignment yet.**

### A-07.1 User's explicit instruction

> "စစ်ဆေးနည်း ၇ လက်ရှိအပတ်ရဲ့ အပူး ညီကို ပါဝါ နက္ခတ် ပြန်ကျ...ထွက်ရှိမှု အခြေအနေ (အခု အပတ်ရဲ့ result တစ်ကြိမ် ထွက်ပြီးမှ စတင်တွက်မယ်)"

**Purpose:** Report the **actual occurrence status so far this current Monday–Friday week** for the named special-number groups. Unlike A-04, which reviews the **previous week**, A-07 progresses whenever a new final result is confirmed within the **current week**.

### A-07.2 Start and data scope — USER CONFIRMED

- **Before the first confirmed draw of the week:** **A-07 is not active**. Do not claim to have analyzed categories from zero weekly results; do not silently use previous week's results.
- **As soon as the first result of the current week is confirmed:** **A-07 starts**. It must **not** wait until four draws (the minimum-four condition belongs to A-03, not A-07).
- For each subsequent confirmed morning/evening result in that same calendar week, include it in the week's occurrence status. Weekdays/market closures do not themselves count as results.
- **At the beginning of the next week, reset current-week occurrence status**. Do not carry last week's observations into the current-week tally.
- **Use finalized confirmed results only**; never count an upcoming or provisional live result as already occurred.

### A-07.3 Five categories to inspect — USER NAMED

| Category | Current-week question | Domain rule to apply |
|---|---|---|
| **အပူး** | Has this group appeared among the actual current-week results; what occurred? | Exact number membership must follow the validated 2D domain rule, not a new invented classification |
| **ညီကို / ညီအကို** | Has this group appeared among the actual current-week results; what occurred? | Confirm exact synonym/membership via existing app logic/user |
| **ပါဝါ** | Has this group appeared among the actual current-week results; what occurred? | Verify existing app definition/user rule before implementation |
| **နက္ခတ်** | Has this group appeared among the actual current-week results; what occurred? | Verify existing app definition/user rule before implementation |
| **ပြန်ကျ** | Has an **identical full 2D pair** appeared again within this same week? | Already confirmed in A-04: pair repeats across **any two draw occasions** within one week, same day or different days, **no adjacency required** |

The user's wording **"ထွက်ရှိမှု အခြေအနေ"** means report **what has/has not appeared so far**, not forecast the next result. Counts, numbers, exact occurrence sessions and breakdown may be useful but **their first-view detail/priority has not been confirmed yet**. Do not turn them into invented requirements.

### A-07.4 Confirmed relationship to ပြန်ကျ, and a worked example

**Confirmed from A-04 and user clarification on 2026-11-07:** Each **additional** appearance of the exact same complete 2D number **after its first appearance within that week** is one **ပြန်ကျ event**. For **01** appearing Monday AM, Wednesday PM and Friday AM, show **01 ပြန်ကျ ၂ ကြိမ်**. More generally a number's repeat count = `max(0, weeklyNumberOccurrences - 1)`. The first occurrence contributes zero. It does not require consecutive sessions. Seeing 01 and 10 is *not* recurrence of the identical full 2D pair. This same count rule applies to A-07 (current week) and A-04 (previous week).

**Illustrative test scenarios (not real historical results):**

- **Before Mon AM is finalized:** A-07 inactive.
- **After first confirmed result Mon AM = 01:** A-07 active. For **ပြန်ကျ**, **there has not yet been an exact pair repetition** in the one-result history. Do not label the entire A-07 inactive merely because ပြန်ကျ requires a second result.
- **After Mon PM = 01:** Current-week **ပြန်ကျ occurred** for complete pair **01**. This qualifies even though both draws are on Monday.
- **After Mon PM = 10 instead:** No identical 2D pair repetition of 01 yet; sharing the individual digits/reversal does not fulfill ပြန်ကျ.

The first 01/10 examples above are **only for the ပြန်ကျ criterion**. Do not infer the classification of 01 into **အပူး/ညီကို/ပါဝါ/နက္ခတ်** without their validated membership rules.

### A-07.5 Keep A-07 distinct from adjacent methods

- **A-04:** Last week's 0–9 frequencies, parity, named special groups, ပြန်ကျ and still-to-be-clarified "ကျန်ဂဏန်း".
- **A-05:** Current week's **single-digit new/old flow and remaining 0–9 digits**, activated after one result. This is not the same as special-group occurrence.
- **A-06:** Longest **ongoing** absence counts across historical AM-only, PM-only and combined draw streams; not limited to special groups this week.
- **A-07:** **This week's** named special-group appearance status, activated after **one confirmed result**. In particular, **ပြန်ကျ is repetition of a complete 2D pair**, not a repeated single digit.

### A-07.6 Pending; do not invent

- **Q-30:** Verify exact category definitions for **အပူး, ညီကို, ပါဝါ, နက္ခတ်** and any group overlaps; reuse verified app domain behavior.
- **Q-31:** Precisely how should the weekly status be summarized for each category (appeared yes/no, which result(s), occurrence count, draw dates and sessions, or some combination)? The essential current-week *appearance-status check* is agreed, but UI detail has not been finalized.
- **Q-32 RESOLVED (counting):** For a pair observed N times in the same current week, ပြန်ကျ = max(0, N−1); e.g. 01 observed three times → two repeats. The exact UI layout/optional separate total-appearance number remains a design decision, not a counting ambiguity.
- **Q-33:** When source data for a past scheduled current-week draw is missing, how should incompleteness affect "not appeared yet" claims? Known holiday/no-draw does not produce a missing result.

**Feature assignment:** undecided until the user finishes explaining all methods.  
**Code/UI status:** do not implement; Master Document update only.

---

## A-08 — Historical exact-result conditional continuation: BOTH prior results AND each individually

**State:** The user finished explaining the **core A-08 method**. Its three **always-run** searches, AM/PM-specific source-result windows, and the Friday→Monday weekly boundary example are **CONFIRMED**. History-range default/option is confirmed; market-closure, range-boundary and presentation edge cases remain pending. **Do not modify analysis code/UI or group into features yet.**

### A-08.1 What problem this answers (confirmed)

The analysis centers on a **target draw that has not yet produced its result** and **two already confirmed relevant previous 2D results**. It asks: *On historical occurrences of these exact preceding results, what complete 2D values occurred in the corresponding next target session?*

This is a **conditional historical-continuation check**, unlike A-02's unconditional frequency for the same weekday/session, and unlike A-03's 4+ consecutive parity-class matching.

The assistant previously assumed Mon PM was background and wrongly proposed a fallback ("match both or else match each separately"). **USER CORRECTED BOTH ASSUMPTIONS.** The two results are both matching inputs, and **all three checks ALWAYS run**, whether the two-result match exists or not.

### A-08.2 Which TWO earlier results are used (confirmed)

| Target draw to analyze | First reference result | Second reference result | Historical continuation slot |
|---|---|---|---|
| **Tuesday AM** | Monday AM | Monday PM | Tuesday AM |
| **Tuesday PM** | Monday PM | Tuesday AM | Tuesday PM |
| **Monday AM** | **Previous week's Friday AM** | **Previous week's Friday PM** | Following Monday AM |
| **Monday PM** | **Previous week's Friday PM** | **Current Monday AM** | Monday PM |

**General selection principle reflected in these examples:** For an upcoming **morning**, use the two sessions of the **preceding trading day**. For an upcoming **evening**, use the **preceding trading day's evening** plus **the current day's confirmed morning**. Friday→Monday **does cross the week boundary** in this specific A-08 analysis; **do not reuse A-03's same-week-only boundary rule**. The exact holiday/closed-day adjustment needs explicit verification later.

The reference slots are positionally meaningful. Search historical comparable **weekday/session positions**, not arbitrary occurrences of the same number on different weekdays.

### A-08.3 The THREE historical searches — ALWAYS performed

Given earlier references `A` (first slot) and `B` (second slot), and target session `T`:

1. **Two-result exact match**: Find historical sequences where first reference slot **equals A** AND second reference slot **equals B**. For each, inspect what exact 2D result occurred in the corresponding **target T** session.
2. **First reference only**: Separately find historical sequences where the first slot **equals A**, regardless of the second slot. Inspect the corresponding target **T** 2D results.
3. **Second reference only**: Separately find historical sequences where the second slot **equals B**, regardless of the first slot. Inspect the corresponding target **T** 2D results.

**Critical business rule:** These are **three separate outputs, not a fallback hierarchy**. Even when search #1 finds many matches, **searches #2 and #3 must still be executed and reported**. If #1 finds none, do #2 and #3 anyway. Never stop early and never silently combine the result populations into one total (the same historical record may appear in more than one search).

The target result is an **exact 2D pair** in 00–99 (e.g., 02 is not 20). Historical outcomes are observations, not next-result guarantees.

### A-08.4 Worked example for Tuesday MORNING — USER PROVIDED INPUT

Current known confirmed results:
- **Monday AM = 02**
- **Monday PM = 89**
- Target to inspect = **Tuesday AM** (not yet occurred).

Run **all three**:
- #1 **Mon AM=02 AND Mon PM=89** → what was **Tue AM** in each matching historical week?
- #2 **Mon AM=02** (regardless of Mon PM) → what was **Tue AM** historically?
- #3 **Mon PM=89** (regardless of Mon AM) → what was **Tue AM** historically?

Illustrative hypothetical historical records (not actual database data):

| History week | Mon AM | Mon PM | Tue AM | #1 both? | #2 AM only? | #3 PM only? |
|---|---|---|---|---|---|---|
| Example A | 02 | 89 | 35 | YES | YES | YES |
| Example B | 02 | 44 | 71 | no | YES | no |
| Example C | 77 | 89 | 40 | no | no | YES |
| Example D | 77 | 44 | 10 | no | no | no |

Then the three **separate** observed follow-on lists are:
- #1 both matched → Tue AM historical outcome(s) **35**.
- #2 Mon AM alone matched → Tue AM outcomes **35, 71**.
- #3 Mon PM alone matched → Tue AM outcomes **35, 40**.

**Example A must appear in all three relevant matched populations**; this overlap is correct and intentional. Never subtract it from the single-reference analyses or merge them into one disjoint sample. These numbers are illustrative only, not predictions or actual historical findings.

### A-08.5 Worked example for Tuesday EVENING — USER SPECIFIED DIFFERENT REFERENCE WINDOW

The user explicitly corrected: **Evening analysis must use yesterday's evening result plus today's morning result**, not yesterday's two results.

For **Tuesday PM**:
- First reference: **Monday PM=89**.
- Second reference: **Tuesday AM=35** (illustrative).
- Target: **Tuesday PM**.

Always run:
- #1 historical **Mon PM=89 AND Tue AM=35** → historical Tue PM result.
- #2 historical **Mon PM=89** → historical Tue PM result.
- #3 historical **Tue AM=35** → historical Tue PM result.

This must **not** be mistaken for Tue AM's Mon AM+Mon PM comparison.

### A-08.6 Worked example for MONDAY MORNING — USER CONFIRMED cross-week exception

On **Monday AM**, the preceding relevant trading day is normally the **previous week's Friday**.

Illustration:
- Previous Friday AM=25.
- Previous Friday PM=78.
- Target=current Monday AM.

Always search historical transitions **Fri AM=25 AND Fri PM=78 → next Mon AM**, **Fri AM=25 alone → next Mon AM**, **Fri PM=78 alone → next Mon AM**.

This explicitly **crosses from one week to the next** and must not be blocked by weekly-reset logic from A-01, A-03, A-05, A-07.

### A-08.7 Eligibility, observations and integrity

- Run against **confirmed actual results**. The two reference slots are needed to run their intended searches; no rule for missing/closed source slots was approved yet.
- Inspect the **correct future slot relative to the historical reference window**, not the immediately next result by accident. For Tue AM, the target is Tue AM even though Mon PM happens between morning draws.
- Count/retain matching historical examples separately for each of the three groups; evidence can include dates and results. History matching **does not imply a future probability**.
- Do not treat unknown historical target results as "not matching" outcomes. Do not allow a result from a different weekday/session to stand in unnoticed.
- A-08 is about matching **actual 2D result values**, not shared digits, even/odd categories, or special groups.

### A-08.8 What remains open (implementation details, not reasons to repeat the user's explanation)

- **Q-34 RESOLVED:** Both reference slots **AND** each separate reference slot are **always examined**. They are not conditional fallback modes.
- **Q-35 RESOLVED (core):** Morning and evening use **different two-result windows**, and the user explicitly included the Friday→Monday cross-week morning case; the table above captures the explained normal-week cases.
- **Q-36 OPEN:** How do true holidays / unusual closures change "previous trading day" and comparable historical slot matching? Do not silently invent a rule.
- **Q-37 OPEN:** History-range choice is **resolved** (all available by default, optional week/month/custom); minimum verified sample, eligible episodes spanning filter boundaries, tie/evidence display, and additional breakdowns remain open.
- **Q-38 RESOLVED:** Evening has its own logic: previous day's evening + current morning, then the same three historical searches.

**Feature grouping:** undecided until all practical checks are collected.  
**Code/UI:** No implementation authorized by this specification update; preserve "မူ" and other app behavior.

---

# Part IV — Other user-described observations (not yet separate confirmed methods)

These observations came from the user to illustrate the real-world analysis domain, **not** to authorize implementing new detectors:

- This week's **အပူး, ပါဝါ, နက္ခတ်, ညီအကို** appearing/not appearing.
- Weekly **စုံစုံ, မမ, စုံမ, မစုံ** progression.
- `14 → 26 → 35`: no single digit repeats between the three successive draws; all six digits are new.
- `14 → 45 → 65`: successive draws share one digit (4 then 5), even though the shared digit changes.
- Week's missing single-digit set and the interplay of current and historical weeks.

When the user describes an exact method using any of these observations, **give it a new method ID or attach it by explicit user agreement**. Do not create implied formulas.

---

## Shared verified closed-day policy — USER CONFIRMED 2026-11-07

**User instruction:** **"ပိတ်ရက်ဆို အဲ့ပိတ်ရက်ကို မစစ်ဘူး"** — if a date is an **actual verified closed/no-draw day**, **exclude that entire day from the analysis**. Neither morning nor evening on that date is an eligible draw occasion. A verified closure is **not** a missing data point, a zero result, an absence event, or an observed draw; it does not increase an AM, PM, or combined absence streak.

**Practical examples (illustrative):**
- Monday AM=12 and PM=35 → both eligible.
- Tuesday **verified closure** → Tuesday excluded, contributes zero observations and **no "Result မတွေ့" warning for an expected draw**.
- Wednesday AM=09 and PM=46 → both eligible and analyzed. the available real draws remain eligible for **other methods**; **A-03 does not pattern-match that closed-day week**, because weekday+AM/PM slot correspondence is required and no results are shifted into the closed slots.

**Critical distinction:** A date/session for which no result can be found **without proof of a closure** must be labeled **"Result မတွေ့"**, and any other confirmed results must still be analyzed. Do not equate absence of a feed record with an exchange holiday. Determine closure from trusted closure evidence where available; the existing app has a `closed_days` table, but its historical coverage and external market-closure completeness need inspection and must not be assumed.

**Scope:** Apply this exclusion rule consistently to A-01 through A-08 while preserving each method's separate confirmed reference scope, weekly boundaries and chronology. For A-03 **specifically**, **exclude a week containing a verified closed date from strict week-pattern matching**; do not compress the week. For all other methods, skip the verified closed day and continue using other available confirmed results. For A-08, **do not invent** a reference replacement/previous-trading-day mapping for a closed date; its specific Friday→Monday, adjacent reference and historical comparison alignment need separate precise rules.

**A-03 now fully specified on this point by later user correction (2026-11-07):** matching requires the **same weekday, same AM/PM session and same corresponding parity class**. Weeks with verified closed dates are **excluded from A-03's week-pattern matching** rather than compressed. This is an A-03-specific exception to the shared principle of using remaining confirmed results, which continues to apply to other methods. A-08's previous-trading-day reference mapping across closures remains open.

---

## Shared missing-result and partial-history policy — USER CONFIRMED 2026-11-07

**User's exact business instruction:** **"Result မတွေ့ရင် မတွေ့ကြောင်းပြောမယ် တွေ့တဲ့ result အတွက်တော့ စစ်ပေးရမယ်"**.

**CONFIRMED:** When a result is unavailable for an expected date/session, explicitly label that occasion **"Result မတွေ့" / "Result not found"**, while **still analyzing every other confirmed, available 2D result** that qualifies for the requested method. Do **not** exclude an entire week/date range, abort the analysis, or fabricate a result solely because one other historical occasion is missing. Include numbers of examined confirmed results and missing occasions when determinable.

**Critical truthfulness distinction:** A missing/unrecorded result is **unknown**, not a verified instance in which digit `d` failed to appear, a group failed to occur, or a particular two-digit number was absent. The app may report **"not seen among available results"**, but cannot certify **"did not appear in the complete week"** unless completeness can be established. A **verified closed/no-draw day is completely excluded from analysis**, not counted as an expected draw or as a missing feed result. Otherwise, where closure is not verified, a missing record is **unknown**, not necessarily a holiday.

**Method-specific guardrails:**
- **A-01:** Always count/show known draws and which digits were observed. If some previous-week sessions are unknown, label any missing-digit conclusion as **provisional / not observed in available results**, not proven absent for the whole previous week. For its historical **first-return** companion ranking, an earlier unknown draw in the following week can prevent confirming that a later observed result was truly **first**: do not promote a merely first-*observed* candidate to a verified first-return event. Still report the known results with coverage status.
- **A-02 / A-04:** Calculate digit-position, parity and special-group frequencies using all valid in-scope known results. Show denominator/coverage. Partial weeks or sparse same-weekday/session history are **analyzed**, not thrown away.
- **A-03 (specific exception):** For strict weekday+AM/PM+parity-class pattern matching, **exclude a week with a verified closed day** rather than compacting or shifting its sequence. For an eligible open week, require at least **four confirmed preceding draws**. Preserve other known results for other methods and flag unverified missing slots, rather than manufacturing an exact match. Preserve slot identity/gap status; report that exact matching is not verifiable for the affected segment if needed. How confirmed true closures align with historic patterns remains an independent open item.
- **A-05 / A-07:** Continue current-week observed-result progression/group tally using known results, explicitly flag missing slots. An unknown gap may prevent asserting that a digit/group is definitively unseen for the entire current week; do not invent intermediate "new/old" state or group events.
- **A-06:** Count **confirmed relevant draw occasions**, never calendar days and never an unknown result as if it were a known nonappearance. Present observed-data absence count with an incomplete-coverage label when an unknown gap could change the ongoing streak; do not manufacture a longer verified streak.
- **A-08:** Run each of the three searches using its exact specified date/AM-PM reference and target slots, when the required historical values are known. Unknown values for a candidate matching episode cannot be substituted or inferred, but other fully evidenced episodes remain eligible; show known sample and missing evidence separately.

**Implementation rule:** Prefer useful, accurately labeled **partial answers** over a blank feature or fabricated complete answer. Distinguish genuinely **no matching observations** from **insufficient evidence to evaluate a particular matching episode**. The user's confirmation authorizes partial-data evaluation, but does **not yet settle** all market-holiday alignment rules (A-03/A-08) or exact historical reference-window boundary conventions.

---

## Shared history-range policy — USER CONFIRMED 2026-11-07

**Scope:** All methods which examine historical periods beyond their intrinsically fixed week — in particular **A-01, A-02, A-03, A-06, A-08** — should use the full locally **available verified history** by default, not a hard-coded 1-year/3-year or last-month analytical cap. The limit is **what the App actually has and can verify**, not an assumed amount of data. Historical result sync/depth is an independent ingestion concern.

**User-selectable range:** User may override the default and analyze a **selected time window**, including **one-week**, **one-month**, and **custom start/end dates** (examples explicitly requested by user). The precise quick-filter definitions (rolling 7 days vs calendar week, rolling month vs calendar month), plus other useful presets, can be settled during UI design. The default must remain **All available history**.

**Behavioral boundaries:**
- The selected range filters the **historical evidence** used for qualifying and counting matches; it must not secretly change a method's underlying calculation rules, weekday/session identity, or other confirmed data scope.
- **A-04** is intrinsically the **previous completed Monday–Friday week**, and **A-05/A-07** intrinsically describe the **current Monday–Friday week**. Do **not** reinterpret them as arbitrary "last 7 days" or last-month methods because of a shared History filter.
- For A-01's missing-week→following-week pairs, A-03's within-week prefix+next result, and A-08's reference draws+target, selected-window boundaries may split a **logical analysis episode**. Do **not** silently treat an incomplete episode as negative/no match; specify later whether eligible episodes are defined by the date of the historical source week/target or whether every supporting draw must be inside the requested window.
- A-06's ongoing missing streak can be truncated by a selected start date. Do **not** state a truncated duration as an all-history streak, and never convert a never-observed candidate within a short range into a verified long absence.
- Show the **actual effective history start/end and eligible confirmed sample size**; distinguish "no matches in this selected period" from "no matches in all available history".
- Historical matching must use only source records appropriate to the method and must not introduce data look-ahead beyond its intended observation cutoff.
- UI and analytics must remain responsive for large available historical datasets; no arbitrary shortened data horizon may be silently applied.

**Approved policy:** All available by default; week/month/custom optional user range. **Pending implementation details:** exact shortcut date semantics, cross-boundary episode eligibility, and stream-specific truncated-streak labels.

---

# Part V — Pending decisions index

| ID | Topic | Needed clarification |
|---|---|---|
| Q-01 | A-01 holiday week | PARTLY RESOLVED: verified closed dates are excluded; analyze known draws, mark missing records unknown; establishing historical closure/week completeness still open |
| Q-02 | A-01 history | PARTLY RESOLVED: all available verified history default, optional week/month/custom; show/use known evidence; unknown before apparent first return is not verified first return; denominator and range boundaries pending |
| Q-03 | A-02 horizon | RESOLVED: all available verified history by default, optional user one-week/one-month/custom dates |
| Q-04 | A-02 digit tally | RESOLVED: count both positions; 55 contributes two occurrences of digit 5 |
| Q-05 | A-02 minimum | RESOLVED: least frequency excludes zeros; zero-observation values are listed separately as never observed |
| Q-06 | A-02 groups | User-approved exact mapping of doubles/power/nakhat/siblings and overlap rules |
| Q-07 | A-03 holidays | RESOLVED: historical match MUST align identical weekday, identical AM/PM, identical corresponding even/odd class; any week with a verified closed day is excluded from A-03 strict pattern matching, not compressed |
| Q-08 | A-03 missing records | PARTLY RESOLVED: closed-day weeks excluded from A-03; unknown open-day result gaps are flagged and not compressed/shifted. Final week eligibility for unknown records pending |
| Q-09 | A-03 sequence location | RESOLVED: prefix strictly anchored at Monday morning at start of week, never sliding midweek; verified closure-week excluded |
| Q-10 | A-03 history horizon | PARTLY RESOLVED: all available verified historical weeks by default, user-selectable one-week/one-month/custom range; qualifying next draw and boundary cases pending |
| Q-11 | Design | PARTLY RESOLVED: 4+2+2 timeline grouping with per-group concise previews, expandable details, compact premium Material 3 and soft elevated surfaces approved; mobile screenshot acceptance pending |
| Q-12 | Spec workflow | RESOLVED: eight checks collected, grouped 4+2+2, UI approved, implementation authorized on isolated branch |
| Q-13 | A-04 ပြန်ကျ | RESOLVED: exact 2D repeat count per number = max(0, occurrences within previous week − 1); first appearance excluded, 3 appearances → 2 repeats. Optional UI presentation later |
| Q-14 | A-04 ကျန်ဂဏန်း | RESOLVED: 0–9 single digits absent from both positions of every confirmed draw in the completed previous week; not 00–99 pairs |
| Q-15 | A-04 digit counts | PARTLY RESOLVED: 55 contributes two occurrences of digit 5; optional separate tens/ones display still undecided |
| Q-16 | A-04 classifications | Exact class/group mapping, overlaps and ညီကို/ညီအကို synonym |
| Q-17 | A-04 data completeness | RESOLVED base policy: exclude verified closed dates, analyze other known previous-week results, label missing open-day slots and unverified weeks |
| Q-18 | A-04 presentation | Default groups and drill-down after all methods collected |
| Q-19 | A-05 repeated digit within one draw | RESOLVED: first 11 = one new digit, remaining 234567890; optional wording for later already-seen doubles |
| Q-20 | A-05 tens/ones order | RESOLVED: preserve tens/ones order; new+old and old+new distinct |
| Q-21 | A-05 history scope | Current-week descriptive flow only or any user-approved history matching |
| Q-22 | A-05 presentation | Remaining current-week digits REQUIRED; final layout for draw/day flow and remaining set pending |
| Q-23 | A-05 completeness | PARTLY RESOLVED: skip verified closed dates, continue with known weekly results, flag unknown open-day gaps; historic closure evidence still needs validation |
| Q-24 | A-06 absence unit | RESOLVED: consecutive relevant completed result occasions, not calendar days; independent AM/PM/combined counters |
| Q-25 | A-06 history horizon | PARTLY RESOLVED: all available verified history default, optional week/month/custom range; never-seen, truncated-streak boundaries pending |
| Q-26 | A-06 target timing | RESOLVED: eligible confirmed results strictly BEFORE target draw; selected historical target is an exclusive cutoff, no later result leakage |
| Q-27 | A-06 completeness | PARTLY RESOLVED: skip verified closures with zero draw occasions; count confirmed observations only; missing open-day slots and unverifiable streak lengths remain flagged |
| Q-28 | A-06 presentation | All ties/top N, last-seen date, duration details and drill-down |
| Q-29 | A-06 meaning of longest | Current ongoing absence at the latest confirmed result is the user-described case; no unrequested all-time streak leaderboard |
| Q-30 | A-07 category definitions | Verify အပူး, ညီကို, ပါဝါ, နက္ခတ် groups and overlaps |
| Q-31 | A-07 occurrence status | Whether to show counts, involved results, dates/sessions alongside appeared/not-yet |
| Q-32 | A-07 ပြန်ကျ multiplicity | RESOLVED: exact 2D repeat count per number = max(0, occurrences within current week − 1); first appearance excluded, 3 appearances → 2 repeats. Optional UI presentation later |
| Q-33 | A-07 missing source data | RESOLVED base policy: skip verified closed dates; count observed current-week groups, flag unknown results and do not claim unseen digits as complete-week fact |
| Q-34 | A-08 three searches | RESOLVED: always match both references together AND first alone AND second alone; never fallback-only |
| Q-35 | A-08 reference selection | RESOLVED core: AM = previous trading day's AM+PM; PM = previous trading day PM+today AM; Friday→Monday explicit |
| Q-36 | A-08 holiday and closure | PARTLY RESOLVED: use nearest prior verified-open weekday across known closures, keeping slot identity and unknown records explicit; historic closure evidence completeness still needs validation |
| Q-37 | A-08 history sample | PARTLY RESOLVED: all available verified history default, optional week/month/custom range; incomplete/cross-boundary episodes, counts, ties and evidence pending |
| Q-38 | A-08 evening method | RESOLVED: today PM uses preceding day's PM + current day's AM, with all three searches |
| Q-39 | A-01 return partner frequency | RESOLVED OPTION A: first eligible confirmed 2D return result only in the following week; count one companion per historical missing-week episode |

Do not mistake the table for a user-approved implementation plan.

---

# Part V-A — User-approved timeline-based grouping (2026-11-07)

**APPROVED BY USER:** Organize the eight checks based on **which time context the user wants to understand**, NOT by the history source, algorithm type or forcing the smallest possible number of groups. The user explicitly prefers **three chronologically ordered groups** with a **4 + 2 + 2** assignment:

| Timeline order | User-facing group | Included methods | Why these belong together |
|---|---|---|---|
| **1** | **လက်ရှိအကြိမ်** (Current occasion) | **A-02, A-03, A-06, A-08** | Questions pertinent to the selected/next AM or PM occasion: same-day/session historical distribution, strict weekly parity pattern through the previous slots, as-of-now ongoing missing streaks, and continuation of preceding exact results |
| **2** | **ယခုအပတ်အခြေအနေ** (Current-week status) | **A-05, A-07** | Current week's observed new/old single-digit flow and named special-group occurrence/repeat status |
| **3** | **ပြီးခဲ့တဲ့အပတ်** (Previous-week review) | **A-04, A-01** | Completed previous-week frequencies/groups/missing digits, and a follow-up on that previous week's missing single digits into this week and historical return companion comparisons |

**A-01 placement explicitly confirmed:** Keep A-01 under **group 3 (previous week)**, **not** group 2, despite its continuation checking this week's return. Its entry question starts from the *previous* week's missing digits; its history and current-week comparison are part of that one method, not a new feature.

**A-06 placement:** The ongoing missing-streak metric uses all available earlier history but is **as of the selected occasion**, so group 1 is about *the question being answered*, not the period of source data. Enforce analysis cutoff so an already-completed later draw cannot leak into the selected earlier occasion.

**Important non-duplication and logic boundaries:**
- A-04's previous-week `ကျန်ဂဏန်း` is a summary; A-01's previous-week missing digits can reuse the same source set but must retain its additional current-week/historical first-return analysis.
- A-04 and A-07 share group categories and same-week `ပြန်ကျ` counting rule but remain distinct by **previous vs current week**.
- A-03's current-week sequence powers the *current/next occasion question*, so it stays in group 1 rather than being moved to group 2.
- Historical lookback (all available by default, optional one-week/one-month/custom) is a **data filter**, not an extra history group. Do not modify fixed-week scopes of A-01/A-04/A-05/A-07 just to match a UI filter.
- **2D Research Workspace remains separate**, not A-09 and not counted in the 4+2+2. The existing **မူ (Rules)** section remains independent and its logic/UI untouched.

**This approval confirms only the INFORMATION ARCHITECTURE / METHOD GROUPING.** It does **NOT** approve screen count, card layout, tabs, landing page, navigation style, colors, first-view answer order, or the placement of Research/Rules on screen. Those are **the next distinct discussion stage**. **Do not start implementing UI or engine based solely on grouping approval.** Retain each method's already confirmed calculation and evidence-safety rules unchanged.

---

# Part VI — UI/UX constraints after method collection

- **Grouping is now approved: timeline-based 4+2+2, see Part V-A. Do not redesign screens yet.** Decide the visual presentation/navigation in a separate discussion; prioritize clear user-facing answers and their evidence.
- **First view must prioritize the current target occasion's relevant checks**, not static counts, generic 00–99 grids, giant empty headers or accidental rankings.
- Missing-number analysis is important; show meaningful context (week, previous-week absence, current-week return, historical comparison), not just an overwhelming 0× list.
- Modern/premium, clear foreground/background layering and perceptible card elevation (including selected nested cards); coherent light/highlight and shadow, not only thin borders.
- Compact, readable Burmese and English parity; user-facing term မနက်အကြိမ် / ညနေအကြိမ်.
- Every emphasized statistic should have a **reason for being emphasized**; ties must not be randomly ranked.
- Display actual data coverage and dates to prevent a user from mistaking incomplete historical samples for full records.

## VI.1 Approved progressive disclosure — meaningful previews under EACH timeline group

**USER CONFIRMED on 2026-11-07:** The three approved 4+2+2 timeline groups are presented as clear, concise section headings. **Under every individual group heading (1, 2 and 3), provide a small summary of that same group's most important/useful actual findings even when the user has not opened the group.** These summaries are **NOT one shared/global highlight block** placed between groups. More complete checks/results remain accessible by expanding/opening the respective group.

**Priority:** Help an ordinary Myanmar 2D user immediately grasp what matters for the selected occasion while avoiding a screen full of text. The primary goal is practical usability and familiarity, **not implementation convenience or a minimal group count**. Use recognizable terms such as လက်ရှိအကြိမ်, ယခုအပတ်အခြေအနေ, ပြီးခဲ့တဲ့အပတ်, ကျန်ဂဏန်း, ပြန်ကျ, မနက်အကြိမ် and ညနေအကြိမ်. The technical method identifiers A-01...A-08 need not appear in the consumer UI.

**Information behavior:**
- Collapsed state: show the group title **and only a few short, useful, verifiable results from its own assigned checks**; do not enumerate all eight methods or expose lengthy explanations.
- Expanded state: allow access to the full results and evidence for all checks assigned to that group, preserving its same timeline context.
- A group's highlights should be **selected for relevance and availability**, not fixed arbitrary numbers, fabricated example results, claimed prediction probabilities, or overconfident "most likely to come out" labels.
- Clearly distinguish **no data**, **incomplete source records**, **no verified historical matches**, and **not yet eligible for a check**. When unavailable, do not fill the preview with guessed values.
- Preserve confirmed timeline assignment: group 1 A-02/A-03/A-06/A-08, group 2 A-05/A-07, group 3 A-04/A-01. In particular, A-01 stays in group 3; its current-week return portion is its supporting detail.
- **Still to design:** exact first-view highlight-selection policy, numbers of preview facts, wording, expand/collapse interaction, card/table/chart form, controls, screen navigation, and visual style. Do not treat an illustrative 2-value-per-group mockup as a confirmed fixed numerical requirement.

**Scope of approval:** Approves the group-specific visible preview idea and usability-first progressive disclosure; **no app code changed**, no finalized pixel layout, no commitment to a single-page implementation yet.


## VI.2 Implementation and user acceptance stage (authorized 2026-11-07)

The user subsequently approved moving from discussion to actual implementation. The historical "not approved" and "design later" notes elsewhere in the spec describe the **earlier planning phase**, not an instruction to stop the already-authorized rebuild.

- Working branch: `feature/2d-analysis-timeline-redesign`. The original `experiment/font-typography-lab-build930` baseline has not been modified by this rebuild.
- Implemented: Pure `TwoDAnalysisEngine` covering A-01 through A-08 with individual typed outputs and evidence, shared `TwoDigitGroups` with Quick Entry, and new `Historical2DTimelineScreen` plus detailed panels.
- Entry is one 2D Analysis feature, with three concise expandable timeline group cards in 4+2+2 order. Each card displays useful verifiable findings even while collapsed. A previous-screen route remains available for regression/fallback. `မူ` retains its placeholder scope.
- Result sources: operational `winning_numbers` take precedence; deleted-manual-result lock/tombstone blocks live/history fallback; in-progress current-day values cannot pass final result cutoff; verified closed dates excluded; unknown gaps surfaced.
- History lookback: all available verified history (default), one week, one month, or custom dates. Intrinsic current/previous-week checks stay fixed to their true week.
- Visual approach: material surfaces, subtle soft shadows/tonal elevation, expand/collapse motion, compact responsive labels, full MM/EN labels and evidence drill-down, not decorative heavy 3D.
- Automated regression: checks added for all eight engines, exact repeat counting, weekday/session anchoring, no look-ahead, source precedence, manual deletion locks, and chosen history-range boundaries.
- CI evidence: Android CI Build #995 completed successfully at commit `f3cd17ff0d345cf0f8bce0a58bc43dfe55c08e33`. **Later changes are NOT implied to pass by that earlier successful run**; confirm the latest branch-head CI before a release or merge.
- Remaining acceptance: user inspection on actual phone (layout, legibility, MM/EN wrapping, compact highlights) and historical data completeness in real imported feeds. No guarantee of next-result prediction.

# Part VII — Code/change safety

- This document is created **without code changes**. Existing analysis implementation is reference material, **not the newly approved business specification**.
- Preserve transaction entry, parser, commission/payout snapshots, customer and dai reporting, closed/limit logic, vouchers, live result reconciliation and background persistence.
- Preserve "မူ" unchanged.
- Keep analysis read-only over confirmed result data unless a future explicitly approved requirement changes that.
- In a future implementation, make unit tests for: correct 0–9 vs 00–99 scope, weekly boundaries, no look-ahead, holiday eligibility, ordered vs unordered matching, matched-next-result existence, duplicate-source handling, ties, and data completeness.
- Old analysis code files relevant for orientation: 
  - `app/src/main/java/com/myanmar/ledger2d/feature/main/Historical2DAnalysisScreen.kt`
  - `app/src/main/java/com/myanmar/ledger2d/feature/main/Historic2DDataSource.kt`
  - `app/src/test/java/com/myanmar/ledger2d/feature/main/Historic2DDataSourceTest.kt`
- Existing restore point before the Aurora analysis redesign: `backup/pre-aurora-analysis-build957-20261106`. Do not reset to it without explicit user direction.

# Part VIII — Requirement register / change-log template

**For each new user-described checking method, append:**

- **ID and provisional name**
- **Purpose / exact user question**
- **Data scope:** current target occasion, current/previous week, historical same weekday+session, historical weeks/pattern
- **Eligibility:** minimum confirmed draws, date/session constraints, holidays, missing-data handling
- **Exact classification/counting/matching algorithm in plain language**
- **Positive worked example:** input draws and expected check output
- **Negative worked example:** what must NOT match or be counted
- **Result contract:** counts, dates, matches, any no-result rule, evidence
- **Confirmed by user vs pending**
- **Tests to create after approval**
- **Feature assignment: APPROVED; timeline-based three-group 4+2+2, see Part V-A**
- **Change note and approval date** if later modified

## Decision log

| Date | Decision | Status |
|---|---|---|
| 2026-11-06 | Rebuild historical 2D Analysis around *useful checks* before redesigning UI/navigation | Confirmed |
| 2026-11-06 | Current target session + primarily Monday–Friday weekly frame; different methods use distinct history scopes | Confirmed |
| 2026-11-06 | A-01 previous-week missing digit and current-week return + historical next-week comparisons | Core logic confirmed; edge cases pending |
| 2026-11-06 | A-02 same weekday AND same AM/PM historical checks, five breakdowns | Core logic confirmed; counting/horizon pending |
| 2026-11-06 | A-03 strict four-class chronological sequence, min 4 confirmed draws, grows to 9 preceding Fri PM, same-week only | Core logic confirmed; holiday comparison pending |
| 2026-11-06 | Do not modify "မူ"; keep user's custom research idea separate for later feature planning | Confirmed |
| 2026-11-06 | Save detailed Master Specification in GitHub to avoid loss across conversations | Approved for documentation only |
| 2026-11-06 | A-04 previous-week 0–9, parity, special-group and remaining-number review | User listed check categories; definitions/edge cases pending |
| 2026-11-06 | A-04 ပြန်ကျ = exact 2D number repeated, e.g. Mon 01 then Tue 01 | User confirmed core definition; edge cases pending |
| 2026-11-06 | A-04 ပြန်ကျ includes recurrence at any later occasion in the same week, including same-day AM→PM, with no adjacency rule | User explicitly confirmed; handling 3+ appearances still pending |
| 2026-11-06 | A-05 current-week new/old single-digit flow: Mon 12,03 → Tue 02,35 | User supplied example; subsequent trigger/double/remaining rules confirmed |
| 2026-11-06 | A-05 activates only after first weekly final result; display remaining 0–9 unseen digits; first 12 → remaining 34567890; first 11 → one new digit 1 and remaining 234567890 | Explicitly confirmed by user; layout and other edge cases pending |
| 2026-11-06 | A-06 longest-absent 2D pair and 0–9 digit, each analyzed separately for AM-only, PM-only and both | User specified six checks; details pending |
| 2026-11-06 | A-06 absence counted in distinct relevant completed draws, not days. Morning 56 absent 100 AM results while PM 56 hit: AM remains missing; PM and combined reset on PM hit | Explicit user clarification, confirmed; do not re-ask this definition |
| 2026-11-06 | A-07 current-week အပူး, ညီကို, ပါဝါ, နက္ခတ်, ပြန်ကျ appearance status starts after just one confirmed weekly result | User explicitly confirmed scope/activation; details and category mappings pending |
| 2026-11-06 | A-08 initially recorded as single prior-morning condition | SUPERSEDED by the user's later clarification below; do NOT implement fallback-only |
| 2026-11-06 | A-08 three independent queries ALWAYS: exact pair of preceding reference draws, first draw alone, second draw alone | User confirmed; search all three even if two-draw match found |
| 2026-11-06 | A-08 AM references preceding day AM+PM; PM references preceding day PM + current AM; Mon AM uses previous Fri AM+PM | User confirmed; cross-week Fri→Mon allowed; closure handling pending |
| 2026-11-06 | 2D Research Workspace as a separate user-controlled historical-question testing feature, distinct from eight automatic checks and unchanged မူ | Previously approved by user; reiterated after completing A-08; implementation design pending |
| 2026-11-07 | User reviewed detailed restatement of A-01 through A-08 and found the core logic complete and correct, with new A-01 addition below | Confirmed main understanding; previously pending details remain open |
| 2026-11-07 | A-01: prominently display which digit most frequently pairs with a digit when it returns after a missing week, grounded in historical returned 2D outcomes | User explicitly approved; ranking priority confirmed |
| 2026-11-07 | A-01 first-return partner frequency uses OPTION A: only the first result in the immediately following week containing the missing digit, one observation per historical missing-week episode; ignore subsequent occurrences for this ranking | Explicitly confirmed by user; Q-39 resolved |
| 2026-11-07 | A-04 ကျန်ဂဏန်း means unique digits 0–9 not observed in any confirmed AM/PM result of the completed previous week, tens or ones; not unused 00–99 pairs | Explicit user confirmation; Q-14 resolved |
| 2026-11-07 | A-02 and A-04 0–9 single-digit frequency counts **each digit position**; `55` adds 2 occurrences of digit 5, unlike A-05's distinct new/old set | Explicit user choice of 2; Q-04 resolved, Q-15 frequency portion resolved |
| 2026-11-07 | A-04/A-07 ပြန်ကျ = number's second and each subsequent same-week exact 2D occurrence, never its first; 01 occurring three times → 2 repeat events | Explicit user confirmation; Q-13 and Q-32 counting resolved |
| 2026-11-07 | History-based checks A-01/A-02/A-03/A-06/A-08 default to **all available verified historical data**; user can limit to **one week, one month or custom start/end dates** | Explicit confirmation by user; preserve fixed previous/current-week scopes of A-04/A-05/A-07 |
| 2026-11-07 | If a result is not found, explicitly say "Result မတွေ့" but keep analyzing all other available confirmed results rather than dropping the whole week/range; unknown slots are not proven negative results or safely compressible ordered history | Explicitly confirmed by user; partial-history data-coverage policy established, true holiday alignment still open |
| 2026-11-07 | Verified closed date is excluded from draw analysis; other methods still use available draws, but **A-03's strict slot-pattern check excludes a week with a confirmed closed date** | Earlier general closure-skip rule refined by latest user correction; Q-07 resolved, A-08 trading-day mapping still open |
| 2026-11-07 | A-03 historical MATCH needs **same weekday + same morning/evening occasion + same parity class** in each compared slot; do not compact holidays or shift results into different slots; verified closure-week is excluded from A-03 | Explicit corrective explanation by user; previous optional A/B interpretation superseded |
| 2026-11-07 | **Timeline grouping approved**: 1) လက်ရှိအကြိမ် = A-02/A-03/A-06/A-08; 2) ယခုအပတ်အခြေအနေ = A-05/A-07; 3) ပြီးခဲ့တဲ့အပတ် = A-04/A-01. **Keep A-01 in group 3** | User explicitly confirmed the 4+2+2 grouping and A-01 placement; layout and UI are not decided |
| 2026-11-07 | **Visible, relevant preview below each of the three timeline headings**: show a few of that group's most important/useful actual findings without opening it; full details expand within respective group, not one global highlight area | User explicitly confirmed per-group highlights and prioritizing user utility/readability; exact choices/design pending |

---

**Next action:** User authorized the isolated 2D Analysis rebuild after reviewing the 4+2+2 grouping, per-group highlights and premium soft-elevation visual direction. Validate full Android CI on the latest commit, then obtain real-phone visual/behavioral acceptance before merging. Keep Research Workspace and မူ independent.

# Part IX — 2D Research Workspace: separate feature concept (NOT a ninth fixed method)

**Decision status:** User previously **agreed** this should be an independent feature inside historical **2D Analysis**, separate from the fixed automatic checks A-01–A-08. The user reminded us again after finishing all eight methods. The **concept is approved**, but detailed inputs, navigation, UI and code are **not yet specified or implemented**.

## IX.1 Why Research is different from the eight checks

- **A-01–A-08:** Eight predefined analysis methods; the app chooses and runs their fixed, user-taught checks automatically with the appropriate current/previous/historical scope.
- **2D Research Workspace:** The **user invents a question/condition** and asks the app to test it **against the historical 2D results**, receiving matched source observations and clearly reported sample sizes. It is an exploratory *workspace*, not just another fixed frequency dashboard.
- **မူ (Rules):** A separate existing app area that the user explicitly requested **not to modify yet**. Research must not be silently renamed into, or replace, this Rules destination.
- **Other 2D candidate/policy/formula ideas:** Not automatically part of Research; no approval to implement unconfirmed generating/predictive formulas or gambler-odds assertions.

## IX.2 Example from the original Research conversation — illustrative

The user may ask:

> "**မနက် 12 ထွက်တဲ့နေ့တွေမှာ ညနေ 21 ထွက်ဖူးသလား?**"

A Research interaction should allow the user to express this **conditional historical question** rather than hard-code a special case. It would inspect historical dates with **AM=12** and identify which of their **PM results were 21**, showing the actual matching dates/results, number of checked records and data limitations. The example illustrates the intended type of question; it is **not a claim that 12 predicts 21**, and no results are implied before the real history is queried.

The initial proposed concept also allowed exploring chosen **numbers, groups, time intervals and relationships** in historical data. The precise form and available condition operators require a later explicit specification; do not claim all combinations are already supported.

## IX.3 Intended user experience — high-level only

1. User specifies the number/condition/relationship to research (and potentially a historical range, when the design is agreed).
2. App checks only the relevant confirmed historical results, preserving exact draw dates and AM/PM identity and checking for missing records.
3. App returns direct factual answers, **matched dates and 2D values**, counts of matches, the number of eligible records, and limitations such as insufficient/partial data.
4. The user can study another question without needing a new preset analysis method for every pattern.

## IX.4 Strict exclusions and remaining design work

- Do **not** treat Research as **A-09**; it is **a separate user-driven tool**, whereas A-01–A-08 are eight specified automated checks.
- Do **not** merge Research with "မူ", change "မူ", or repurpose its current UI.
- Do not claim future-draw probability or guarantee from historical conditional counts.
- **Pending:** Which condition types/operators can users select? Can they combine two or more reference draws? Which historical ranges can be chosen? How will matched results and total eligible cases be presented? Are saved queries needed? These are design questions, not already approved behavior.
- **Implementation:** No source code changes authorized; define Research inputs and workflow after reviewing the eight methods and grouping the overall 2D Analysis features.

---

---

# Part X — Information-first 2D Analysis UI rules and experimental layout (2026-11-08)

## X.1 Three user-approved design rules

**Rule 1 — Information priority before layout.** Identify the purpose of the view and the smallest set of truly useful answers. Prioritize facts by what the user needs to know, then decide their placement; only after that choose cards, charts, grids and visual styling. Do not mistake implementation subchecks for equal-level headline information.

**Rule 2 — Progressive disclosure.** Show the highest-priority results immediately. Important but secondary facts must be accessible on demand, without crowding the first view. Eliminate genuinely unnecessary content; do not delete valid evidence or calculations. Avoid deep nested disclosure and unnecessary taps.

**Rule 3 — Smart space utilization.** Compact, legible and user-centered placement. Reduce wasted header height, giant cards, duplicate captions, empty paddings and pointless scrolling. Preserve enough spacing, Burmese/English legibility, tap targets and responsive behavior; compact does not mean tiny text.

## X.2 A-02 clarification

The A-02 method has five verified calculations but only **two top-level information families**:

1. **ဂဏန်းတွဲ (00–99):** exact pair frequency; odd/even class frequencies; special 2D group frequencies.
2. **ဂဏန်း (0–9):** combined single-digit frequency (both positions counted, e.g. 55 adds 5 twice); tens-only maximum; ones-only maximum.

User approved trying a **side-by-side comparison of ဂဏန်းတွဲ and ဂဏန်း** for the top-level answers, rather than two tall, separated sections. The paired panels are comparable in information *purpose*, not directly comparable in raw count denominators. Pair counts are per draw; single-digit counts are per digit position. Preserve equal-maximum ties. Single-digit least observed (>0) is an important initial answer; least-observed 2D pairs and the full 00–99 browser are secondary details, not a replacement for that answer. Show verified sample size compactly as context.

This layout is not authority to change the five A-02 calculations, validated domain group memberships, historical weekday/session matching, zero-vs-missing handling, or source precedence.

## X.3 Screen hierarchy — implementation trial, not yet phone-accepted

Retain the approved **three chronological groups** and fixed **4+2+2** assignment:

- **လက်ရှိအကြိမ်:** A-02 / A-03 / A-06 / A-08.
- **ယခုအပတ်အခြေအနေ:** A-05 / A-07.
- **ပြီးခဲ့တဲ့အပတ်:** A-04 / A-01.

**Main Analysis Overview** is an answer-first dashboard, not a method directory. Each group's closed/summary state shows a few actual verified facts from its own members (with missing/incomplete-data distinctions). Pressing a group enters a **focused group workspace**, where each assigned method displays a compact real-data preview; selecting the method opens the original full-evidence detail. This separates navigation from unnecessary content, limits initial scrolling, and preserves all eight engines. Research and Rules remain separate.

Group-specific initial result ideas (subject to phone testing and further priority evaluation):

- Current occasion: side-by-side A-02 pair and digit historical leaders; A-03/A-06/A-08 also retain their own result previews in the focused group.
- Current week: remaining 0–9 digits (A-05) and exact repeated 2D count (A-07).
- Previous week: recorded missing digits (A-04) and observed current-week returns (A-01).

**Implementation on isolated experimental branch:** On 2026-11-08 changed `Historical2DTimelineScreen.kt` to show real group previews rather than a selected-group method directory; added focused group results, with existing detail drill-down. Updated `TwoDA02Dashboard.kt` to show the pair/digit comparison first and place full pair frequencies/class groups and 0–9 frequency grids behind separate on-demand disclosures. These are **experimental UI edits pending CI plus real-device user approval**. Neither merging to main nor final acceptance is implied. All previous source-of-truth method specifications still apply.

## X.4 Acceptance checklist and limits

- Open Analysis: see the three groups and their own *actual* useful outcomes, without first opening any directory.
- Open a group: see the correct 4, 2 or 2 methods, each with a brief nonfabricated answer; select a method and return correctly.
- A-02: pair/digit leaders side by side; least-observed *single digit* visible; fuller 00–99, parity, special groups, tens/ones and all-digit counts available when requested.
- Preserve all ties, complete historical context and verified-data counts; explicitly distinguish missing results, insufficient eligibility and observed zero.
- Review small-screen MM/EN typography, overflow, scrolling, tap sizes, focus/Back behavior and updates when target or range changes.
- Check against all eight method unit tests and unrelated ledger functionality.
- **A-02 evidence gap addressed in subsequent isolated implementation (2026-11-08):** The analysis result now exposes verified date/session records for each exact two-digit pair, filtered by the same weekday/session, selected historical range, closed-day rules and target cutoff. The 00–99 browser now opens each number's date/session evidence. Unit coverage tests this data isolation. Actual phone verification remains pending.
- A-02's least-observed nonzero single digit is now displayed in group previews as a third compact fact; tied leaders can be opened in a full list without arbitrarily selecting one. The pair/digit comparison retains the two equal-priority panes.
- Preservation check: compared against previously successful build #1016, changed files are limited to the analysis engine, the two analysis UI files, the analysis regression test and this specification. Unrelated ledger feature files remain unchanged in this set of commits. Passing CI and on-device acceptance are still independently required.
- Do not merge to the baseline branch until the user accepts the real-phone appearance and behavior.

---

# Part XI — Approved three-tag, result-first 4–2–2 mobile grid (2026-11-08)

## XI.1 User-authorized frame layout

After the on-device screenshot showed that the previous 3 vertically stacked timeline cards overemphasized headings and wasted screen height, the user approved a different **frame structure** and explicitly authorized implementing a first version. This is not an approval of exact fonts, colors, or per-method content proportions yet.

- **At the top:** a compact single-row selector of three **timeline tags**, in order: လက်ရှိအကြိမ်, ယခုအပတ်, ပြီးခဲ့တဲ့အပတ်.
- **Immediately below:** the selected tag's **actual analysis results**, in an **adaptive-height two-column grid**, not another screen containing only method names.
- **Tag 1:** A-02, A-03, A-06, A-08, as a 2×2 grid.
- **Tag 2:** A-05 and A-07, as a 2×1 grid.
- **Tag 3:** A-04 and A-01, as a 2×1 grid.
- Pressing a tag swaps result frames **in place**. No intermediate group-selection screen. Each frame displays as much important verified information as can be read clearly in its available space. Full method evidence is available by tapping the frame when genuinely necessary.
- Headings are quiet, short labels; actual result values lead visual hierarchy. Do not stretch no-data statuses into tall empty cards. Do not shrink Myanmar or English typography arbitrarily to force long strings into narrow cells.
- Target date and morning/evening selectors remain accessible in a compact row; historical period filter is shown only for groups with history-dependent checks. Fixed-week A-04/A-05/A-07 calculations are unaffected by a historical lookback filter.
- Keep Analysis / Research / Rules bottom navigation as before. The Research and Rules content is out of scope.

## XI.2 Initial content priorities for each frame

| Method | Direct result(s) in grid | Secondary/on-demand evidence |
|---|---|---|
| A-02 | Same-weekday/session top exact pair and top 0–9 digit side-by-side, both observed frequencies; least observed *nonzero* digit; matched sample count | All ties, per-pair date/session evidence, 00–99 list, parity/special-group distribution, tens/ones |
| A-03 | Number of matching historical weeks and following parity class (including ties or no matches) | Historical matched weeks, chronological prefixes and evidence |
| A-06 | Longest unobserved pair and single digit for target AM/PM stream; observed-draw gaps and lower-bound disclaimer | Separate AM/PM/combined streaks and underlying history |
| A-08 | Number of historical occurrences matching both preceding results; comparisons matching one preceding result | First/second reference details, matching observations and historical evidence |
| A-05 | Current-week unseen digits and latest verified newly observed digits, with incomplete-week flag | Per-draw new/old flows and evidence |
| A-07 | Current-week exact 2D repeat count plus counts for named special groups, and incomplete-week flag | Each occurrence's date/session and exact repeated numbers |
| A-04 | Previous-week missing digits and exact repeat count, labelled as incomplete if record coverage is partial | Complete frequency distributions and date/session evidence |
| A-01 | Previously missing digits observed returning this week, and previous-week missing set as context; avoid presenting this as a new current-week-only method | Historical next-week return episodes and companion digits |

**Rule enforcement:** group labels and tile headings must not dominate results; choose typography/spacing/layout to match information. Show a concise unavailable/insufficient state without false 0 claims. Preserve ties, verified-data cutoff, source precedence, closed-day rules, and the exact analysis methods from earlier parts of this specification. No predictive claims.

## XI.3 Implementation status / verification gate

This first layout implementation is on the isolated `feature/2d-analysis-timeline-redesign` branch, with its main UI call replaced by `TwoDAnalysisResultGrid` in a new dedicated `TwoDAnalysisResultGrid.kt`. All A-01–A-08 domain outputs and existing deep-detail routes remain intact. Existing non-analysis ledger source modules are not part of this layout change.

The user may request further visual revisions after testing. **Accept only after the new branch-head CI passes and the user checks the actual phone**, especially the 2-column fit, Burmese/English wrapping, missing-result compactness, and all tab/date/filter interactions. Earlier green CI builds do not prove later code passed.

## XI.4 Second-pass visual and wording audit after user's on-device screenshot (2026-11-08)

The three-tag 4/2/2 frame arrangement was accepted; the **result presentation was not**. The user's screenshot exposed oversized prose, weak number hierarchy, confusing shorthand, and recycled full-width previews in narrow grid tiles. The initial grid had one shared `twoDQuickFacts()` label/value/note renderer for most methods. That is explicitly **not an acceptable final method-specific design**.

The isolated UI implementation now uses distinct per-method data renderings in `TwoDAnalysisResultGrid.kt`, consuming the existing immutable `TwoDAnalysisReport` directly:

- **Information**: factual primary values lead. Small muted heading, strong key numbers, muted units and supporting caveats. Use the accent for meaningful observed values rather than feature labels or meaningless zeros.
- **A-02**: two related information families, 00–99 top pair / 0–9 top digit, with counts clearly distinguished, plus least-observed positive digit and verified sample size. Multiple maximum ties are not silently reduced to one definitive winner; the detail page retains evidence.
- **A-03**: distinguish an ineligible check from a completed search with no matching week; do not display an artificial zero for ineligible checks.
- **A-06**: distinguish the historical number/digit from its observed-draw gap. Avoid presenting the unexplained symbol `≥` as an exact claim about calendar draws. Unknown slots/truncated history require a subtle *verified-records only* qualification, while full detail retains the metadata.
- **A-08**: foreground the two actual preceding verified reference results; do not celebrate a large zero when no dual-reference historical match is verified. Show first/second match counts with explanatory labels and preserve unknown-follow-up warnings.
- **A-05/A-04/A-01**: numeric digit sets use legible, wrap-capable type rather than oversized single-value fonts that truncate sets. Clearly distinguish missing, no observed return, and incomplete records.
- **A-07**: display exact 2D repeat count plus the actual special-group distribution rather than using one generic headline metric.
- Keep data-heavy details on the existing detail routes. Do not delete evidence, change sample denominators, or invent predictor probabilities.
- Preserve the entire accepted frame structure and previously built domain engines; these changes are presentational.

**Verification caveat:** Passing CI establishes compilation and existing automated tests, not actual on-device legibility. Review both MM/EN and small-display cases after installing the final build. Color contrast and the amount of visible text remain subject to user approval.

---

# Part XII — Latest user-approved Analysis-main-first rebuild contract (2026-11-08)

**This section supersedes Part XI's per-tile content and per-tile titles.** The previous 1038 on-phone screenshot was rejected because results looked like prose and empty findings filled tiles with words. The accepted structural frame remains 3 inline tags + 4/2/2 two-column grid. The user approved rebuilding **only the Analysis Main Screen first** and reviewing it on their phone before separately rebuilding each of the eight Detail Screens from scratch. Existing detail UI is temporarily retained as a working route, not accepted as the final design; preserve the original domain methods, history evidence and interactions until their full replacements exist.

## XII.1 Visual design requirements

- **NO method headings inside individual grid tiles.** Do not render titles like "နေ့တူ၊ အကြိမ်တူ" or "မထွက်တာကြာ".
- **NO explanatory sentences inside tiles.** The grid is a summary, not a compressed Detail Screen.
- Only the **shortest essential micro-labels**; English is allowed as the **fixed** grid label if it is shorter/clearer in a constrained tile, while Detail Screens remain properly localized.
- Result numbers/pairs must be **distinct physical UI objects**: raised/inset capsules or panels with contrasting deep-wine numbers, separate smaller count capsules, subtle depth and clear internal grouping. Avoid plain text + prose blending. No gratuitous 3D effects.
- No data / ineligible / no match must appear as a quiet minimal state (dash plus at most a tiny state label); do not invent a result, a large false zero or a paragraph.
- Hero ranking favors **highest observed frequencies**, NEVER least-observed results on the overview. An explicit absence method (A-06) is an exception because longest ongoing absence is its core purpose.
- Ties must not be hidden by arbitrarily selecting one winner; preserve full ties in subsequent Detail Screen rebuild.
- Main screen and detail views keep live data, date/AM/PM, historical range, holiday rules and no-lookahead cutoff.

## XII.2 Current main-grid selection (user-led)

| Method | Selected compact main-grid result | Deferred to detail |
|---|---|---|
| A-02 | Top historical **2D pair, single digit, four-way parity category, named special group, tens position and ones position**, each with recorded frequency | Least-observed cases, full distributions, every tie and date/session evidence |
| A-03 | Most common following **parity class** and its count among matching historical weeks, with tiny matched-week number | Exact sequences, compared weeks, next-result evidence and eligibility |
| A-06 | Longest-absent **0–9 single digit FIRST**, longest-absent **00–99 full 2D pair SECOND**, for each independent **AM, PM, ALL** stream. Both results remain visible with their own small observed-draw counts, including `+` for lower bounds. | Historical streak evidence, last seen, completeness and all ties |
| A-08 | Top observed **next exact 2D result** for all **three independent** prior-reference checks: BOTH/1st/2nd, with count | Actual two references, all match lists, next outcomes, unknown data |
| A-05 | This week's **unseen individual digits** and most recently seen **new** digits | Full chronological new/old transition trace |
| A-07 | This week's **အပူး, ညီအကို, ပါဝါ, နက္ခတ်, ပြန်ကျ** occurrence counts | All actual results/dates, history and repeat lines |
| A-04 | Previous week's **most observed single digit** and unseen individual digits | Parity/group/frequency/repeat detail |
| A-01 | Missing digits and **most observed companion** when they historically first returned the following week; preview up to two rows | All missing digits, current return status, historical episodes, ties and evidence |

**Vocabulary standardization**: use **ပျောက်ဂဏန်းတွဲ** for the complete missing 2D pair and **ပျောက်ဂဏန်း** for one missing digit, never revert to long "မထွက်တာကြာနေသော ဂဏန်းတွဲ" headers. Render **ညီအကို** (not "ညီကို") in the UI; keep the underlying verified domain key unchanged. Long method names are only for Detail Screens / definitions, never grid titles.

## XII.3 Scope and acceptance

New UI lives in `TwoDAnalysisResultGrid.kt`; selected main-screen microcopy improvements in `Historical2DTimelineScreen.kt`. A single source of truth for user-visible method wording is `TwoDAnalysisText.kt`. The temporarily retained old Detail Screen uses `TwoDAnalysisDetails.kt`, with wording only corrected; it is due for its **own separate, user-reviewed rebuild AFTER Main Screen approval**. The underlying `TwoDAnalysisEngine.kt` and unrelated Ledger modules are not changed by this visual work.

This is an **experimental implementation**, not a final phone-approved design. Verify CI on the current head, install APK, check Myanmar/English and screen widths, and have the user evaluate density, legibility, colors, inset result isolation and all tab states before starting Detail rebuild.


---

# Part XIII — On-phone Bento density correction (2026-11-08, after screenshot)

**The screenshot of build 1049 was NOT accepted.** The user reported A-02 as a giant six-card stack, A-03 as an oversized empty tile, mismatched column heights, mixed numbers and labels, and insufficient premium hierarchy. Do not reintroduce that nested-card layout.

## XIII.1 Replacement home-grid layout

- Preserve the three timeline tags and **four checks / two checks / two checks**. The four current-draw tiles are now positioned to balance heights: left column A-02 then compact A-03, right column A-06 then A-08. All four checks remain tappable to the existing details.
- The A-02 six-dimensional maximum-frequency data is **one card** containing only two tinted hero wells (2D pair and 0–9 digit) followed by two FLAT summary rows (top parity class and top named special group), followed by one tiny top-tens/top-ones row. No six nested rounded cards or long headings. Do not silently omit any of the six approved maxima.
- When A-03 is ineligible or has no historical match, its entire summary tile collapses to one compact status row, not a tall blank canvas. Ineligible and verified no-match remain distinct.
- A-06 shows three independent AM/PM/ALL blocks. In **each block**, prioritize the longest-absent **single digit (0–9)** with larger type and stronger rose visual layer, followed by the longest-absent **2D pair (00–99)** in a quieter secondary layer. Show **both** observed nonappearance counts in small subdued text; unknown-history/open-slot truncation uses `+` only as a lower-bound qualification. Neither result may be omitted or derived from the other.
- A-08 remains three short separate rows BOTH/1st/2nd, using independently computed historical next-target 2D frequencies.
- A-05/A-04 use small digit tokens with clear missing/unseen conditions. A-07 uses five flat counters; A-01 shows at most two first-return-companion previews.
- One coherent tile surface, tiny consistent result chips, high-contrast deep-wine numeric glyphs, hairline separators, minimal supporting labels and no body explanations. Chevron hints must not consume a whole extra row.
- For A-01, do not present a historical missing-digit return ranking as fully verified when the prior week is incomplete. Users can inspect incomplete evidence in the detail view instead. Likewise indicate A-04's observed-only rankings for incomplete weeks.
- This correction changes ONLY presentation selection, reading order and wording in the Analysis home grid. It does NOT change the A-01–A-08 engine, dates, historical lookback, filters, holiday handling or ledger features.

## XIII.2 Acceptance gate

The user has **not** approved the phone appearance of this revision yet. Check the latest branch-head CI, ensure both APK artifacts are produced, then review screenshot for spacing, contrasting number wells, no hidden ties and compact data-empty tiles. Rebuild the individual Detail Screens only after main-screen approval.


## XIII.3 Final user clarification: missing digit always outranks missing pair

The previous sample mockups omitted the missing **single digit** and visually overemphasized absence counts. The user explicitly corrected both mistakes and approved implementation of the latest sample style with this revised information hierarchy:

1. **ပျောက်ဂဏန်း** (0–9) is the **primary**, largest, highest-contrast result.
2. **ပျောက်ဂဏန်းတွဲ** (00–99) is the **secondary** result and remains visible beside it.
3. Their individual `မထွက်သေး ... ကြိမ်` draw counts are **small supporting data**, never the hero.
4. Repeat that hierarchy independently for **AM / PM / ALL**. Pull from `TwoDStreakStream.singleDigits` and `TwoDStreakStream.fullNumbers`; do not confuse 0–9 missing digits with a 00–99 missing pair. Never use the same stream data for three different streams.
5. Preserve ties and `+` lower-bound markers for incomplete recorded history. Keep the main grid as summary-only and retain all historical evidence in Detail Screens.
6. Latest user instruction authorizes **direct GitHub implementation**; do not generate further design sample images for this revision.

---

## Part XIV — Aligned and layered main dashboard (2026-11-08, approved implementation direction)

The user preferred **the second rendered visual design sample**, specifically its raised/3D-depth cards, but rejected the **independently stacked left/right column layout** as misaligned. The newest instruction was to implement the layout directly in the Android app, not to generate another image.

### XIV.1 Fixed structural plan

Retain the three top tabs and grouping 4–2–2 by method (no analytical reclassification). The **current-occasion** group has FOUR independently tappable analyses arranged in coherent aligned rows:

1. First row: **A-02 left and A-06 right**, with a shared maximum intrinsic row height for their populated cases. Never place A-03 directly below A-02 while placing A-08 independently below A-06 as an unaligned masonry column.
2. Second row: **A-03 full-width**, using a short, meaningful state when not eligible or no comparable recorded pattern exists. Do not fill its tile with explanation prose.
3. Third row: **A-08 full-width**, with its two reference 2D numbers displayed and its independent **Both/First/Second** frequency leaders in three adjacent mini panels. No fabricated matches.
4. Group 2 remains A-05/A-07; group 3 remains A-04/A-01. Equal-height row alignment applies to both one-row groups.

### XIV.2 Visual system

- Raised/glass-*like* card surfaces: shared corner radius, lightly tinted top-to-bottom gradient, subtle rim border, consistent outer elevation. Do not add blur/shader effects indiscriminately or sacrifice contrast.
- Hero number panels are deeper wine with stronger, but consistent depth. Supporting rows use quieter ivory/elevated surfaces and finer borders. Use the brand design system rather than a rainbow palette.
- Alignment is **structural**, not merely similar individual card sizes: match top card edges, shared row boundaries, common internal gutter and padding. Full-width rows resolve the unequal masonry columns.
- A-02 retains six historical **maximum-frequency** categories: pair, single digit, parity class, special group, tens and ones. Hero pair/digit; supporting categories less dominant. Avoid clipping Myanmar text and force-feeding counts into single horizontal lines.
- A-06 retains **missing single digit as top priority**, missing 2D pair as secondary for *each* AM, PM, ALL stream; their separate observed-draw counts stay small. Never replace any of the six answers with a derived or unrelated statistic; preserve incomplete-history `+`.
- The short method-context labels shown in the chosen design sample are **allowed on main cards** for immediate comprehension; these are not a license to restore multi-line explanations or full detail content.
- Old Detail Screens continue to work and retain history/interaction functionality; do not redesign them before the main-screen phone review.

### XIV.3 Verification gate

Build/test the isolated `feature/2d-analysis-timeline-redesign` branch, preserve `main`, and verify both debug/release APK plus on-device UI. Automated build success cannot certify visual equality to the rendered reference; phone validation is pending.

---

## Part XV — Temporary fully populated 4–2–2 fixed-screen demo

At the user's explicit request, **test the layout using invented, clearly labeled figures before applying it to production Analysis results**. Several real methods have insufficient verified historical data at the moment; a screenshot of these empty results cannot establish whether the grid will work when all checks are populated.

- A separate `TwoDAnalysisFixedDemoGrid.kt` owns hard-coded, obviously fake sample values for all eight cards. It has no DB, history, API, or settlement dependencies and does not mutate user records.
- `Historical2DTimelineScreen.kt` exposes a conspicuous `DEMO ON` / `DEMO` toggle in the app bar. **DEMO is on by default on this experimental branch only.** The banner states that the numbers are sample-only and the date/history filters do not apply. Switching off returns to the previous real-data rendering and detail navigation.
- In the **demo only**, the app bar, date/AM-PM, three tabs, history controls and bottom navigation occupy their ordinary space. The remaining screen height is passed to the grid by Compose `Modifier.weight(1f)`, with no main-page scrolling. **Four-card group** = identical screen-filling 2×2 cells. **Two-card groups** = side-by-side, equal-width and equal-height cells, but **do not stretch their short result content down to the bottom navigation**; use a balanced, content-density-based height capped by the available viewport.
- The preview includes populated A-02, A-03, A-06, A-08, A-05, A-07, A-04 and A-01, including ties and missing-digit/pair rankings. A-06 still highlights **missing 0–9 single digits before 00–99 pairs** with separate small counts for AM / PM / ALL.
- These are intentionally **visual test fixtures, NOT computed or predictive results**. The demo cards do not navigate into production Details, avoiding a misleading mixture of fake preview with genuine evidence.
- Evaluate the fixture at a normal phone width with Burmese and English labels, then stress-test oversized text and tie cases. Promote the fixed-layout rendering to real data **only after the user approves its on-device appearance**. Do not merge this experimental demo as permanent production behavior.

Build/test branch-only; never touch the main ledger data model.

## XV.1 Screenshot feedback correction — 2026-11-08

- The A-06 preview had a **dark, muddy grey 2D-pair inset frame**, over-layered borders, and counts cramped into tiny wells. Keep the **missing single digit primary**, **2D pair secondary**, but use one pale well per value, no nested grey frame, and content-measured labels/numbers/counts. Counts must remain readable without being the visual focal point.
- The user rejected stretching A-05/A-07 and A-04/A-01 cards down to the bottom navigation when there are few results. **Each two-card pair stays side-by-side and matched in height, but takes only the height justified by the denser card.** Extra viewport space below can remain blank; do not push the data rows apart to fill it.
- **Do not modify the 2×2 full-remaining-height rule for the current-draw four-card demo.** The demo is still non-persistent; its values are fabricated and the real data path is unchanged.

## XV.2 Inner sizing and premium surface acceptance (user-requested)

The three Build #1079 phone screenshots showed **A-01's final row clipped**, and both two-card tabs still required a more balanced, premium visual style. The user explicitly authorized BOTH layout corrections and 3D/glass polish, while **retaining the demo-only fixtures**.

- **A-04/A-01 and A-05/A-07:** use the *actual intrinsic rendered content height* of the denser card to size the single side-by-side row; equal width and height, aligned top edges, but never forced down to the bottom navigation. This replaces fragile estimates based on a fixed number of rows. Keep the available viewport as an upper bound.
- **Current four-card group:** keep the existing full-remaining-screen 2×2 structure and the priority of actual numbers, not counts. Its equal quarter-screen cells need a full-height shared frosted surface.
- **Premium depth without heavy grey framing:** a consistent glass-*like* ivory-to-blush gradient on outer cards, restrained reflective one-pixel highlight and hairline wine border, soft shadow; wine-toned raised hero panels use gentle beveled gradient and highlight; secondary values use quiet translucent inset panes with significantly weaker elevation. Do not add real-time blur/shaders or heavy nesting that harms text contrast/performance.
- **A-06:** the missing 0–9 digit stays larger and visually primary; missing 00–99 pair stays next to it, and both own counts are small. Preserve full count labels across MM/EN without clipping or overflowing the fixed 2×2 cards.
- Confirm on the handset in all three tabs (MM and EN) before accepting the sample as the basis for production real-data integration. Automated compile/test is not visual certification. The demo remains artificial and never persists to data storage.

---

## Part XVI — Approved demo shell with real data (2026-11-09)

The user approved the 4–2–2 visual layout and explicitly authorized connecting verified history results. They also specified a **mandatory unavailable-state exact text** and reiterated that the previously corrected vocabulary must be retained.

- **Real results are the default**, using the original `TwoDAnalysisEngine.calculate` report and the exact same glass/3D result components from the accepted demo. The source of truth remains the selected date/session, verified history and relevant lookback/closed-day rules. Do not invent or save any actual 2D result.
- Keep the `DEMO` switch only as a **visibly marked, reversible test harness** until the real-data visual behavior is approved; demo fixtures stay isolated and nonpersistent.
- **No dash placeholders for unavailable output.** Show exactly **`အချက်အလက် မရရှိနိုင်သေးပါ`** in Burmese and **`Information not yet available`** in English. This applies to entire unavailable methods and unavailable independent subresults (including missing AM/PM/ALL stream data or absent A-08 condition data). The message must not be presented as a verified observed zero.
- Verified results with **a true numeric zero** are distinct from unknown/not enough history. Do not change engine calculations or silently transform a zero into a fabricated outcome. Where there are *no* missing digits in a verified complete set, use `ပျောက်ဂဏန်းမရှိ` instead of an empty placeholder.
- Respect the user's corrected terminology: **`ပျောက်ဂဏန်း` (0–9), `ပျောက်ဂဏန်းတွဲ` (00–99), `ညီအကို`** for display, and the approved compact home-card headings. The internal special-group key `ညီကို` from `TwoDigitGroups` remains untouched and is mapped only at display time. Localize parity labels through `twoDParityLabel`.
- Preserve independent AM/PM/ALL single-digit/pair streaks and `+` lower-bound metadata; the digit remains the primary raised result. A-02 must show maximum-frequency pair, digit, parity, special group, tens and ones, respecting ties. A-08 shows independently sourced both/first/second condition leaders. A-05/A-04 missing-digit sets wrap to four tokens per line to prevent clipping for actual full 0–9 populations.
- Keep **current draw = four equally sized 2×2 cards filling available height**. Keep the two weekly groups as **equal-width, equal-height, side-by-side pairs with content-measured height**, not stretched to the bottom nav. Maintain method detail navigation.
- Add unit regression tests for exact unavailable wording, maximum-frequency ties, missing-digit and missing-pair streak rankings. CI plus a real phone screenshot are required before calling final.

This supersedes earlier demo-default guidance in Part XV. Do not migrate or modify the financial ledger, live-feed job, commission, or transaction rules as part of this phase.

---

## Part XVII — Live results screenshot corrections (2026-11-09)

Approved main-screen 4–2–2 design is retained, but the first handset screenshots with real historical data revealed content-placement errors. Apply these rules without changing the historical computation engine, DB, source priority, Demo fixtures or detail-screen logic.

1. **A-01 equal-frequency companions:** The card must display the **actual top companion digits** as distinct raised chips, not just e.g. `3 မျိုးတူ`. Show up to three sorted, distinct leading digits with the common verified frequency on the row above. If additional digits tie, show `+N` for the hidden count; opening the Detail screen provides the full list. No invented winner, no loss of data in the engine.
2. **A-04 previous week:** The most observed 0–9 digit and its occurrence count must be **visually grouped in one inset result plate**. Do not leave a stray text-only “အကြိမ်ရေ” beside an already-counted hero. If tied, show other tied leaders in secondary text and preserve the same count.
3. **A-03 empty state:** Retain the exact unavailable message `အချက်အလက် မရရှိနိုင်သေးပါ` when evidence is insufficient, centered within the existing equally-sized current-draw card. A verified absence of matching patterns remains a different state (`တူညီသောမှတ်တမ်း မတွေ့ပါ`).
4. **A-08 partial evidence:** Present verified conditional results normally. **Combine multiple unavailable subconditions** in one quiet status panel listing their conditions and the exact unavailable message **once**; no repeat of the full sentence for each condition. Keep both historical 2D reference digits.
5. **History label:** In Burmese UI show **`မှတ်တမ်း`**, English remains **`History`**.
6. Preserve the approved user terminology `ပျောက်ဂဏန်း`, `ပျောက်ဂဏန်းတွဲ`, `ညီအကို`, and the localized morning/evening terminology. Keep primary digits more visually important than counts.
7. Only the real-result renderer, History label and directly related regression tests may change. The established layout, 3D/glass style, Demo mode, analytics engine and historical snapshots remain intact. Validate through CI plus handset screenshots.

---

## Part XVIII — Formula audit and available-data-only display rule (user confirmed 2026-11-09)

This section **supersedes conflicting earlier UI display requirements** in Parts XVI and XVII. The approved main 4–2–2 grid and existing analysis formulas remain unchanged unless the user separately confirms a formula change.

**Global display principle (A-01 through A-08, main cards and details):**
- Display every genuinely available, qualifying result. If only some subresults/AM-PM streams/reference searches have data, show **those subresults only**; omit unavailable rows, sections and explanations about missing subresults.
- Use the exact `အချက်အလက် မရရှိနိုင်သေးပါ` / `Information not yet available` text **only when the entire method card has no usable finding to display**. Do not repeatedly show it for missing subconditions while other values are present.
- Actual observed numeric zero can be displayed when it has a defined, verified interpretation. Unknown data must not be fabricated as zero. Do not use `-` or a `+` suffix to suggest missing records are additional observed absences.
- Keep provenance and incomplete-data metadata in engine outputs; omit decorative missing-record warnings in the compact analysis answer. Observed counts remain counts of actual stored results, not hypothetical absent draws.

**A-01 reaffirmation (user corrected assistant during 2026-11-09 audit):** A historical/source week does not require ten stored results. Use all present confirmed records to identify digits not seen in that recorded week and the first **observed** return result in the following week for the one-companion-per-episode ranking, even if earlier slots in that week have no record. Do not reject an available result merely because the source week is incomplete. Treat these as observed-history facts, not a guarantee that a missing source entry contains no digit. Older strict first-return-evidence language in Parts II/III/V is superseded to this extent; **do not change the A-01 formula to force complete weeks**.

**A-03 reaffirmation:** Only slot-aligned confirmed class sequences match. When a historical week has the matching prefix but no recorded next result, omit that history from the displayed next-outcome matches. Do not add a missing-next-result counter or message. Existing A-03 matching formula is acceptable.

**A-04/A-01:** Missing 0–9 digits obtained from available previous-week results remain usable when that week is incomplete. The display may accurately label them `unseen in recorded results`, without hiding valid digits behind an unavailable status.

**A-06:** AM, PM, and combined are independent. Display whichever streams have observed results; omit empty streams. Observed absence counts exclude unknown slots. Do not append a `+` symbol to imply that unknown slots extend a verified absence streak.

**A-08:** The first-reference-only and second-reference-only searches can produce usable historical results even if the other current reference is absent. Never hide those valid checks behind a two-reference requirement. Display any available condition results, omit conditions with no observed outcomes, and use whole-card unavailable only when neither references nor observed outcomes can be shown.

**Implementation boundary:** UI selection rules and corresponding regression tests only. Do not alter financial ledger, database, historical ingestion precedence, live timing, or unconfirmed future-result forecasting; do not rebuild the currently accepted main layout. Detail-screen visual redesign remains Phase 2.

---

## Part XIX — Eight analysis detail screens: implementation (2026-11-09)

All eight details follow **Information First / Progressive Disclosure / Smart Space Utilization**. The user's edited check titles/subtitles remain centralized in \`TwoDAnalysisText\`; the locale-specific **LedgerTypographyStandard** is required, using English Sans Serif and Burmese Noto Sans Myanmar UI with existing role-based sizes and line heights. Preserve \`မနက်အကြိမ် / ညနေအကြိမ်\`, \`ပျောက်ဂဏန်း\`, \`ပျောက်ဂဏန်းတွဲ\`, \`ညီအကို\`, and \`တူညီသောမှတ်တမ်း မတွေ့ပါ\`.

A-02 keeps the existing rich frequency dashboard; A-01, A-03, A-04, A-05, A-06, A-07 and A-08 get compact, result-first and evidence-on-demand detail views. Main 4–2–2 screen and its card routing are unchanged; the old duplicate primary finding and oversized non-A02 header are replaced by one compact approved-title header and the actual result-first detail panels. Existing formula engine, read-only history, closed dates, selected ranges and independent A-08 searches are untouched. Partial-data UI shows only available independent outcomes; no missing-subresult placeholders, false zero or \`+\` for unknown absence draws. Unit tests cover leader ties, independent streams and one-reference A-08.

---

## Part XX — Evidence-first detail audit correction (2026-11-09)

The user's final acceptance requirement is **complete traceable historical information**, not simply fewer detail cards. Details must show which 2D value(s) were checked, which target day/AM-PM occasion was under review, which outcomes were recorded, frequency totals and the exact corresponding prior and following historical date/session evidence.

- A-08: show source first and/or second reference **digits**, separate source conditions, all recorded following 2D results and frequency, and each matching episode's actual historical reference date/session/result followed by the actual target date/session/result. Do not infer unrecorded outcomes.
- A-02: keep the accepted two-column first view; enrich per-digit history (including double-digit two positional appearances) and parity/special group occurrence evidence. Display least-seen leaders with explicit multiplication separator to distinguish digits from counts.
- A-03: retain exact Monday-anchored parity formula, show current slot-specific prefix and each matching historical week's preceding slots with its recorded next 2D outcome. Skip unrecorded following outcomes.
- A-05: use engine's \`classificationVerified\` to qualify incomplete history, show newly observed/old digits per recorded result and remaining unseen digits after each result. The \`11\` double introduces only one new distinct digit.
- A-06: AM/PM/ALL are independent; compact first-view leaders, all recorded digit/pair streak rankings accessible by explicit detail action, with last-seen dates where available and historical absence limited to observed draws.
- A-07: the most frequent special groups are primary; each observed group has actual dates and digits. Repeated exact pair episodes show initial and each later occurrence.
- A-04: render the previous-week actual date range, top digit and missing digits first; expose digit/parity/special/repeated frequency histories from recorded results.
- A-01: display previous-week missing digits, actual current-week observed return, most common historical companion(s), and each historical missing-week-to-first-observed-return episode.
- The user's **Noto Sans Myanmar UI / English Sans Serif** approved typography and \`TwoDAnalysisText\` titles remain the source of truth. The main 4–2–2 overview and all eight formula arithmetic rules are unchanged.

---

## Part XXI — Screenshot-approved detail frame families (2026-11-09)

The user explicitly accepted A-06's AM/PM/ALL selector + compact scope summary + **two side-by-side highlighted primary-number cards** + directly visible detail lists as a frame reference. Key result digits must pop via a separate contrasting rounded inset, subtle elevation and depth (font size alone is insufficient). Long result histories remain on demand. A restrained un-elevated list-number badge must not compete with the primary result well.

**Frame assignments:** A-02, A-04, A-06, A-07 use the A-06 frame family adapted to their data; A-01 and A-08 use relationship/result variants; A-03 and A-05 use chronological evidence timelines. Do not force identical content card counts across methods. Preserve all engine calculations and read-only historical evidence. Compact first views must still show meaningful frequencies and evidence categories; collapsing an entire detail screen into empty disclosure drawers is rejected.

**A-02 explicit correction:** separate 2D pair frequency, 0–9 digit frequency, even/odd parity, and special number-group sections. Never bury parity/special groups inside 2D pair details. The digit Frequency table has combined/tens/ones selectors; tapping a digit must filter historical evidence to that chosen positional mode, with a double such as 55 contributing two combined digit appearances, one tens appearance, one ones appearance. Replace ambiguous \`ဂဏန်း အထွက်အနည်းဆုံး (ပေါ်ဖူး)\` with \`ထွက်ဖူးသောဂဏန်းများထဲမှ အနည်းဆုံး\` meaning the least frequent **among digits recorded at least once**, distinct from never-seen digits.

---

## Part XXII — Approved single-row category tabs and stable first render (2026-11-09)

The user clarified A-06 is a **frame and information-space reference**, not just card aesthetics. Keep the most important result(s) in fixed highlighted primary cards above a strongly discoverable, selectable category strip. Only the chosen category's complete relevant detail is shown underneath. This prevents repeated large panels and unnecessary scrolling without losing history evidence. Selected category has a distinct contrast fill, stroke, low shadow and underline. Number of categories determines number of equal-width cells **in a single row**: three categories = three cells, four = four, five = five. Never split 4 into 2×2 or 5 into two rows. Text can take two lines within its own cell where the approved font needs space. Keep the user's Burmese wording and English translation.

- A-02: fixed top pair/digit frequency highlight cards, four tabs **ဂဏန်းတွဲ / ဂဏန်း / အထူး / စုံ/မ**. Pair results only in pair tab, 0–9 with nested ပေါင်း/ထိပ်/ပိတ် plus least-seen-among-observed in digits tab, special groups and parity each isolated. Ties in top heroes remain selectable and evidence retains exact position counts.
- A-04: fixed previous-week period and top digit/unseen digit cards, four tabs **ဂဏန်း / စုံ/မ / အထူး / ပြန်ကျ**. Historical evidence available within each chosen category, including first/duplicate occurrence rows.
- A-07: fixed top observed special group result, five **အပူး / ညီအကို / ပါဝါ / နက္ခတ် / ပြန်ကျ** tabs in one row. Only the selected group's measured count, actual numbers and full historical source results appear beneath.
- A-08 (latest user correction): render **exactly three persistent tabs** in this order: **ALL / first actual reference digit / second actual reference digit** (e.g. ALL / 67 / 74). Default to ALL. Do not hide any tab because a historical result is absent. ALL means a simultaneous two-reference historical condition, NOT a union of single-reference outcomes. An unmatched selected condition shows only the standard empty state below the tabs; recorded outcomes show their accurate dates, sessions, frequencies and source→following evidence.
- A-01: previous week's unseen-digit choice retained; fixed selected digit's strongest observed companion when available; available companion/observed return/history categories as one-row tabs. Unknown outcomes remain absent.
- A-06: preserve the existing approved AM/PM/ALL selector, scope summary and two result cards; do not impose a redundant category tab.
- A-03 and A-05: chronological timeline remains primary, no artificial category stripping.
- Initial 2D Analysis navigation: distinguish **unemitted** Room streams (null) from truly **empty observed** streams (empty list). Delay full analysis grid rendering until every required data source emits its initial snapshot; during this brief phase use a small progress indicator in the existing frame, never an incorrect empty grid. Default target date/session is derived immediately; do not asynchronously reset it after the first composable frame. Later database emissions still update the result normally.

The entire 4–2–2 overview, the eight approved formulas, the user-provided method titles, font standard, and live-result source precedence are unchanged.

## Part XXIII — Latest A-05 hierarchy (2026-11-09)

The user approves the A-05 frame and keeping the weekly **remaining unseen digits** at the top. The detailed chronological flow must visually prioritize the **new/old class**, NOT the raw 2D value. There are exactly three approved, position-independent classes: **အသစ် + အသစ်**, **အသစ် + အဟောင်း**, **အဟောင်း + အဟောင်း**. A mixed outcome such as historical 7 (old) and 4 (new) in pair 74 belongs to **အသစ် + အဟောင်း**, with the individual new digit 4 and old digit 7 named accurately. Use prominent separated semantic status surfaces, smaller original outcome/date/session context and secondary remaining-digits information. If earlier known data is incomplete, honor classificationVerified: show the recorded result but do not assert a verified new/old pattern. Preserve the one-distinct-new-digit semantics of doubles such as 11.



---

# Part XXIV — Research Workspace V1 implementation initiated (2026-11-11)

The user entrusted implementation/design leadership of Research to the assistant and
explicitly authorized starting work. Research remains **a separate user-directed
feature**, not A-09, not the automatic eight checks and not the unchanged Rules
destination. This is a **work-in-progress implementation note**, not a claim
that the APK has passed CI or phone acceptance.

Detailed first-version contract and tests:
[`docs/2D-RESEARCH-WORKSPACE-SPEC.md`](2D-RESEARCH-WORKSPACE-SPEC.md).

**V1 scope:** exact 2D pair (00–99) source; AM, PM or ALL scopes for
next-draw searches; same-day AM→PM; optional exact following pair or
following-pair discovery; all/week/month/custom evidence ranges; counts and
dated evidence for observed, matching, other, missing, pending, and
range-boundary outcomes. A reverse-pair shortcut fills the chosen exact
target; it is not a new probability formula.

**Work branch:** `feature/2d-research-workspace`, based on
`feature/2d-analysis-timeline-redesign`. Research has its own read-only
domain engine, independent guided Compose screen and unit tests; the
bottom Research tab is wired into the new screen. The existing
`Historic2DDataSource.resolve` is reused for verified reconciliation,
manual overrides, deletion locks and final-result time boundaries.

**Acceptance not granted yet:** CI/unit tests, lint/APK verification, real
historical-result audit and handset MM/EN UI inspection are all required.
Do not merge to main or describe the feature as released until those gates
are satisfied. Later candidate research operators (tens/ones, groups,
multi-draw sequences, weekly conditions, AND/OR and saving queries) remain
unapproved implementation extensions, not confirmed V1 features.

**Earlier confirmed constraints remain in force:** no changes to
A-01–A-08 business formulas, Summary contract, ledger, LIVE or Rules.
