package com.myanmar.cherryocrbenchmark;
import android.graphics.*;
final class ImageVariants {
    static Bitmap prepare(Bitmap src,int mode){
        if(mode==0)return src;
        int w=src.getWidth(),h=src.getHeight();
        Bitmap scaled=src;
        if(mode==2 && (long)w*h*4<=12000000L && Math.max(w,h)<=3000)scaled=Bitmap.createScaledBitmap(src,w*2,h*2,true);
        int sw=scaled.getWidth(),sh=scaled.getHeight();
        Bitmap out=null;
        try{
            out=Bitmap.createBitmap(sw,sh,Bitmap.Config.ARGB_8888);
            int[] row=new int[sw];long sum=0,count=0;int[] hist=new int[256];
            for(int y=0;y<sh;y+=Math.max(1,sh/128)){scaled.getPixels(row,0,sw,0,y,sw,1);
                for(int x=0;x<sw;x+=Math.max(1,sw/128)){int z=gray(row[x]);hist[z]++;sum+=z;count++;}}
            boolean invert=sum<128*count;
            int lo=0,hi=255;long n=0;while(lo<255&&(n+=hist[lo])<count/100)lo++;n=0;while(hi>0&&(n+=hist[hi])<count/100)hi--;
            int range=Math.max(32,hi-lo);
            for(int y=0;y<sh;y++){scaled.getPixels(row,0,sw,0,y,sw,1);for(int x=0;x<sw;x++){
                int z=Math.max(0,Math.min(255,(gray(row[x])-lo)*255/range));if(invert)z=255-z;row[x]=Color.rgb(z,z,z);
            }out.setPixels(row,0,sw,0,y,sw,1);}
            return out;
        }catch(RuntimeException|OutOfMemoryError e){if(out!=null)out.recycle();throw e;}
        finally{if(scaled!=src)scaled.recycle();}
    }
    static int gray(int c){return (77*Color.red(c)+150*Color.green(c)+29*Color.blue(c))>>8;}
}
