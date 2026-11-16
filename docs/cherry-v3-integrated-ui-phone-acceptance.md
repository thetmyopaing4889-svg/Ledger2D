# Cherry V3 — Integrated Math UI Phone Acceptance

**Implementation branch:** `feature/cherry-discovery-v2-from-build1641` (do not merge to `main` without approval).  
**Product decision:** User requested **one integrated APK**, not an isolated diagnostic screen or APK.

## Placement and protection

- Existing `BETTER DATA → 🧮 သင်္ချာနည်း` keeps its **selection overview → existing Champion → LONE/WAM/BREAK cards → Gemini 210 variants** in that exact order.
- `CherryV3DiscoveryPanel` is appended **under Gemini 210**, replacing the old finite 828-slot workflow as the default visible Formula Search. The pre-existing finite search, original formula selection/lock and prospective trial records are **not deleted**; accessible through **ယခင် Formula မှတ်တမ်း ကြည့်ရန်**.
- No changes to Research, Rules Library, DOC/Gemini formula calculations, Champion selection, Analysis, ledger or `main`.
- The V3 controller is remembered **at Better Data screen level**: scrolling the lazy Math list does not kill a running search. Leaving Better Data or changing the draw target cancels safely and persists completed formulas.
- New search is **never** performed by service construction, Math tab opening or saved-state inspection. It starts only after the explicit **ရှာဖွေမည် / ဆက်ရှာမည်** tap.

## What to check on the actual phone

1. Open Welcome, Home, Better Data, and Math repeatedly **without tapping Search**; no unwanted slowdown or V3 scan should occur.
2. At the **bottom of Math**, select **လုံးပိုင်**, tap **ရှာဖွေမည်**, check that history preparation is separately displayed, then the REAL elapsed 0–30-second bar advances smoothly. Navigate between existing Math cards while running; they remain usable.
3. Confirm the search stops once the engine's 30-second computation budget is reached, displays number of expressions completed, distinct/duplicate behaviors and at most **eight** first-found historical streak tiers (**3–9 and 10+**). A historical fit is **not** a winning forecast.
4. Tap **ဆက်ရှာမည်**; the persisted cursor advances instead of rescanning the first formula, and no earlier first-found tier is replaced. Repeat for **ဝမ်းချိန်း** and **ဘရိတ်**.
5. Start again, tap **ရပ်မည်** halfway, exit/reopen Better Data, return to Math and select that same category; the completed-work checkpoint should restore, then Continue advances. Cancel must not leave a background worker.
6. Open **ယခင် Formula မှတ်တမ်း ကြည့်ရန်**; verify existing old 828-era saved trials/locks remain accessible (V3 never overwrites the old `..._v2` preferences).
7. Watch responsiveness, battery/heat and RAM in an actual device; CI only verifies Kotlin/JVM and APK construction, **not real phone temperature/latency**. Report device model/Android version and any reproducible jank; do not claim those gates passed without a physical test.

## App behavior and limits

- UI timer begins **after** bounded Room loading, history normalization and persisted cursor restore; it does **not** misleadingly include the data-preparation phase in the 30-second computation budget.
- Progress is a **real elapsed-time percentage**, not a false percentage of the infinite grammar. V3 scans lazily beyond legacy 207 with only lag-1..3 inputs in the 203-slot Master policy.
- Category results are **historical digit evaluations**; V3 never silently expands them into 2D betting pairs, promotes a Champion, or changes existing mathematics. The candidate shown as `ဦးစားပေးရွေးကွက်` means longest *historical* consecutive fit in the selected mode.
- The original V2 progress remains in `cherry_v2_discovery_cursors_v2` and V3 progress lives in `cherry_v2_discovery_cursors_v3`. Old incompatible cursor positions cannot be interpreted as V3 syntax.

**Release gate:** exact-head GitHub Action must show success for **unit tests, lint, debug/release APK and uploads**. Only after that is the integrated APK ready for the user to install and personally test. No claim of a prospective prediction edge is made.
