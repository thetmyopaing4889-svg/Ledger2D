# Cherry's 2D Ledger — 2D Analysis Master Specification

> **Status:** Requirements collection / redesign planning; implementation **NOT approved**.  
> **Version:** 0.1 — 2026-11-06  
> **Repository branch at creation:** `experiment/font-typography-lab-build930`  
> **Baseline code reviewed:** Build #963, commit `522a37c8637ad847677931756bcdef916693f343` (CI success).  
> **Purpose:** Transfer the complete *why, what, definitions, examples, open questions and decisions* across conversations and eventually into implementation tests. **This document is a working source of truth, not permission to change app code.**

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

**State:** Core intent and stages confirmed; holiday/incomplete-history handling and history horizon pending.  
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

### A-01.4 Worked example — illustrative, NOT actual source data

- Prior week: 10 confirmed results; digit **0** appears in no tens or ones position → **"0 ပျောက်"**.
- Current week: Wed AM confirmed result is **40** and this is the first appearance of 0 → returned Wed AM in the **ones** position.
- History may contain other absent-0 weeks; inspect the immediately subsequent week for the first reappearance and whether it happened at all.

**Negative example:** If prior week's result includes **70** even once, 0 was **not missing**, even if no result had tens digit 0.

### A-01.5 Pending; do not invent

- How to assess a shortened week due to true exchange holidays versus incomplete records, both for the previous week and the history population.
- Historical lookback period (all available, 1/3/6 months, or user-selectable).
- Whether a historically missing digit should still be compared when its following week is partially recorded, and how prominently sample sizes should be displayed.
- Date/session evidence for restored/imported results when availability is uncertain.

---

## A-02 — Same weekday + same morning/evening: frequency/class comparisons

**State:** Five checks and *exact matching day/session scope* confirmed; horizon and counting details pending.  
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
- Counting a double such as 55 as one draw containing 5 versus two digit positions is **pending**. Do not silently pick.

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

- History horizon / period choice.
- Should the target draw be excluded when already completed? The user framed the check for the current target session; no look-ahead must be introduced.
- For digit frequency, does 55 count once for digit 5 (draws containing it), or twice (digit positions)?
- Does least frequent include digits at zero occurrences?
- Exact boundaries of all special groups; treatment of overlap.
- Handling an empty/small matched sample and available-data vs full-schedule distinction.

---

## A-03 — Strict chronological weekly even/odd-pattern matching

**State:** Core order, minimum count, dynamic length, week boundary and no-match behavior confirmed. Holiday alignment and incomplete-history rules pending.  
**Scope:** Current Monday–Friday week's **confirmed chronological four-class sequence** BEFORE the target session, matched to historical within-week sequences, then inspect each match's **next result**.

### A-03.1 User's question

At the current target session, take already finished draws this week, convert each to **စုံစုံ / မမ / စုံမ / မစုံ**, and check whether history contains a week whose **ordered sequence is exactly the same**. If yes, what class came **immediately after** that sequence in the historical week? If no history match, **do not display that check/result**.

### A-03.2 Conditions confirmed by the user

1. **Only within a Monday–Friday week**. Never splice Fri PM of one week into Mon AM of the next.
2. **At least four confirmed draws must already exist** before the target session. Earlier than four, this check is not eligible.
3. **Order is mandatory:** equal category totals in a different order are **NOT** a match.
4. The observed sequence grows as new draws complete this week. It is **not fixed at four**; e.g., Wed AM=4 prior draws, Wed PM=5 prior draws, continuing through Fri PM=9 prior draws in an ordinary fully open week.
5. If exchange/market closure prevents Monday or Tuesday draws, **wait until four real results have been collected** within the week. Do not infer four draws just because it is Wednesday.
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

These values assume **all earlier scheduled sessions really occurred**. With holidays, calculate from actual confirmed results, not the calendar cell number.

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

### A-03.6 Holiday and historical alignment — unresolved and important

The user confirmed: **a holiday does not reset the required four observations; the first eligible target moves later in the same week**.

The user has **not** yet decided whether, for historical matches when calendars differ:
- match **the ordered sequence of real draws only**, ignoring which weekday/session slot was closed, OR
- require **the identical weekday and AM/PM slot positions as well as class order**.

