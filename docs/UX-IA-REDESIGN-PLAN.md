# Ledger2D UX / Information Architecture Redesign Plan

## Purpose

This document defines the proposed user experience and navigation architecture for Ledger2D before any further presentation-layer implementation. The goal is not to add more shortcuts to Home. The goal is to make the application understandable and efficient for a Myanmar 2D seller while preserving the existing Room data, domain rules, calculations, and saved records.

The redesign therefore follows this rule:

> **Keep the business system intact; reorganize the user workspace around real user intent, scope, frequency, and context.**

No database schema, parser, bet expansion, limit validation, closed-number validation, commission, payout, report calculation, winning-number logic, or stored data should be changed as part of this UI redesign.

## 1. Current Application Map

### Current top-level flow

```text
App start
└── Home / Today workspace
    ├── Quick Entry
    ├── Today Ledger
    ├── Settlement
    ├── Agent management
    ├── Winning Number
    ├── Closed Day
    └── Settings
```

The current Home screen presents a status summary, a primary Quick Entry action, several action cards, management shortcuts, and a bottom navigation bar. The same or closely related destinations are therefore exposed both as Home shortcuts and as bottom-navigation destinations.

### Current Agent flow

```text
Home
└── Agent list
    ├── Add Agent
    ├── Edit Agent
    └── Select Agent
        └── Agent Detail
            ├── Customer list
            ├── Total list
            ├── Agent report
            ├── Closed numbers
            ├── Winning numbers
            ├── Agent limits
            └── Format guide
```

The Agent Detail screen currently acts as both an identity/profile screen and a large action hub. This creates a hierarchy problem: daily work, reporting, configuration, and help content appear to have equal priority.

### Current Customer flow

```text
Agent Detail
└── Customer list
    ├── Add Customer
    ├── Edit Customer
    └── Select Customer
        └── Customer Detail
            ├── Betting entry
            ├── Bet history
            ├── Analysis
            ├── Customer report
            ├── Customer limit
            ├── Commission
            ├── Digit list
            └── Customer winning view
```

Customer Detail is useful for preserving customer context, but it currently exposes frequent operational actions and occasional configuration/reporting actions together.

### Current betting flow

```text
Home or Customer Detail
└── Betting entry
    ├── Choose date
    ├── Choose morning/evening
    ├── Enter source text
    ├── Choose format
    ├── View expansion and validation preview
    ├── Confirm
    └── Return after submission
```

This is the highest-frequency workflow. Agent, customer, date, and draw context must remain visible throughout the screen.

### Current ledger, report, winning, and settlement areas

```text
Ledger
├── Date
├── Morning/evening
└── Agent/customer totals

Reports
├── Agent report
├── Customer report
├── Daily Before/After
└── Weekly Before/After

Winning
├── Global winning entry/history
├── Agent-scoped winning view
└── Customer-scoped winning view

Settlement
└── Date + draw + Agent settlement
```

These areas should be treated as workspaces with explicit scope selectors rather than as unrelated screens hidden behind many nested action cards.

## 2. Problems in the Current Experience

| Current issue | User impact | Design consequence |
|---|---|---|
| Home exposes many shortcuts and a bottom bar at the same time | The user cannot tell which actions are primary | Home should be a work dashboard, not a feature directory |
| Agent Detail is a large menu hub | Profile, daily work, configuration, reporting, and help compete visually | Separate identity/context from workspaces and configuration |
| Similar destinations are reachable from multiple places | The user may not know which route is canonical | Each major task needs one canonical entry point |
| Agent-specific and global features are mixed | The user can lose track of which Agent a result belongs to | Every scoped workspace needs a visible scope header |
| Frequent actions and occasional configuration are mixed | Daily operation becomes slower | Separate operational work from setup/configuration |
| Home, Ledger, and Settlement can overlap conceptually | The user may see duplicate totals or duplicate entry points | Define one responsibility for each destination |
| A detail screen can become a button collection | More buttons do not equal easier management | Use progressive disclosure and contextual selectors |

## 3. Design Principles

### 3.1 Home is a work dashboard, not a menu

Home should answer three questions immediately:

1. What is the state of today?
2. What should I do next?
3. What important issue needs attention?

Home should not display every feature in the application.

### 3.2 One canonical entry point per task

