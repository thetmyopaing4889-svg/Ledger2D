"""Synthetic single-background OCR volume benchmark, not real chat validation.
Requires Python, Pillow with RAQM, NumPy, fonttools and Tesseract models Myanmar/mya/eng.
Run with --myanmar-fonts NotoSansMyanmar-Regular.ttf Padauk-Regular.ttf
--latin-font DejaVuSans.ttf --output result.json. No private data or fonts committed.
"""
import argparse, io, json, os, random, re, subprocess, time, unicodedata
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageDraw, ImageFont, ImageOps, features
from fontTools.ttLib import TTFont
import numpy as np

def norm(s):
    return re.sub(r'\s+', '', unicodedata.normalize('NFC', s).translate(str.maketrans('၀၁၂၃၄၅၆၇၈၉','0123456789')))

def make(n, font, size, dark, spacing, seed, latin):
    r = random.Random(seed)
    f = ImageFont.truetype(font,size,layout_engine=ImageFont.Layout.RAQM)
    lf = ImageFont.truetype(latin,size,layout_engine=ImageFont.Layout.RAQM)
    cmap=TTFont(font).getBestCmap()
    lc=TTFont(latin).getBestCmap()
    lines=[]
    for i in range(n):
        a,b,c=[r.randrange(100) for _ in range(3)]
        v=r.randrange(100,99999)
        s=[f'{a:02}/{b:02}/{c:02} = {v}',f'စာရင်း {a:02} / {b:02} {v}',f'{a:02}',f'ကုန်ပစ္စည်း {v}'][i%4]
        if i%3==0: s=s.translate(str.maketrans('0123456789','၀၁၂၃၄၅၆၇၈၉'))
        lines.append(s)
    bg,fg=((25,30,37),(236,237,238)) if dark else ((243,244,245),(25,30,37))
    rendered=[]
    for s in lines:
        runs=[]
        for c in s:
            use=f if ord(c) in cmap else lf if ord(c) in lc else None
            if use is None: raise ValueError('missing glyph '+repr(c))
            if runs and runs[-1][0] is use: runs[-1]=(use,runs[-1][1]+c)
            else: runs.append((use,c))
        boxes=[fo.getbbox(t,anchor='ls') for fo,t in runs]
        top=min(b[1] for b in boxes)
        bottom=max(b[3] for b in boxes)
        width=int(sum(fo.getlength(t) for fo,t in runs))+8
        row=Image.new('RGB',(width,bottom-top+4),bg)
        d=ImageDraw.Draw(row)
        x=2
        for fo,t in runs:
            d.text((x,2-top),t,font=fo,fill=fg,anchor='ls')
            x+=fo.getlength(t)
        g=np.asarray(row.convert('L'))
        ys,xs=np.where(abs(g.astype(int)-int(g[0,0]))>15)
        if not len(ys): raise ValueError('blank line')
        rendered.append(row.crop((0,int(ys.min()),row.width,int(ys.max())+1)))
    im=Image.new('RGB',(max(x.width for x in rendered)+64,sum(x.height for x in rendered)+spacing*(n-1)+48),bg)
    y=24
    truth=[]
    for s,row in zip(lines,rendered):
        im.paste(row,(24,y))
        truth.append((s,y,y+row.height))
        y+=row.height+spacing
    return im,truth

def crops(im,legacy):
    a=np.asarray(im.convert('RGB')).astype(np.int16)
    g=np.asarray(im.convert('L')).astype(np.int16)
    dark=np.median(g)<128
    if legacy:
        mask=(a.min(2)>185)&((a.max(2)-a.min(2))<35)
        gap=11
        min_h=9
    else:
        # Uniform background only. Mixed chat bubble backgrounds remain unsolved.
        bg=np.median(np.concatenate([g[:,:8],g[:,-8:]],axis=1),axis=1)[:,None]
        mask=(g-bg>35) if dark else (bg-g>35)
        gap=5
        min_h=2
    groups=[]
    for y in np.flatnonzero(mask.sum(1)>=2):
        if groups and y-groups[-1][-1]<=gap: groups[-1].append(y)
        else: groups.append([y])
    out=[]
    for q in groups:
        if len(q)<min_h: continue
        lo,hi=int(q[0]),int(q[-1])+1
        xs=np.flatnonzero(mask[lo:hi].any(0))
        if len(xs)<2: continue
        c=im.crop((max(0,int(xs[0])-3),max(0,lo-3),min(im.width,int(xs[-1])+4),min(im.height,hi+3))).convert('L')
        c=ImageOps.invert(c) if dark or legacy else c
        out.append((lo,hi,ImageOps.expand(c,border=12,fill=255)))
    return out

def match(found,truth):
    hits=[[i for i,(lo,hi,_) in enumerate(found) if max(0,min(hi,y1)-max(lo,y0))>=.7*(y1-y0)] for _,y0,y1 in truth]
    return [m[0] if len(m)==1 and sum(m[0] in o for o in hits)==1 else None for m in hits]

def recognize(c,lang,psm):
    b=io.BytesIO()
    c.save(b,format='PNG')
    return subprocess.run(['tesseract','stdin','stdout','-l',lang,'--psm',str(psm)],input=b.getvalue(),capture_output=True,timeout=30,env={**os.environ,'OMP_THREAD_LIMIT':'1'},check=True).stdout.decode().strip()

def evaluate(im,truth,legacy,lang='Myanmar',psm=13):
    found=crops(im,legacy)
    unique=match(found,truth)
    start=time.monotonic()
    with ThreadPoolExecutor(max_workers=4) as p:
        texts=list(p.map(lambda c:recognize(c,lang,psm),[c for _,_,c in found]))
    return dict(expected=len(truth),crops=len(found),unique=sum(i is not None for i in unique),exact=sum(i is not None and norm(s)==norm(texts[i]) for (s,_,_),i in zip(truth,unique)),numeric=sum(i is not None and re.findall(r'\d+',norm(s))==re.findall(r'\d+',norm(texts[i])) for (s,_,_),i in zip(truth,unique)),seconds=round(time.monotonic()-start,2))

if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--myanmar-fonts',nargs=2,required=True)
    ap.add_argument('--latin-font',required=True)
    ap.add_argument('--output',required=True)
    args=ap.parse_args()
    if not features.check('raqm'): raise RuntimeError('RAQM required for Myanmar shaping')
    rows=[]
    for j,(n,f,size,dark,spacing) in enumerate([(12,0,30,True,16),(50,1,26,True,8),(50,0,26,False,8),(100,1,24,False,7)]):
        im,t=make(n,args.myanmar_fonts[f],size,dark,spacing,410+j,args.latin_font)
        row=dict(theme='dark' if dark else 'light',font=f,width=im.width,height=im.height,legacy=evaluate(im,t,True),script13=evaluate(im,t,False),mya_eng7=evaluate(im,t,False,'mya+eng',7))
        rows.append(row)
        print(row,flush=True)
    with open(args.output,'w',encoding='utf-8') as out:
        json.dump(rows,out,indent=2)