Likewise, unconfirmed:
- whether a missing record that should exist makes a historical week unusable rather than compressing the sequence;
- whether matching is always anchored at the first actual draw of the historical week (rather than a four-draw window starting midweek); user examples imply week-start comparison, **but holiday alignment must be resolved before coding**;
- history horizon and whether different market holiday schedules qualify.

**Do not silently define any of the above.** These choices materially change match counts and next-draw outcomes.

### A-03.7 Presentation intent (not yet designed)

The user wants **a practical analysis of the current target session**, based on matching *real* weekly sequences, not an attractive but unrelated historical chart. If no matches, the requested behavior is **omit the result**; broader UI empty-state choices are not approved yet.

---

## A-04 — Previous week's completed draw distribution and remaining digits

**State:** Core check categories and the precise **same-week exact-2D recurrence** meaning of **ပြန်ကျ** are **confirmed by user**; three-or-more recurrence presentation, counting method and other categories remain pending. **Do not code yet.**  
**Scope:** The **entire previous Monday–Friday week**, including its completed AM and PM draws that have verified final results. **Do not confuse with A-02**, which filters to *the same weekday and same session* as the current target. **Do not confuse with A-01**, which continues from previous-week missing single digits into *current-week reappearance* and historical next-week behavior.

### A-04.1 User's actual request (captured from conversation)

> "ပြီးခဲ့အပတ်ရဲ့ ဂဏန်းထွက်ရှိမှုအခြေအနေ 0 to 9 အကြိမ်ရေ, စုံစုံ မမ စုံမ မစုံ အကြိမ်ရေ, ပါဝါ ညီကို အပူး နက္ခတ်.ပြန်ကျ....ထွက်ရှိမှု, ကျန်ဂဏန်း"

The user explicitly **confirmed ပြန်ကျ** as **the exact same two-digit number appearing at least twice within one Monday–Friday week, regardless of which days or AM/PM sessions**: e.g. Monday **01**, Tuesday **01** or Monday AM **01**, Monday PM **01**. It does **not** require consecutive draw occasions. **"ကျန်ဂဏန်း"** is still undefined. "ညီကို" may be the user's shorter term for an existing "ညီအကို" group; verify the synonym before coding.

### A-04.2 Components explicitly requested by user

1. **0–9 digit appearance counts for the previous week.** Examine the week's 2D results and report occurrence statistics for individual digits 0 through 9. This concerns single digits, not the 00–99 2D pair frequency. The counting policy for a result such as `55` (one draw containing 5 or two digit-position occurrences) **remains pending**.
2. **Four even/odd class counts for the previous week:** စုံစုံ, မမ, စုံမ, မစုံ — how often each category appeared in the week's draws. Validate the exact classification orientation and rules before implementing.
3. **Special number-group appearances for the previous week:** ပါဝါ, ညီကို/ညီအကို, အပူး, နက္ခတ် and **ပြန်ကျ**. Count/report how each appeared. **ပြန်ကျ (confirmed core meaning):** repeat occurrence of an **identical 2D pair**, as with **01 on Monday, then 01 again on Tuesday**. This is different from a single 0 or 1 appearing in another pair; it concerns identical complete 2D values. The two occurrences need not be consecutive in the draw sequence and can take place on the same day or different days. A reversed pair like 01 and 10 is not identical, so it is not an exact-number recurrence. The exact membership definitions for the other named special groups remain pending; inspect existing verified app definitions and confirm with user. Do not assume the special groups are mutually exclusive.
4. **ကျန်ဂဏန်း (remaining numbers):** user wants a separate remaining-number component of the previous-week report. **Ambiguous scope:** Is this the 0–9 digits that did not appear, the 00–99 two-digit numbers that did not appear, or another locally defined "remaining" concept? **Keep unimplemented and pending until user clarifies.**

### A-04.2a Worked example of ပြန်ကျ — user supplied

- **Monday draw: 01**
- **Tuesday draw: 01**  
  The exact same **01** has appeared again in the same reporting week; this qualifies as **"ပြန်ကျ"**.