A user should not have to decide whether to enter a task from Home, a bottom tab, an Agent Detail card, or a Customer Detail card. Contextual entry points may exist for speed, but they must resolve to the same canonical workspace and must not create parallel navigation structures.

### 3.3 Scope must always be visible

A workspace must clearly show whether it is operating on:

- Global data
- All Agents
- One Agent
- One Customer

The selected Agent or Customer should be visible in the app bar or a pinned context header, not hidden in a previous screen.

### 3.4 Frequency determines prominence

Daily tasks receive primary placement. Occasional setup and configuration are available but visually secondary.

### 3.5 Preserve context, not necessarily depth

Removing a detail screen is not automatically an improvement. A screen should be kept when it preserves an important context; it should be reduced or redesigned when it merely acts as a crowded menu hub.

### 3.6 Business logic is not part of this redesign

The UI may change how a user reaches a calculation, but it must not change the calculation or its source data.

## 4. Proposed Information Architecture

The recommended architecture uses four operational workspaces plus a controlled management area:

```text
Home / Today
├── Quick Entry
├── Ledger
├── Reports
├── Settlement
└── Manage
    ├── Agents
    ├── Customers through selected Agent
    ├── Limits
    ├── Closed Numbers
    ├── Winning Numbers
    ├── Format Guide
    └── Settings
```

This is a conceptual architecture, not a requirement that every item become a bottom-navigation tab.

### Recommended canonical destinations

| Destination | Responsibility | Scope |
|---|---|---|
| Home / Today | Today status and next actions | Global with optional selected context |
| Quick Entry | Fast bet entry | Agent + Customer + Date + Draw |
| Ledger | Existing totals and entries | All Agents / Agent / Customer + Date + Draw |
| Reports | Before/After and weekly analysis | Agent or Customer + period |
| Settlement | Financial settlement | Agent + Date + Draw |
| Manage | Setup and configuration | Global or selected Agent |
| Guide | Input-format help | Global |

### Recommended bottom navigation

The best default is four items, not five:

```text
Home   Ledger   Reports   Manage
```

Quick Entry should be the prominent central action or the primary action on Home and Ledger, rather than a full destination that duplicates Home. Settlement should be reachable from Home status and Reports, and may be a prominent action inside Ledger or Reports depending on real usage frequency.

If actual daily usage proves that Settlement is used repeatedly enough to deserve a permanent destination, the alternative is:

```text
Home   Ledger   + Entry   Settlement   Manage
```

Reports would then be reached through Ledger or Manage. This alternative should be chosen only after real usage testing, not merely because Reports exists.

## 5. Proposed Home Workspace

### Home responsibility

Home is the starting workspace for today's operation. It should not contain separate buttons for every configuration feature.

### Recommended Home layout

```text
┌────────────────────────────────────┐
│ Myanmar 2D              Settings   │
│ Today • 11 Sep 2026                 │
├────────────────────────────────────┤
│ TODAY STATUS                       │
│ Total stake              MMK ...    │
│ Agents active             ...       │
│ Morning     ...   Evening    ...   │
├────────────────────────────────────┤
│ PRIMARY ACTION                     │
│ [ + အမြန်စာရင်းသွင်းရန် ]          │
├────────────────────────────────────┤
│ NEXT / RECENT                      │
│ Latest entries or pending result   │
│ [ယနေ့စာရင်းကြည့်ရန်]              │
│ [ရှင်းတမ်းကြည့်ရန်]               │
└────────────────────────────────────┘
│ Home   Ledger   Reports   Manage   │
└────────────────────────────────────┘
```

### Home should contain

- Today and current draw context
- One visually dominant Quick Entry action
- A compact status summary
- A small recent-activity or next-action section
- A direct route to today's Ledger
- A direct route to Settlement only if it is currently relevant

### Home should not contain

- A grid of every feature
- Separate buttons for limits, closed numbers, format guide, and settings
- Duplicate Agent, Ledger, or Settlement buttons already present in navigation
- Large decorative banners that push operational content below the fold
- Several equal-sized action cards with no priority

## 6. Agent UX Recommendation

### Decision

Do not remove Agent context blindly. Keep an Agent list workspace, but do not use Agent Detail as a crowded feature menu.

Recommended structure:

```text
Manage
└── Agents
    ├── Agent list
    ├── Add Agent
    ├── Edit Agent
    └── Select Agent
        ├── Compact Agent context header
        ├── Customer list / customer management
        └── Agent-scoped actions through clear sections or routes
```

