# Myanmar 2D Agent Ledger

A native, offline-first Android ledger for Myanmar 2D agents and customers. The app uses **Kotlin**, **Jetpack Compose / Material 3**, **Room**, **ViewModel + Flow**, and **Navigation Compose**. Money is represented as integer MMK, commission percentages as basis points, and all parser/limit/report formulas live outside the UI.

## Features implemented

The current production-oriented foundation includes a clean first launch, Welcome and Agent flows, editable Agent and Customer records, customer and agent details, draw-aware betting entry with the required **Confirm** action, separators/reverse/quick-format expansion, immediate preview, cumulative limit and Closed Number enforcement, digit and agent total grids, commission and limit configuration, global winning-number entry/history, real date/session-scoped Agent and Customer winning views, Before/After reports backed by Room aggregates, Customer `သုံးသပ်ချက်` analysis, Format help, Room foreign keys/indexes, reactive repositories, and centralized deterministic report calculation services.

No login, cloud sync, export, backup, ads, payment, or unrelated features are included.

## Requirements

- JDK 17 or newer (the Gradle toolchain targets Java 17)
- Android SDK Platform 35 and Build Tools
- Internet access on first build to resolve Gradle/Maven dependencies

## Build

```bash
./gradlew clean assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Tests and checks

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Run the complete local verification pipeline with:

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

## Business-rule notes

- A draw identity is ISO `LocalDate` plus `MORNING` or `EVENING`.
- All Limit applies to every digit; Special Limit overrides All Limit for its digit.
- Expanded and aggregated `00`–`99` lines are authoritative for limits, totals, winning stake, and reports.
- Integer percentage arithmetic truncates fractions smaller than one MMK; it never uses floating point for stored money.
- Room destructive migration fallback is intentionally disabled.
