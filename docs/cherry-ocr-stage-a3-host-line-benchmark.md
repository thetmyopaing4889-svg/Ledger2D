# Stage A3 — Host line OCR checkpoint (2026-11-27)

Base: Build 5, 66594594ccdb9e020dcd30515bf242a4f1770015. Production app untouched.

18 original Viber screenshots were recovered from the user's archive; SHA-256 matched all 18 Build 5 image records. The transcript is a visual draft, not independently verified. Private images and transcript are not committed.

Host Tesseract 5.5.0; mya and eng traineddata SHA-256 match the pinned Build 5 Fast models. Runtime differs from Android. Four prototype runs on the same 18 screenshots, NFC plus Burmese-digit mapping; whitespace excluded from CER and image exact, numeric sequence compares contiguous digit tokens. UI text is not silently removed.

| Method | CER | image exact | numeric sequence exact |
|---|---:|---:|---:|
| whole PSM 6 | 39.6% | 1/18 | 3/18 |
| whole PSM 11 | 34.0% | 4/18 | 5/18 |
| line PSM 7 | 19.1% | 9/18 | 12/18 |
| line PSM 13 | 20.5% | 5/18 | 10/18 |

Host prototype suggests segmentation is promising, but these are development-set results only. The reusable CLI is in ocrbenchmark/tools/host_line_benchmark.py. Its packaging has not been independently executed yet.

Known gaps: wrong digit and delimiter recognition, missing line recognition despite detection, UI Translate still read. A brightness threshold assumes white text on dark UI and cannot generalize to light mode or colored messages. Preserve all line crops and raw candidates; do not silently discard uncertain content. Do not infer correctness from engine agreement.

Next gates: independently review transcript, compare whole-image and line OCR using the same metric, evaluate polarity-aware segmentation and additional unseen samples; assess exact amounts, symbols and line recall; compare ML Kit on Android and Tesseract Best fallback. Keep CRNN research isolated; diagnose per-line empty failures independently. Do not ship or integrate into production before verification; no automatic submission or guess-based substitutions. Build 6 is not yet approved for release.