The selected Agent context should be reusable by Ledger, Reports, Limits, Closed Numbers, and Winning views through explicit selectors. The old Agent Detail screen should be **redesigned**, not automatically deleted.

### Agent workspace responsibilities

The Agent workspace should focus on:

- Agent identity and contact information
- Add/edit Agent
- Customer management under that Agent
- A clear way to launch Agent-scoped workspaces

It should not place Format Guide beside financial operations as if they have equal priority.

### Agent-specific workspaces

- Agent totals belong in Ledger with an Agent selector.
- Agent reports belong in Reports with an Agent scope.
- Agent limits and closed numbers belong in Manage with a selected Agent context.
- Agent winning views belong in Winning or Reports with a selected Agent context.
- Format Guide is global and should not be inside Agent Detail.

## 7. Customer UX Recommendation

Keep Customer Detail because it preserves a high-value context, but restructure it into two groups:

```text
Customer Detail
├── Primary: စာရင်းသွင်းရန်
├── Primary: ယနေ့/လက်ရှိစာရင်း
├── History
└── More
    ├── Analysis
    ├── Report
    ├── Limit
    ├── Commission
    └── Winning view
```

The user should be able to enter a bet from Customer Detail without reselecting the Agent and Customer. Occasional configuration should be placed behind a secondary section or overflow action rather than being displayed as equal-sized cards.

## 8. Quick Entry Recommendation

The canonical flow should be:

```text
Quick Entry
→ Agent
→ Customer
→ Date + Draw
→ Input + Format
→ Preview + Validation
→ Confirm
→ Receipt / next action
```

A contextual shortcut from Customer Detail may open the same screen with Agent and Customer preselected. It must not create a second betting implementation.

The betting screen must keep a compact pinned context header:

```text
Agent name  •  Customer name
Date        •  Morning/Evening
```

The lower portion should follow the operational order:

```text
Input → Format → Preview → Validation → Confirm
```

The Confirm action must remain visible without covering the final preview information.

## 9. Ledger Recommendation

Ledger should be a standalone workspace rather than something that requires opening Agent Detail first.

### Ledger filters

```text
Scope: All Agents | One Agent | One Customer
Date: selected date
Draw: Morning | Evening
```

The selected scope must remain visible in the header. The screen should show compact totals first and detailed rows second.

### Ledger flow

```text
Home or navigation
└── Ledger
    ├── Select scope
    ├── Select date
    ├── Select draw
    ├── View summary
    └── Expand Agent/Customer rows when needed
```

This reduces unnecessary depth while preserving context through an explicit selector.

## 10. Reports, Winning, and Settlement

### Reports

Reports should be a scoped workspace:

```text
Reports
├── Scope: Agent or Customer
├── Period: Daily or Weekly
├── State: Before or After
└── Results
```

Do not expose every report variant as a separate Home button.

### Winning

Winning numbers are global source data, while Agent and Customer results are scoped views derived from that source. The UI should therefore distinguish:

```text
Winning
├── Enter / edit global winning number
└── View results by Agent or Customer scope
```

Do not ask a user to enter a winner separately inside an Agent or Customer view.

### Settlement

Settlement should be entered from a context that already identifies:

- Agent
- Date
- Draw
- Total stake
- Winning stake
- Payout
- Commission
- Net settlement

It may be a Home next-action card when relevant, a Ledger action, and a Reports action, but these must all open the same Settlement workspace rather than separate implementations.

## 11. Scope Model

| Scope | Examples | Selector behavior |
|---|---|---|
| Global | Date, global winning number, closed day, guide, settings | No Agent or Customer required |
| All Agents | Overall Ledger, overall summary | Agent selector set to All Agents |
| Agent | Agent total, Agent report, Agent limits, Agent closed numbers | Agent selector required and visible |
| Customer | Bet entry, history, analysis, commission, customer report | Agent context plus Customer selector required |

A screen must never silently change scope. If a user enters an Agent-scoped screen from a selected Agent, the selected Agent must be visible and editable through the scope control.

## 12. Current vs Proposed Flow