- This is a **two-digit identity** check. A recurrence of digit 0 or 1 by itself is **not** sufficient to claim that the full 01 result repeated.
- **User-confirmed: same day counts.** Monday AM **01** → Monday PM **01** = **ပြန်ကျ**.
- **User-confirmed: any day/occasion within the same week counts.** Monday **01** → Thursday **01**, with other intervening results, also qualifies.
- **User-confirmed: no cross-week merging.** Previous-week summary evaluates that single Monday–Friday week; repeating the number in a different week is not a recurrence *within that weekly summary*.
- **Logical exclusion of non-identical pairs:** **01** followed by **10** is not the same exact two-digit number and is not "ပြန်ကျ" under this definition.
- **Still pending:** How to present/count **three or more occurrences** of the same number (e.g. 01 on Monday, Wednesday and Friday): count repeat events, number of distinct repeated pairs, or both? Do not invent an aggregation formula.

**Negative distinction (logical, not a separate agreed formula):** Monday **01**, Tuesday **02** share the single digit 0 but **not** the identical two-digit number 01; this is not the demonstrated ပြန်ကျ case.

### A-04.3 Important distinction in business purpose

- **A-04:** "What exactly occurred / was absent during the completed **previous week**?" — a standalone structured, useful previous-week state review.
- **A-01:** "What **single digit was missing last week**; did it return **this week**; and how did it behave after historically missing weeks?" — an inter-week follow-up/history comparison.
- **A-02:** "Among historical draws matching the **current target's weekday AND morning/evening**, what numbers/digits/classes/groups occurred most?" — a different history population.
- **A-03:** "Does the **chronological four-class pattern** of this week match an ordered historical week prefix, and what came after?" — a strict pattern search.

Never combine A-04 totals with A-02 same-session counts, and never automatically treat "remaining" as a missing digit until confirmed.

### A-04.4 Data safety and suggested verification cases (NOT yet approved formulas)

- Explicitly identify the Monday–Friday date range and how many *verified final draws* exist in that range, without assuming 10 draws when holidays occur.
- Distinguish a real closure from an unknown/missing source record. Incomplete history cannot prove a digit was absent.
- Preserve ties; avoid visually inflating one of equally frequent categories.
- Example for scope only: if last week's verified results include 14, 26, 35, all three results belong to **last week's** data regardless of which weekdays produced them. **No frequency or group classification formula is implied by this example.**
- Displaying what happened last week does not establish what will happen this week.

### A-04.5 Specific open questions for user

- **Q-13 (mostly resolved):** **ပြန်ကျ = identical full 2D value occurs at least twice in the same week** (any days, either AM/PM, not necessarily adjacent). 01→10 does not qualify as an identical pair. **Pending only:** how to present/count a pair appearing 3+ times.
- **Q-14:** What are **"ကျန်ဂဏန်း"** here: remaining 0–9 digits, remaining 00–99 pairs, or something else?
- **Q-15:** In 0–9 frequency, does `55` count digit 5 once (one draw) or twice (two positions)? Should tens and ones also be shown separately?
- **Q-16:** Confirm definitions/overlap of the four parity classes and five named special groups, including whether **"ညီကို"** equals **"ညီအကို"**.
- **Q-17:** Which incomplete/holiday weeks are eligible for last-week review, and how should missing records be represented?
- **Q-18:** Whether the default view should show all group counts, only groups that appeared, and where/when to show drill-down — **UI not decided yet**.

**Feature assignment:** undecided. **Code change:** none. **Next action:** clarify the remaining edge cases of `ပြန်ကျ`, the meaning of `ကျန်ဂဏန်း`, and other special-set definitions; do not implement unconfirmed rules.

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

**State:** User provided the **six requested output categories**. The time-gap measurement, data horizon, handling never-seen values and exact eligibility are **pending**, so these must NOT be silently assumed in code.  
**Scope:** Historical confirmed 2D draw records, using three **separate observation streams**: (1) morning-only, (2) evening-only, (3) combined morning+evening chronological results. This is distinct from A-01 previous-week missing digits and A-04 previous-week category summary.

### A-06.1 User's explicit instruction

