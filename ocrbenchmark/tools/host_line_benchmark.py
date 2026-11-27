"""Experimental host-only dark-chat OCR benchmark. Python 3, Pillow, numpy, Tesseract CLI.
Usage: python host_line_benchmark.py IMAGE_DIR TRUTH_JSON OUTPUT_JSON
Images are named <id>.png; truth JSON has images: {id: {sha256, text}}.
Keep private screenshots and transcripts outside git. No automatic corrections or deletion.
"""
import argparse
import hashlib
import io
import json
import re
import subprocess
import unicodedata
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageOps
import numpy as np

def norm(s):
    return unicodedata.normalize("NFC", s).translate(str.maketrans("၀၁၂၃၄၅၆၇၈၉", "0123456789"))

def clean(s):
    return re.sub(r"\s+", "", norm(s))

def edit(a, b):
    p = list(range(len(b)+1))
    for i, x in enumerate(a, 1):
        q = [i]
        for k, y in enumerate(b, 1):
            q.append(min(q[-1]+1, p[k]+1, p[k-1]+(x!=y)))
        p = q
    return p[-1]

def recognize(im, psm):
    buf = io.BytesIO()
    im.save(buf, format="PNG")
    r = subprocess.run(["tesseract","stdin","stdout","-l","mya+eng","--psm",str(psm)],
                       input=buf.getvalue(), capture_output=True, check=True)
    return r.stdout.decode().strip()

def segments(im):
    # Experimental for white-on-dark chat only: timestamps may be dimmer.
    a = np.asarray(im.convert("RGB")).astype(int)
    mask = (a.min(axis=2)>185) & ((a.max(axis=2)-a.min(axis=2))<35)
    rows = np.flatnonzero(mask.sum(axis=1)>=3)
    groups = []
    for y in rows:
        if groups and y-groups[-1][-1]<=11:
            groups[-1].append(y)
        else:
            groups.append([y])
    out = []
    for g in groups:
        if len(g)<9:
            continue
        y0,y1 = int(g[0]),int(g[-1])+1
        xs = np.flatnonzero(mask[y0:y1].sum(axis=0)>0)
        if len(xs)<4:
            continue
        x0,x1 = int(xs[0]),int(xs[-1])+1
        box=(max(0,x0-3),max(0,y0-4),min(im.width,x1+3),min(im.height,y1+4))
        crop=ImageOps.invert(im.crop(box).convert("L"))
        out.append((box,ImageOps.expand(crop,border=12,fill=255)))
    return out

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("image_dir",type=Path)
    ap.add_argument("truth_json",type=Path)
    ap.add_argument("output_json",type=Path)
    args=ap.parse_args()
    truth=json.loads(args.truth_json.read_text(encoding="utf-8"))["images"]
    if not truth:
        raise ValueError("Empty ground truth")
    def job(item):
        ident,expected=item
        path=args.image_dir/(ident+".png")
        if hashlib.sha256(path.read_bytes()).hexdigest()!=expected["sha256"]:
            raise ValueError("Image hash mismatch: "+ident)
        with Image.open(path) as original:
            im=original.convert("RGB")
        lines=segments(im)
        outputs={f"whole_{p}":recognize(im,p) for p in (6,11)}
        for p in (7,13):
            outputs[f"lines_{p}"]="\n".join(recognize(crop,p) for _,crop in lines)
        return ident,{"boxes":[box for box,_ in lines],"outputs":outputs}
    with ThreadPoolExecutor(max_workers=4) as pool:
        results=dict(pool.map(job,truth.items()))
    metrics={}
    for key in next(iter(results.values()))["outputs"]:
        errors=chars=exact=numeric=0
        for ident,expected in truth.items():
            a=expected["text"]
            b=results[ident]["outputs"][key]
            errors+=edit(clean(a),clean(b))
            chars+=len(clean(a))
            exact+=clean(a)==clean(b)
            numeric+=re.findall(r"\d+",norm(a))==re.findall(r"\d+",norm(b))
        metrics[key]={"cer_no_whitespace":errors/chars if chars else None,
                      "image_exact":exact,"numeric_sequence_exact":numeric,"images":len(truth)}
    args.output_json.write_text(json.dumps({"metrics":metrics,"images":results},
                                           ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps(metrics,indent=2))

if __name__=="__main__":
    main()