| User intent | Current route | Proposed route |
|---|---|---|
| Add Agent | Home → Agents → Add | Manage → Agents → Add |
| Enter bet for known Customer | Customer Detail → Betting | Customer Detail → Quick Entry with context preserved |
| Enter a new bet without context | Home → Quick Entry → Agent → Customer | Home → Quick Entry → Agent → Customer |
| View today's totals | Home → Ledger or Agent → Detail → Total | Home → Ledger → Scope/date/draw |
| View Agent report | Agent → Detail → Report | Reports → Agent scope |
| Configure Agent closed number | Agent → Detail → Closed | Manage → Agent scope → Closed Number |
| Configure Agent limit | Agent → Detail → Limit | Manage → Agent scope → Limit |
| Enter global winner | Home/Agent route → Winning | Manage or Winning → Global entry |
| View Customer result | Customer → Detail → Winning | Reports/Winning → Customer scope |
| View format guide | Agent Detail → Guide | Home or Manage → Guide |

## 13. Screen Decisions

| Existing screen | Decision | Reason |
|---|---|---|
| Home | Redesign | Remove feature-grid behavior; preserve today dashboard role |
| Agent List | Keep and simplify | It is the correct Agent management entry point |
| Agent Detail | Redesign, do not blindly delete | Preserve Agent context; remove crowded action-card behavior |
| Customer List | Keep | Natural child management under selected Agent |
| Customer Detail | Keep and regroup | High-value context for frequent customer work |
| Betting screen | Keep and refine | Highest-frequency workflow; preserve all business behavior |
| Today Ledger | Keep as canonical Ledger workspace | Add explicit scope selectors |
| Settlement | Keep as one canonical workspace | Reach it contextually, not through duplicate implementations |
| Reports | Keep as scoped workspace | Consolidate daily/weekly/before/after choices |
| Winning | Keep global entry separate from scoped result views | Preserve source-of-truth semantics |
| Limit | Keep as Agent/Customer-scoped configuration | Do not expose as an unscoped Home feature |
| Closed Number | Keep as Agent-scoped configuration | Do not expose as an unscoped Home feature |
| Format Guide | Move to global Guide destination | It is not Agent-specific |
| Settings | Keep as a secondary global destination | Avoid competing with daily work |

## 14. Implementation Order After Approval

No UI code should be rewritten until this architecture is accepted.

1. Freeze and document the existing route map.
2. Identify canonical destination for every existing feature.
3. Add shared scope/context components without changing domain behavior.
4. Redesign Home to show only today status, Quick Entry, recent activity, and relevant next actions.
5. Redesign Agent workspace and remove its feature-grid behavior.
6. Regroup Customer Detail into primary daily work and secondary management.
7. Make Ledger, Reports, Winning, and Settlement scope-aware.
8. Remove duplicate navigation entry points only after verifying replacement routes.
9. Preserve existing repository/ViewModel calls and business rules.
10. Add UI state coverage for loading, empty, unavailable, error, and edit flows.
11. Run existing unit tests and CI build.
12. Perform device-level smoke testing before claiming completion.

## 15. Non-Negotiable Verification Checklist

- Existing saved data opens without migration or loss.
- Betting parser and every quick format behave identically.
- Reverse input behaves identically.
- Limits and Special Limit precedence behave identically.
- Closed Number validation still occurs after expansion.
- Commission, payout, compensation, and P/L formulas are unchanged.
- Date and draw identity remain unchanged.
- Global winning data remains the source for scoped results.
- Editing a source bet refreshes totals and reports.
- Confirm remains the betting submission action.
- Home contains no duplicate feature grid.
- Selected Agent/Customer context is always visible in scoped workspaces.
- Every major task has one canonical destination.
- Back navigation returns to the user's previous context.
- Empty, loading, unavailable, and error states are intentional.

## 16. Immediate Decision Needed Before Coding

The architecture recommendation is:

> **Keep Agent and Customer context screens, but redesign them as context and primary-work screens rather than crowded feature menus. Make Ledger, Reports, Winning, and Settlement canonical scope-aware workspaces. Make Home a compact today dashboard, not a directory of every feature. Move global Guide and configuration into controlled destinations.**

The only remaining product decision before implementation is the exact bottom-navigation set. The recommended default is:

```text
Home | Ledger | Reports | Manage
```

with Quick Entry as the dominant action and Settlement as a contextual workspace. An alternative five-item layout may be considered only if real daily usage proves Settlement deserves permanent placement.

Until this decision is accepted, the current code should remain unchanged.
