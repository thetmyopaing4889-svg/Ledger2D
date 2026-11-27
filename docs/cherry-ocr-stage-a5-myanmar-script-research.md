# Stage A5 — Myanmar script OCR research
Base ecb41bbee52e45685f7bc12c3e18672e15cf3799. Host experiment only. Production app and main unchanged.

## Evidence
Tesseract 5.5.0 host, 18 previously hash-matched images, 41 crops (37 content and 4 Translate candidates), same visually transcribed draft, same crop segmentation. SHA-256: Myanmar.traineddata 78f95e6033824b7c197a099e9fdef55e0742146619d822c9b8d81f933f2b27df (7,840,489 bytes, local installed model; upstream revision/hash still to be verified before Android bundling). mya and eng models match Build 5 pinned fast hashes. Seven passes evaluated. All results below exclude the same four experimental UI candidates; normalize NFC and Burmese digits, ignore whitespace for CER and exact text, compare digit-token sequences for numeric exact.

| Variant | CER % | exact images | numeric exact images |
|---|---:|---:|---:|
| mya+eng PSM7 | 8.50 | 12/18 | 12/18 |
| eng+mya PSM7 | 19.65 | 5/18 | 7/18 |
| mya+eng PSM7 no system/frequency DAWG | 8.50 | 12/18 | 12/18 |
| Myanmar PSM7 | 4.69 | 13/18 | 14/18 |
| Myanmar PSM13 | 1.47 | 15/18 | 15/18 |
| Myanmar PSM7 no system/frequency DAWG | 4.69 | 13/18 | 14/18 |
| Myanmar PSM7 2x Lanczos | 6.74 | 11/18 | 13/18 |
| Stage A4 combined preview baseline | 5.87 | 12/18 | 13/18 |

Myanmar PSM13 exact content lines 34/37, numeric sequence exact 34/37. All three remaining mismatches are among 14/37 content lines with disagreement between script13, script7, base and raw13. This is observed on the development set only, not a calibrated review filter. Across all tested variants 36/37 lines have at least one candidate matching draft truth (oracle coverage, not automatic accuracy). One line has no fully matching candidate. No actual selection improvement may be claimed based on oracle coverage.

Remaining issues: amount digit substitutions, mixed-script numeric/letter confusion, delimiter and Myanmar text substitution. Never infer a replacement from an expected pattern or from majority agreement. Keep raw text and crop references; user review before accepting. Do not add screenshot-specific corrections. Raw private images/text stay outside Git.

## Sources and decisions
- https://tesseract-ocr.github.io/tessdoc/ImproveQuality.html — padding, segmentation, polarity, dictionary controls; disabling dictionaries did not help here.
- https://github.com/tesseract-ocr/tessdata_fast/blob/main/script/Myanmar.traineddata — script pack is a promising existing Tesseract candidate, no new runtime required; verify exact source revision and license before bundling.
- https://developers.google.com/ml-kit/vision/text-recognition/v2/android and /languages — Latin candidate and geometry, not Burmese recognition.
- https://www.paddleocr.ai/latest/en/version3.x/pipeline_usage/OCR.html — possible detector experiment; no verified Myanmar recognition pack from this list.
- https://huggingface.co/myandev/myanmar-crnn-ocr — Apache-2.0, claims small INT8 ONNX and scanned-document validation; check if renamed or same lineage as existing CRNN, Android operator compatibility, weights and real screenshot performance before considering.
- https://huggingface.co/pndaza/burmese-kraken-ocr — CC-BY-4.0, Burmese/Pali documents, Android export and mixed Latin coverage unverified.
- https://github.com/ye-kyaw-thu/myOCR and https://github.com/tesseract-ocr/tesstrain — possible synthetic-data fine-tuning research if existing script pack cannot generalize. Proper font shaping, independent splits and actual numeric/amount/line validation required.

Next: verify independent truth, held-out tests across fonts/themes, quantify amount and symbol exact match and real line recall, verify upstream script model provenance, run Android-specific tests including ML Kit and fallback. Host prototype ran; newly packaged CLI host_script_benchmark.py not yet separately executed. No Android APK built, no automatic insertion, no production integration.
