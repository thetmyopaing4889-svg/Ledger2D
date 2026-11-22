# Rules Library: prospective Better Data success bridge

Base: Build 1821, commit 36188fb9b97313b3a78fb16520c7f79f4973f928.
Branch: feature/rules-success-library-from-build1821-20261122.

## Source of truth
- Only the existing local CherrySuccessSnapshotStore "cherry_success_draw_archive_v1" and confirmed 2D history are read. No historical reconstruction of a missing pre-result forecast.
- Reuse CherrySuccessEngine.completed() cutoff/confirmation checks. Result corrections re-evaluate the frozen snapshot; no additive mutable counter exists.
- Reading the archive happens on Dispatchers.IO **only when Rules is opened**; no Better Data formula search or background engine runs.
- Unreadable archive errors are shown, never silently converted to "0/0" success.

## Scoring
- One canonical Library source ID per pre-result eligible draw: 1 in the denominator, 1 in the numerator only when a recorded prediction matched the confirmed result.
- Even when multiple evidence IDs or CURRENT/BORN tabs match one Library source in the same draw, one draw can contribute only once. Union their pairs and count unique pairs for the evidence view.
- Not eligible: no pre-result snapshot, not completed or confirmed, no pair prediction, cut from the snapshot, MATH formula, or an ID whose source mapping has not been verified.
- A zero-hit source displays e.g. 0/4 in the original book but does not enter the success book; a first hit after four misses enters at 1/5.
- Exact score is stored **nowhere new**; computed from authoritative snapshots/results so it never doubles with a corrected winner.

## Books and mapping
- The 10 source books and 98 canonical rules in two_d_rules_reader.json are immutable.
- The 11th "အောင်ခဲ့သောမူများ" is a derived view of the SAME TwoDReaderBookEntry objects. Sorted by correct hits, then percent, eligible count, and stable canonical rule ID.
- Exact-ID bridge: direct new rules where trigger/outputs match; verified original 9:30, grouping/BK, double wheel/born, selected section 6 & 8 relations and weekday rules. Born evidence '@YYYY-MM-DD-SESSION' origin is normalized before mapping.
- Display names and numeric-substring coincidences NEVER decide a source mapping.
- Do **not** score the ambiguous new-mob-wheel / 1267, partial new-special-khant, broad section-2 digit table submethods, or other unverified correspondences. Their Better Data records are not removed and can be mapped after separately reviewing their exact source conditions.
- The Library score detail shows actual date/AM-PM/result, hit or miss, and unique predicted pair count for each eligible draw.
- Search and saved books retain the original canonical behavior; the derived cover can be saved, but no duplicated source rows are added to search.

## QA
- 3/7 including earlier misses, zero-hit 0/4, missing snapshot ignored, two source IDs or tabs only one point per draw, multiple Born origins, old-result correction, unknown IDs and Math excluded, after-cutoff snapshot excluded, mapping to real Library IDs.
- App Gradle unit tests, lint, debug APK, release APK, and artifact upload must pass.
- Manual phone QA: all ten old covers intact, success cover appears even when empty, 0/4 only in source book, hits enter success book on confirmed result, number lookup/search/bookmarks work, all scores survive navigation/restart, winner correction updates without double-counting.
- No edits to main, Research Workspace, LIVE, or formula-discovery engines.

## Cross-listed 00–99 table accuracy pass
- The canonical source rule cherry-021 appears as two distinct reader projections: category-double (wheel column) and category-born (born-pairs column).
- Scoring treats them as separate display keys cherry-021#wheel and cherry-021#born, each from its own pre-draw Success evidence. No wheel hit can mark the born-pairs column correct or vice versa.
- Other cross-listed source entries are only shared when they describe the same canonical condition; unresolved mixed conditions remain unmapped rather than inflated.

## Build 1824 whole-rule mapping correction (2026-11-22)
- The original Library rule is authoritative. A Better Data rule that computes only one output branch of a multi-outcome source does not certify success of the whole source.
- Do not map s3-power => cherry-011: the original requires ပါဝါ / ဆယ်ပြည့် but the recorded signal covers only ပါဝါ.
- Do not map s3-six or s3-week => cherry-019/020: these are group/whole-week conditions, and an isolated pre-draw 2D pair result cannot establish the original rule-level success.
- Do not map s5-mon or s5-wed => cherry-028/029 yet: these depend on week/remaining-sessions expiry conditions and should have a source-faithful horizon evaluator before awarding a whole-rule score.
- Do not map s6-70, s6-12, s6-12-wheel, s6-am92 or s6-92 => cherry-035/038/056: they implement only portions of combined source outcomes.
- Do not map s8-00-bk => cherry-074 yet: 5 working days in the existing engine is not the plain 5-day duration asserted in the original source.
- Do not map s6-78 => cherry-051 yet: the source says R (reverse), which is not fully expressed in the current signal's 8 direct pairs.
- These signals **remain usable in Better Data and its existing Success history**; only the inaccurate whole-Library-rule awarding is suspended. Original 98 rule entries and books are not altered.
- An unmapped original rule no longer claims 'No eligible draw recorded yet'; the no-record message is reserved for actually supported score IDs.
- Regression tests verify all withheld IDs, supported IDs, and that CURRENT/BORN duplicate evidence yields at most one draw-level score.