> "စစ်ဆေးနည်း၆ မနက်ပိုင်းသီးသန့် ပျောက်တာအကြာဆုံး ဂဏန်းတွဲ (ဥပမာ ၅၆) နဲ့ ပျောက်တာအကြာဆုံးဂဏန်း(ဥပမာ 0) ညနေသီးသန့် ပျောက်ဂဏန်းတွဲနဲ့ ဂဏန်း ပြီးတော့ နှစ်ကြိမ်လုံး ပျောက်ဂဏန်းတွဲနဲ့ ဂဏန်း"

Interpret this as **three independent missing-duration examinations**, **each** covering both complete 2D values 00–99 and single digits 0–9.

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
- **"ပျောက်တာအကြာဆုံး"** is about **time/observations since last occurrence**, not necessarily the result with the lowest overall frequency. A number can have appeared many times historically but still have the longest current absence.
- Morning-only results should not reset morning-only absence due to an evening appearance; evening-only similarly should not be reset by a morning appearance. A **combined** stream can be reset by an appearance in either.
- **Not a prediction**: longest absence alone does not establish a higher future chance.

### A-06.4 Illustrative edge scenarios (not factual history)

**Example 1 — morning vs evening distinction:** If **56** last appeared in a morning draw many mornings ago but appeared yesterday evening, the **morning-only** "last seen" remains the old morning occasion, while **combined** "last seen" is yesterday evening. Do not merge streams.

**Example 2 — pair vs digit:** If 56 has not appeared recently but 59 has, digit 5 has appeared while complete pair 56 has not. They must have separate absence calculations.

**Example 3 — ties:** If several candidate values have equally long absence by the approved metric, the method must not arbitrarily highlight only one without revealing the tie.

### A-06.5 Required data/metric decisions (NOT confirmed yet)

1. **Q-24 — Absence unit:** Does "ပျောက်တာအကြာဆုံး" mean **number of completed relevant draw occasions since last seen** (morning observations for AM, evening for PM, both for combined), **calendar days**, or another Myanmar 2D convention? The ranking can differ, especially with holidays and two draws per day.
2. **Q-25 — Lookback boundary:** Should this be calculated over **all historical records available**, a specified number of months, or a user-selectable date range? What if a digit/pair has **never** been seen in available history?
3. **Q-26 — Starting point / target session:** For the current target session, does analysis use only **previously completed results** (recommended for avoiding look-ahead) and when should a latest-result update refresh the ranking? Need explicit target timing.
4. **Q-27 — Data sufficiency:** What if the source contains missing records, true holidays or import gaps? Do not count an unavailable draw as a verified nonappearance; preserve any inability to know the true absence length.
5. **Q-28 — Desired user answer:** Should the result expose only the single longest candidate, **all ties**, a top N shortlist, last-seen date/time and the exact number of intervening relevant draws, or a full sortable table? UI grouping remains undecided.
6. **Q-29 — Historic observed vs all-time:** Does the user mean the **longest absence ongoing now**, or the **longest historical absence streak ever recorded**? The wording sounds like ongoing absence, but this distinction affects the entire algorithm and has **not yet been explicitly confirmed**.

### A-06.6 Relationship to other approved methods

- **A-01:** Detect **digits absent for an entire previous calendar week** and whether they returned next week; week-to-week History comparison.
- **A-04:** Report the previous week's frequencies and groups, including its remaining-number definition (still pending).
- **A-05:** Track **new/old digits and remaining 0–9 digits within the CURRENT week**, activated after the first draw of that week.
- **A-06:** Evaluate **duration of absence** for both full 2D pairs and single digits over **morning-only, evening-only, both** observation streams. No default week-only limitation or absence metric is approved yet.

**Feature assignment:** undecided; do not add six separate screens simply because there are six questions.  
**Code status:** not implemented or approved.  
**Next action:** Confirm the time/observation unit and whether the user means the longest absence **as of now** or the longest historical absence **ever** before finalizing calculations.

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

# Part V — Pending decisions index

