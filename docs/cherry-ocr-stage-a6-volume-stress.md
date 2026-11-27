# Stage A6 — Dense text and volume stress
Base 51fd3cabd64e8d6b6477e892640224ae590bf2f2. Host research only; no production app or main changes.

User requires correctness with much larger real text inputs, not optimization on the original 18 development screenshots alone.

## Executed tests
Synthetic neutral text rendered with Pillow RAQM (Noto Sans Myanmar Regular or Padauk Regular, DejaVu Sans fallback when glyph missing), seeded numbers, 12/50/50/100 lines, varying font size and gap, dark and light uniform backgrounds. The initial synthetic test was invalid for Noto because Latin glyphs were missing; discarded recognition scores and reran with verified cmap coverage and fallback. The final synthetic prototype executed locally. CLI is packaged as host_volume_benchmark.py; packaged form not separately run. No private images or fonts in repository.

| Case | Old segmentation one-to-one lines | Margin-adaptive lines | Myanmar PSM13 exact text | mya+eng PSM7 exact text |
|---|---:|---:|---:|---:|
| Dark Noto 12 | 12 | 12 | 12 | 11 |
| Dark Padauk 50 | 0 | 50 | 38 | 50 |
| Light Noto 50 | 0 | 50 | 45 | 49 |
| Light Padauk 100 | 0 | 100 | 70 | 99 |
| Total 212 | 12 | 212 | 165 | 209 |

One-to-one means at least 70% reference vertical interval overlap, unique in both directions; not character accuracy. Exact text uses NFC, Burmese-digit mapping and ignores whitespace. Numeric metrics in this volume script also remove whitespace before extracting digit runs (not the same metric as A5, not separate amount accuracy). Host OCR time on the 100-line synthetic case: 3.87 sec script13 and 3.92 sec mya+eng7, 4 parallel CLI workers; not an Android speed or memory claim.

## Blocking regression
Applying the same margin-adaptive segmentation directly to the original 18 mixed-background chat crops with Myanmar PSM13 and experimental exact Translate-only filtering gave only 2/18 normalized text exact (versus prior 15/18 with previous segmentation/UI preview). Chat bubble borders, icons and timestamps were included; not directly comparable UI exclusion conditions. Do NOT replace existing chat segmentation globally. Uniform-background detector must be separate; routing must be independently validated. A single OCR model is not globally best: original 18 favored script13, synthetic dense text favors mya+eng7.

## Required next gates
- Design robust region/bubble detection, polarity per region and layout validation; maintain raw candidates and all source regions; never silently drop lines on error or ambiguity.
- Stress dense/wrapped text, multi-bubble chat, mixed background, font sizes, JPEG artifacts, narrow and tall inputs, many images per batch, cancellation/retry/memory. Preserve ordering and handle repeated text without deduplication by content.
- Hold out genuine new chat images; independently confirm draft truth. Record separate line recall, exact number, amount, symbol and full-image match with insertions and omissions; do not use synthetic numbers as real accuracy.
- Bounded processing, resumable review, no automatic acceptance of uncertain results or automatic ledger writes. Verify Android package/runtime, offline behavior and memory before integration.
- Build 6 not produced. Existing prototype remains isolated. No claim of 100% recognition.
