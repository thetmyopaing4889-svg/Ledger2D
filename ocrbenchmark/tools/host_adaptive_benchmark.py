"""Host-only Stage A4: retain alternative OCR candidates and evaluate a review preview.
Usage: python host_adaptive_benchmark.py IMAGE_DIR TRUTH_JSON OUTPUT_JSON
Development experiment for cropped white-on-dark chat screenshots, not production.
The raw candidates and coordinates are always retained, including UI and empty lines.
"""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess

from PIL import Image
from host_line_benchmark import clean, edit, norm, segments

def recognize(im, psm, lang):
    buf = io.BytesIO()
    im.save(buf, format="PNG")
    result = subprocess.run(
        ["tesseract", "stdin", "stdout", "-l", lang, "--psm", str(psm)],
        input=buf.getvalue(), capture_output=True, check=True,
        env={**os.environ, "OMP_THREAD_LIMIT": "1"})
    return result.stdout.decode().strip()

def review(c):
    primary = c["base"]
    preview = primary if primary else c["raw13"]
    ui_candidate = all(c[k].strip() == "Translate" for k in
                       ("base", "raw13", "up", "eng"))
    # Agreement is not proof. Every line requires review; no automatic ledger writes.
    return {
        "preview": preview,
        "ui_candidate": ui_candidate,
        "empty_primary": not primary,
        "disagreement": len({clean(c[k]) for k in
                             ("base", "raw13", "up", "binary", "mya")}) > 1,
        "review_required": True,
    }

def recognize_line(box, crop):
    variants = {
        "base": (crop, 7, "mya+eng"),
        "raw13": (crop, 13, "mya+eng"),
        "up": (crop.resize((crop.width*2, crop.height*2), Image.Resampling.LANCZOS), 7, "mya+eng"),
        "binary": (crop.point(lambda x: 0 if x < 90 else 255), 7, "mya+eng"),
        "mya": (crop, 7, "mya"),
        "eng": (crop, 7, "eng"),
    }
    candidates = {key: recognize(*value) for key, value in variants.items()}
    return {"box": box, "candidates": candidates, **review(candidates)}

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("image_dir", type=Path)
    ap.add_argument("truth_json", type=Path)
    ap.add_argument("output_json", type=Path)
    args = ap.parse_args()
    truth = json.loads(args.truth_json.read_text(encoding="utf-8"))["images"]
    if not truth:
        raise ValueError("Empty truth")
    def job(item):
        ident, expected = item
        path = args.image_dir / (ident + ".png")
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected["sha256"]:
            raise ValueError("Image hash mismatch: " + ident)
        with Image.open(path) as im:
            lines = [recognize_line(box, crop) for box, crop in segments(im)]
        return ident, lines
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = dict(pool.map(job, truth.items()))
    metrics = {}
    for method in ("base", "raw13", "up", "binary", "mya", "eng", "empty_fallback", "ui_preview", "combined_preview"):
        counts = Counter()
        for ident, expected in truth.items():
            a = expected["text"]
            lines = results[ident]
            values = []
            for line in lines:
                if method in ("ui_preview", "combined_preview") and line["ui_candidate"]:
                    # Exclude from the experimental preview only, never from raw results.
                    continue
                if method in ("empty_fallback", "combined_preview"):
                    values.append(line["preview"])
                elif method == "ui_preview":
                    values.append(line["candidates"]["base"])
                else:
                    values.append(line["candidates"][method])
            b = "\n".join(values)
            counts["errors"] += edit(clean(a), clean(b))
            counts["chars"] += len(clean(a))
            counts["image_exact"] += clean(a) == clean(b)
            counts["numeric_sequence_exact"] += re.findall(r"\d+", norm(a)) == re.findall(r"\d+", norm(b))
            counts["fewer_nonempty_lines"] += sum(bool(x.strip()) for x in b.splitlines()) < len(a.splitlines())
        metrics[method] = {**dict(counts), "cer_no_whitespace": counts["errors"]/counts["chars"] if counts["chars"] else None}
    args.output_json.write_text(json.dumps({
        "metrics": metrics, "images": results, "development_only": True,
        "warning": "All lines require review; UI labels are candidates, not confirmed exclusions."
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(metrics, indent=2))

if __name__ == "__main__":
    main()
