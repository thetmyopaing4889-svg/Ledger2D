# Home Commission Card — resilient money display pilot

Base: successful Home typography experiment #937.

## Changes
- Keep original Home, Commission card shape, header, action, color, tap target, and all calculation sources unchanged.
- Default UI remains the familiar 2-column morning/evening/today versus cumulative card.
- Shorten Myanmar cumulative subtitle from the long "ယနေ့အထိ ကော်မရှင်ရငွေစုစုပေါင်း" to "ယနေ့အထိ" under the Commission earnings heading; English remains "Total to date".
- Switch to four full-width labeled rows if a draw/today amount exceeds 10 formatted characters or cumulative amount exceeds 9 formatted characters.
- Show complete amounts including sign, separators and units. Large amounts get soft line breaks after commas; never intentionally ellipsize financial data.
- This is a **display-only** change. It does not modify commission source, snapshot, aggregation, settlements, database or reports.

## Acceptance
- Test exact threshold and positive/negative/Long.MAX_VALUE/Long.MIN_VALUE formatting.
- CI unit tests, lint, debug and release APKs.
- Same-device English and Myanmar screenshot of ordinary zero and populated sums.
- A large-value fixture must not hide a digit, overlap actions, or push Home quick actions into navigation.
- Home has a fixed height. Visual behavior for very large values and small screens still needs device validation before marking the pilot production-ready.

No agent/customer dashboards, JPEG, weekly grid or production branch changes in this batch.
