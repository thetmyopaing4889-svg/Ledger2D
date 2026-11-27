"""Compare script/language OCR on an existing host adaptive benchmark.
Run host_adaptive_benchmark.py first, then:
python host_script_benchmark.py IMAGE_DIR TRUTH_JSON ADAPTIVE_JSON OUTPUT_JSON
Requires the original images, locally installed Tesseract models, and private draft truth.
No production integration or automatic text correction.
"""
import argparse
import hashlib
import io
import json
import os
import re
import subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from PIL import Image
from host_line_benchmark import clean, edit, norm, segments

MODELS = {
    "mya": "02aa6c25cfe9e583fa7b5d4131eac948f962308983f0f397df077dea58212b03",
    "eng": "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
    "Myanmar": "78f95e6033824b7c197a099e9fdef55e0742146619d822c9b8d81f933f2b27df",
}
NO_DAWG = ["-c", "load_system_dawg=0", "-c", "load_freq_dawg=0"]
VARIANTS = {
    "base": ("mya+eng", 7, []),
    "reverse": ("eng+mya", 7, []),
    "no_dawg": ("mya+eng", 7, NO_DAWG),
    "script": ("Myanmar", 7, []),
    "script13": ("Myanmar", 13, []),
    "script_no_dawg": ("Myanmar", 7, NO_DAWG),
    "script_up": ("Myanmar", 7, []),
}

def recognize(im, lang, psm, extra, tessdata):
    buf = io.BytesIO()
    im.save(buf, format="PNG")
    r = subprocess.run(
        ["tesseract", "stdin", "stdout", "--tessdata-dir", str(tessdata),
         "-l", lang, "--psm", str(psm), *extra],
        input=buf.getvalue(), capture_output=True, check=True,
        env={**os.environ, "OMP_THREAD_LIMIT": "1"})
    return r.stdout.decode().strip()

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ("image_dir", "truth_json", "adaptive_json", "output_json"):
        ap.add_argument(name, type=Path)
    ap.add_argument("--tessdata", type=Path, required=True)
    args = ap.parse_args()
    for name, expected in MODELS.items():
        actual = hashlib.sha256((args.tessdata / (name + ".traineddata")).read_bytes()).hexdigest()
        if actual != expected:
            raise ValueError("Model hash mismatch: " + name)
    truth = json.loads(args.truth_json.read_text(encoding="utf-8"))["images"]
    prior = json.loads(args.adaptive_json.read_text(encoding="utf-8"))["images"]
    if not truth:
        raise ValueError("Empty truth")
    def job(item):
        ident, expected = item
        path = args.image_dir / (ident + ".png")
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected["sha256"]:
            raise ValueError("Image hash mismatch: " + ident)
        with Image.open(path) as im:
            crops = segments(im)
        if len(crops) != len(prior[ident]):
            raise ValueError("Prior crop count mismatch: " + ident)
        rows = []
        for (box, crop), old in zip(crops, prior[ident]):
            if list(box) != old["box"]:
                raise ValueError("Prior crop coordinates mismatch: " + ident)
            candidates = {}
            for name, (lang, psm, extra) in VARIANTS.items():
                img = crop.resize((crop.width*2, crop.height*2), Image.Resampling.LANCZOS) if name == "script_up" else crop
                candidates[name] = recognize(img, lang, psm, extra, args.tessdata)
            rows.append({"box": box, "candidates": candidates,
                         "prior_candidates": old["candidates"], "ui_candidate": old["ui_candidate"],
                         "review_required": True})
        return ident, rows
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = dict(pool.map(job, truth.items()))
    metrics = {}
    for method in (*VARIANTS, "combined_baseline"):
        errors = chars = exact = numeric = 0
        for ident, expected in truth.items():
            lines = [r for r in results[ident] if not r["ui_candidate"]]
            b = "\n".join((r["candidates"]["base"] or r["prior_candidates"]["raw13"])
                          if method == "combined_baseline" else r["candidates"][method]
                          for r in lines)
            a = expected["text"]
            errors += edit(clean(a), clean(b))
            chars += len(clean(a))
            exact += clean(a) == clean(b)
            numeric += re.findall(r"\d+", norm(a)) == re.findall(r"\d+", norm(b))
        metrics[method] = {"cer_no_whitespace": errors/chars if chars else None,
                           "image_exact": exact, "numeric_sequence_exact": numeric, "images": len(truth)}
    args.output_json.write_text(json.dumps({"models": MODELS, "metrics": metrics, "images": results,
        "warning": "Development-only comparison; UI candidate exclusions are not production deletions. Every line requires review."}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(metrics, indent=2))

if __name__ == "__main__":
    main()
