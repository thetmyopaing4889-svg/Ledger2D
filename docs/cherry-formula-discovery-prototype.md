# Cherry 2D — Results-first Formula Discovery Prototype

Status: **experimental domain-only prototype; not connected to the Better Data UI or prediction shortlist**.

## User-approved intent

- Retain the existing eight DOC formulas and 210 Gemini research variants unchanged.
- Discover new historical numerical relationships afresh for each target AM/PM session.
- Search separately for **LONE (လုံးပိုင်)**, **WAM (ဝမ်းချိန်း)**, and **BREAK (ဘရိတ်)**.
- Start considering relationships after at least **three consecutive completed draw matches**, with **ten draws as a search target**, not a guaranteed result.
- Keep only the first discovered formula at each exact streak level, separately per kind, with a 10+ bucket that retains the longest actual streak.
- Let the user run discovery only on demand and search successive unused grammar stages; retain the earlier best result.
- Do not promote experimental fits automatically into production; present original and experimental records separately until evidence and UI rules are reviewed.

## Why symbolic formula search, not a count of 300 prewritten formulas?

Small *expression grammars* and data-derived numeric offsets can reveal relationships without requiring a fixed number of formula outputs. This is an **enumerative symbolic regression / program synthesis prototype**; it is not AI and not an unrestricted mathematical law discovery engine.

Stages, in deterministic order:

1. Single lagged top/tail/break feature (up to three completed previous draw slots).
2. Pairwise addition, subtraction in each direction, and absolute difference (up to two slots).
3. Three distinct lagged top/tail features added and subtracted in several orders.
4. Nine's complement, products, and simple weighted compositions across three slots.

After evaluating a base expression on the newest completed draw, the engine derives the fixed offset from that **observed** draw's LONE/WAM/BREAK target. It then checks the exact *same expression and offset* backwards until the first miss, without changing coefficients between checked draws. New candidates must have at least three matches to be retained.

The historical target value is defined by the existing code's category-specific rule:

- LONE: the computed single digit must appear in either position.
- WAM: computed digit or its mod-10 +5 power appears in either position.
- BREAK: sum of the two actual digits modulo ten equals computed digit.

These are **not equivalent to exact 2D pair hits**, and have different chance-match rates. Do not present longer streaks across categories as comparable prediction quality.

## Important statistical distinction

The prototype's matches are **historical fits**: formulas were chosen using already observed outcomes. They must never be labeled as pre-registered winning predictions or as guaranteed future results.

Before user-facing promotion, build an **independent walk-forward evaluator**: replay each past target, discover using only pre-target history, log the locked formula/output before revealing the target result, then evaluate that result. Compare the results with chance baselines and untouched existing formulas. The in-sample longest streak does not provide an unbiased forecast validation.

## Runtime and lifecycle

- Pure Kotlin implementation: CherryFormulaDiscoveryEngine.kt.
- One grammar level per nextRound call; the returned state carries progress and retained representatives.
- Progress derives from actual completed grammar stages, rather than simulated percent.
- Incomplete immediate prior draw blocks a stage; the engine does not bridge missing AM/PM slots.
- History fingerprint and target identity invalidate stale discovery state.
- All levels are finite; pressing "Continue" after final level returns the same result.
- For future UI integration, run each round via injected Dispatchers.Default on an owned coroutine scope, support cancellation, cache results for a session, and show user-confirmed selections separately from raw discovered fits.
- The prototype has no background tasks, no DB writes, no network, and no UI changes.

## Online technical references used

- PySR operator and expression-space configuration: https://pysr.ai/v2.5.2/api.html
- Symbolic regression and model selection with minimum description length (2026): https://doi.org/10.1098/rsta.2024.0584
- Android coroutines for main-safe CPU work: https://developer.android.com/kotlin/coroutines/coroutines-adv
- Android coroutine best practices (inject dispatchers): https://developer.android.com/kotlin/coroutines/coroutines-best-practices

This prototype deliberately takes inspiration from these approaches rather than embedding PySR/Julia in the Android app.
