package com.myanmar.cherryocrbenchmark;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import ai.onnxruntime.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.*;

/** Experimental cropped single-column screenshot reader. Model vocabulary matches
 * pinned mmpdfkit ocr.py revision 034a567b91c9c11205422902119bee97dccd844c.
 * Row projection is an experimental segmentation method, not a general chat detector. */
final class CrnnEngine implements AutoCloseable {
    private static final List<String> VOCAB=vocab();
    private final OrtEnvironment env=OrtEnvironment.getEnvironment();
    private final OrtSession session;
    CrnnEngine(AssetManager assets)throws Exception{
        byte[] bytes;try(InputStream in=assets.open("crnn/myanmar-crnn-ocr.onnx");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);bytes=out.toByteArray();
        }
        try(OrtSession.SessionOptions opts=new OrtSession.SessionOptions()){
            opts.setIntraOpNumThreads(2);opts.setInterOpNumThreads(2);session=env.createSession(bytes,opts);
        }
    }
    static List<String> vocab(){
        TreeSet<String> set=new TreeSet<>();set.add("<blank>");
        String ascii="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,!?;:()[]{}\"'/- ";
        for(char c:ascii.toCharArray())set.add(String.valueOf(c));
        for(int c=0x1000;c<=0x109f;c++)set.add(String.valueOf((char)c));
        for(int c=0xaa60;c<=0xaa7f;c++)set.add(String.valueOf((char)c));
        ArrayList<String> v=new ArrayList<>(set);if(v.size()!=272||!v.get(22).equals("<blank>"))throw new IllegalStateException("Vocabulary mismatch");return v;
    }
    static String ctc(int[] indices){
        StringBuilder s=new StringBuilder();int prev=-1;
        for(int i:indices){if(i<0||i>=VOCAB.size())throw new IllegalArgumentException("CTC class");if(i!=22&&i!=prev)s.append(VOCAB.get(i));prev=i;}
        return s.toString();
    }
    String read(Bitmap b)throws Exception{
        int w=b.getWidth(),h=b.getHeight();int[] pix=new int[w*h];b.getPixels(pix,0,w,0,0,w,h);
        int[] hist=new int[256];long sum=0;for(int c:pix){int z=ImageVariants.gray(c);hist[z]++;sum+=z;}
        boolean invert=sum<128L*pix.length;int threshold=otsu(hist,pix.length,sum);
        int[] left=new int[h],right=new int[h];Arrays.fill(left,w);Arrays.fill(right,-1);
        for(int y=0;y<h;y++){int count=0;for(int x=0;x<w;x++){int z=ImageVariants.gray(pix[y*w+x]);boolean ink=invert?z>threshold:z<=threshold;if(ink){count++;left[y]=Math.min(left[y],x);right[y]=x;}}
            if(count<Math.max(2,w/500)){left[y]=w;right[y]=-1;}}
        ArrayList<int[]> bands=new ArrayList<>();int start=-1,last=-1;
        for(int y=0;y<h;y++)if(right[y]>=0){if(start<0){start=y;}else if(y-last>Math.max(3,h/150)){bands.add(new int[]{start,last+1});start=y;}last=y;}
        if(start>=0)bands.add(new int[]{start,last+1});
        if(bands.isEmpty())return "";
        if(bands.size()>150)throw new IOException("Too many line bands; crop text area");
        ArrayList<String> text=new ArrayList<>();
        for(int[] band:bands){int x0=w,x1=-1;for(int y=band[0];y<band[1];y++){x0=Math.min(x0,left[y]);x1=Math.max(x1,right[y]);}
            if(x1<x0)continue;
            x0=Math.max(0,x0-4);x1=Math.min(w,x1+5);int y0=Math.max(0,band[0]-3),y1=Math.min(h,band[1]+3);
            int width=Math.max(4,Math.round((x1-x0)*32f/(y1-y0))*2);
            if(width>4096)throw new IOException("Line too wide; crop or split explicitly");
            float[] input=new float[32*width];
            for(int y=0;y<32;y++)for(int x=0;x<width;x++){
                int sx=x0+Math.min(x1-x0-1,x*(x1-x0)/width),sy=y0+Math.min(y1-y0-1,y*(y1-y0)/32);
                int z=ImageVariants.gray(pix[sy*w+sx]);input[y*width+x]=(invert?255-z:z)/255f;
            }
            try(OnnxTensor tensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,1,32,width});
                OrtSession.Result result=session.run(Collections.singletonMap("input",tensor))){
                float[][][] logits=(float[][][])result.get(0).getValue();int[] ids=new int[logits[0].length];
                for(int i=0;i<ids.length;i++){float[] scores=logits[0][i];if(scores.length!=272)throw new IOException("Unexpected CRNN classes");int best=0;for(int j=1;j<scores.length;j++)if(scores[j]>scores[best])best=j;ids[i]=best;}
                String decoded=ctc(ids);if(decoded.trim().isEmpty())throw new IOException("Empty CRNN line "+(text.size()+1));text.add(decoded);
            }
        }
        return String.join("\n",text);
    }
    private static int otsu(int[] hist,int count,long sum){
        long s=0,n=0;double best=-1;int threshold=127;
        for(int i=0;i<256;i++){n+=hist[i];s+=(long)i*hist[i];if(n==0||n==count)continue;double d=(double)s/n-(double)(sum-s)/(count-n);double v=(double)n*(count-n)*d*d;if(v>best){best=v;threshold=i;}}return threshold;
    }
    @Override public void close()throws OrtException{session.close();}
}
