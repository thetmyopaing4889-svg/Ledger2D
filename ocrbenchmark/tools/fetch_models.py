#!/usr/bin/env python3
"""Build-time only. Pin upstream commits; verify Git blob identity; record SHA256."""
import hashlib, json, pathlib, urllib.request, time
ROOT = pathlib.Path(__file__).resolve().parents[1] / "src/main/assets"
REVS = {"fast":"65727574dfcd264acbb0c3e07860e4e9e9b22185",
        "best":"e2aad9b983032bb1beff9133104a67cdbb87ca4d"}
def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={"User-Agent":"Cherry-OCR-Build"}),timeout=90) as r:
                return r.read()
        except Exception:
            if attempt==2: raise
            time.sleep(2**attempt)
records=[]
for kind, rev in REVS.items():
    repo="tesseract-ocr/tessdata_"+kind
    tree=json.loads(fetch(f"https://api.github.com/repos/{repo}/git/trees/{rev}"))
    by_name={e["path"]:e["sha"] for e in tree["tree"] if e["type"]=="blob"}
    for name in ("mya.traineddata","eng.traineddata","LICENSE"):
        url=f"https://raw.githubusercontent.com/{repo}/{rev}/{name}"
        data=fetch(url)
        blob=hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
        assert blob==by_name[name], f"Git blob mismatch {name}"
        dest=ROOT/kind/("tessdata" if name.endswith(".traineddata") else "")/name
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes(data)
        records.append(dict(path=str(dest.relative_to(ROOT)),source=url,sha256=hashlib.sha256(data).hexdigest(),bytes=len(data),git_blob=blob))
# Pinned model and checksum from Hugging Face's revision-specific LFS metadata.
rev="456fa72d49996f1542fa7a957fd41c348f27ddca"
repo="ksithu/myanmar-crnn-ocr"
name="myanmar-crnn-ocr.onnx"
meta=json.loads(fetch(f"https://huggingface.co/api/models/{repo}/revision/{rev}?blobs=true"))
entry=next(x for x in meta["siblings"] if x["rfilename"]==name)
url=f"https://huggingface.co/{repo}/resolve/{rev}/{name}"
data=fetch(url)
digest=hashlib.sha256(data).hexdigest()
assert digest==entry["lfs"]["sha256"], "CRNN LFS SHA256 mismatch"
dest=ROOT/"crnn"/name
dest.parent.mkdir(parents=True,exist_ok=True)
from crnn_compat import convert
converted=convert(data)
dest.write_bytes(converted)
records.append(dict(path=str(dest.relative_to(ROOT)),source=url,source_sha256=digest,sha256=hashlib.sha256(converted).hexdigest(),bytes=len(converted),license="Apache-2.0",conversion="ConvInteger float accumulator v1"))
license=fetch("https://raw.githubusercontent.com/kaungsithu/mmpdfkit/034a567b91c9c11205422902119bee97dccd844c/LICENSE")
(ROOT/"crnn"/"UPSTREAM-LICENSE").write_bytes(license)
(ROOT/"models.json").write_text(json.dumps(records,indent=2),encoding="utf-8")
print(json.dumps(records,indent=2))
