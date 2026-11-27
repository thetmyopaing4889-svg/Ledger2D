# Stage A4 — Adaptive host candidate benchmark
Base: 601258b19e6cdfb7b1af9bcb8d3d6eaf75f43104.
Same 18 hash-matched cropped white-on-dark screenshots, visual draft ground truth, same pinned Fast models. No private screenshot or transcript committed.

Six recognition passes on 41 detected line crops: base PSM7 mya+eng, PSM13 mya+eng, 2x Lanczos PSM7, threshold 90 PSM7, mya only PSM7, eng only PSM7. Host prototype was executed; the newly packaged CLI host_adaptive_benchmark.py still needs independent execution. Results are development-set observations, not Android accuracy and not validation on unseen images.

| Method | CER ignoring whitespace | image exact | numeric sequence exact |
|---|---:|---:|---:|
| PSM7 base | 19.06% | 9/18 | 12/18 |
| PSM13 | 20.53% | 5/18 | 10/18 |
| 2x Lanczos | 20.53% | 6/18 | 10/18 |
| threshold 90 | 21.99% | 6/18 | 9/18 |
| mya only | 17.01% | 8/18 | 8/18 |
| eng only | 49.27% | 3/18 | 3/18 |
| base + empty PSM13 fallback | 16.42% | 9/18 | 13/18 |
| base + standalone Translate preview exclusion | 8.50% | 12/18 | 12/18 |
| combined review preview | 5.87% | 12/18 | 13/18 |

The draft has 37 content lines. 41 crops include 4 standalone Translate labels. 26 crops had disagreement among the five Myanmar-capable variants or an empty primary. A PSM13 fallback recovers a previously empty line in image 17. The combined preview has zero images with fewer nonempty lines than reference; this is only a count check, not true line recall. Raw labels, candidate text and coordinates are retained. Translate exclusion is an experimental review preview based on exact agreement of four passes, not a safe general deletion policy. All rows require human review; do not use candidate agreement as correctness.

Upscale improves some Burmese digit/letter cases, but corrupts otherwise correct digits elsewhere. Myanmar-only also trades errors. Therefore do not globally switch model or preprocessing. Remaining 6 images fail exact text; 5 fail numeric token sequence. Need separate symbol and amount metrics, independent transcript review, held-out screenshots, polarity-aware line detection and validated UI geometry, Android ML Kit and Best fallback. Android implementation and Build 6 not done; production app unchanged.