| ID | Topic | Needed clarification |
|---|---|---|
| Q-01 | A-01 holiday week | What counts as a sufficiently complete week when true holidays remove sessions? |
| Q-02 | A-01 history | Full lookback, denominator, handling partial following weeks |
| Q-03 | A-02 horizon | Month(s), all available or selectable period |
| Q-04 | A-02 digit tally | For 55, count digit 5 once per result or twice by position? |
| Q-05 | A-02 minimum | Is 0 occurrence included when showing 'least'? |
| Q-06 | A-02 groups | User-approved exact mapping of doubles/power/nakhat/siblings and overlap rules |
| Q-07 | A-03 holidays | Must historical weekday/session slot positions match, or only actual result class order? |
| Q-08 | A-03 missing records | Skip weeks with missing results or treat absent slots as legitimate closures only with proof? |
| Q-09 | A-03 sequence location | Historic prefix anchored at first real weekly draw versus any contiguous intraweek substring |
| Q-10 | A-03 history horizon | Number of historical weeks and sufficient follow-up |
| Q-11 | Design | Final feature grouping, default first-view layout, evidence/drill-down and true elevation system |
| Q-12 | Spec workflow | User to teach remaining checks before feature organization |
| Q-13 | A-04 ပြန်ကျ | Confirmed: exact 2D recurrence anywhere within one week, same day or different day; pending 3+ occurrence count/presentation |
| Q-14 | A-04 ကျန်ဂဏန်း | Remaining 0–9 digits vs 00–99 pairs vs another definition |
| Q-15 | A-04 digit counts | Count 55 once or twice; whether to split tens/ones |
| Q-16 | A-04 classifications | Exact class/group mapping, overlaps and ညီကို/ညီအကို synonym |
| Q-17 | A-04 data completeness | Which holiday/partially recorded weeks qualify |
| Q-18 | A-04 presentation | Default groups and drill-down after all methods collected |
| Q-19 | A-05 repeated digit within one draw | RESOLVED: first 11 = one new digit, remaining 234567890; optional wording for later already-seen doubles |
| Q-20 | A-05 tens/ones order | New+old and old+new separate versus aggregated |
| Q-21 | A-05 history scope | Current-week descriptive flow only or any user-approved history matching |
| Q-22 | A-05 presentation | Remaining current-week digits REQUIRED; final layout for draw/day flow and remaining set pending |
| Q-23 | A-05 completeness | Closure vs missing records and eligibility |
| Q-24 | A-06 absence unit | Relevant draw occasions vs calendar days vs another measure |
| Q-25 | A-06 history horizon | All available vs selected interval; never-seen candidates |
| Q-26 | A-06 target timing | Results eligible before target occasion, refresh behavior |
| Q-27 | A-06 completeness | Holidays and absent/imported records vs verified nonappearance |
| Q-28 | A-06 presentation | All ties/top N, last-seen date, duration details and drill-down |
| Q-29 | A-06 meaning of longest | Longest currently ongoing absence vs longest historic streak ever |

Do not mistake the table for a user-approved implementation plan.

---

# Part VI — UI/UX constraints after method collection

- **Do not redesign screens yet.** Start with valuable user-facing answers and the evidence behind them, then decide if they should live in one feature or multiple.
- **First view must prioritize the current target occasion's relevant checks**, not static counts, generic 00–99 grids, giant empty headers or accidental rankings.
- Missing-number analysis is important; show meaningful context (week, previous-week absence, current-week return, historical comparison), not just an overwhelming 0× list.
- Modern/premium, clear foreground/background layering and perceptible card elevation (including selected nested cards); coherent light/highlight and shadow, not only thin borders.
- Compact, readable Burmese and English parity; user-facing term မနက်အကြိမ် / ညနေအကြိမ်.
- Every emphasized statistic should have a **reason for being emphasized**; ties must not be randomly ranked.
- Display actual data coverage and dates to prevent a user from mistaking incomplete historical samples for full records.

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
- **Feature assignment: undecided until all methods collected**
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
| 2026-11-06 | A-06 longest-absent 2D pair and 0–9 digit, each analyzed separately for AM-only, PM-only and both | User specified six checks; absence unit/horizon and current-vs-ever interpretation pending |

---

**Next action:** Continue listening to the user's further analysis methods. Update this document with full detail and explicit confirmed/pending states after each method is clarified. **Do not begin redesign implementation or assume feature assignments.**
