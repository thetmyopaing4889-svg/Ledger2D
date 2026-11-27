package com.myanmar.cherryocrbenchmark;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.*;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.android.gms.tasks.Tasks;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BenchmarkActivity extends Activity {
    private static final int PICK=10, EXPORT=11, FIXTURE_ZIP=12;
    private Button fixture,fixtureImport;
    private static final String[] FIXTURE_HASHES={"02e474f9b73294e9fd4a7fe2c7104bda383549abab740bdde7e596a19d13c421",
        "066d0c7e0f302b85a577d1aa4f515b13de44d6d4ab4d919f13b829ae50d868a8",
        "a52d64f56dc1590afbabb909b0cffce50d03c0d333a98074ba5701d441f4d660",
        "3ef0ce4bc7d69969cdb3bf4c873196c410e42114edc8550c8f716954c9d187b2",
        "5e85731150f54bc72ab1674599d76051b3d4f9be992c324fc4d50b20045ebe60",
        "f8a7e5541e6249f5a8d70017a217070621d2cc6590a56fa18a1ad81a4601f0a8",
        "efa524d3f46b7fc00ce08b3a0421789dcccb17dc5cfdfc39731a6db2a7c81514",
        "228a4b5f93408d5e5c8f9094f1f3edce4e7aa773b7dc6f8c7778f2f1001c9cbc",
        "c1d77b8640563718f0475908f11d7210ffda068f2e1447abbf0428ff32e0c65a",
        "bf96470dc7148e7dcf69cd1e81ed70e47dd578b301224cfa9f9053a66011518c",
        "fc456edb57cf569fe7b35e61f53c6e63a91aefa826d221451e0567de4426dd5f",
        "b29bb3b2f1fb7d344f2d26fa4052ae3a0bf750b985563b157e8b3377f7f360d1",
        "12c2e5ec4c3ba7cf763a977c6c1268106a65ab5ac9b24fa01bd25d5658dd1d52",
        "18b29cedeb18a0840ca713e82ba709021c98906a00cdf0b5888a8df25c2cf19f",
        "ee64a85e49ab41fa1e8f9926970da3eb5d96f341a7edfd8533dd5ae990bd45cd",
        "f96e5ce2b51d9df8c5fd039f0c53be1eacfdc3176e1b1c67129805336aaa44f6",
        "372a3fb9dd8b687bebaa7668c4ac1d1fa23d023d6ccc282966c4e2447a2c0cb4",
        "3d7ec74d9d4258059e5ba9c27e01dd837f193cca0d5eb6e3eaaa819b923de38d"};
    private static final String[] ENGINES={"Tesseract Fast","Tesseract Best","ML Kit Latin","Myanmar CRNN","Compare all 4"};
    private static final String[] VARIANTS={"Original","Contrast / polarity","Upscale + contrast","Auto layout + contrast","Sparse layout + contrast"};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean stop=new AtomicBoolean();
    private final ArrayList<Item> images=new ArrayList<>();
    private final ArrayList<Trial> trials=new ArrayList<>();
    private LinearLayout body,results,selected,summary;
    private Spinner variant;
    private TextView status;
    private Button pick,samples,run,cancel,export,clear;
    private Spinner engine,batch,repeats;
    private ProgressBar progress;
    private boolean busy=false;
    private String exportJson="";
    private final int cherry=0xff98133e;
    private static final class Item {
        final Uri uri; final String id; String truth;
        Item(Uri uri,String id,String truth){this.uri=uri;this.id=id;this.truth=truth;}
    }
    private static final class Trial {
        final String engine,variant; final int repeat; final long initMs,totalMs,peakPss,basePss,afterPss,peakNative,peakJava;
        final ArrayList<Row> rows; final String error;
        Trial(String e,String v,int r,long init,long total,long peak,long base,long after,long nativeMem,long javaMem,ArrayList<Row> rows,String err){
            engine=e;variant=v;repeat=r;initMs=init;totalMs=total;peakPss=peak;basePss=base;afterPss=after;peakNative=nativeMem;peakJava=javaMem;this.rows=rows;error=err;
        }
    }
    private static final class Row {
        final Item item; final String sha,raw,normalized,error; final long decode,ocr,norm,total; final int width,height;
        Row(Item i,String sha,String raw,String norm,String error,long dec,long ocr,long n,long total,int w,int h){
            item=i;this.sha=sha;this.raw=raw;normalized=norm;this.error=error;decode=dec;this.ocr=ocr;this.norm=n;this.total=total;width=w;height=h;
        }
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(0xff33232b);t.setPadding(0,dp(4),0,dp(4));t.setTextIsSelectable(true);return t;}
    private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);return s;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(cherry);
        ScrollView scroll=new ScrollView(this);body=column();body.setPadding(dp(14),dp(12),dp(14),dp(20));body.setBackgroundColor(0xfffff2f6);scroll.addView(body);setContentView(scroll);
        if(android.os.Build.VERSION.SDK_INT>=30){
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
                return insets;
            });
            scroll.requestApplyInsets();
        }
        TextView title=label("Cherry OCR Test • Accuracy First",23);title.setTextColor(cherry);title.setTypeface(null,Typeface.BOLD);body.addView(title);
        body.addView(label("သီးခြားစမ်းသပ်မှု • Offline • စာရင်းမသွင်းပါ",14));
        fixture=button("မူရင်း စမ်းသပ်ပုံ 18 ပုံ ပြန်သုံးမည်",()->loadFixture(null));body.addView(fixture);
        fixtureImport=button("18 ပုံ ZIP တစ်ကြိမ်ထည့်မည်",this::pickFixtureZip);body.addView(fixtureImport);
        pick=button("Screenshot ရွေးမည်",this::pickImages);body.addView(pick);
        samples=button("နမူနာပုံ 20 ခု ဖန်တီးမည်",this::generateSamples);body.addView(samples);
        clear=button("ရွေးထားသောပုံများ ရှင်းမည်",()->{images.clear();trials.clear();renderSelection();summary.removeAllViews();results.removeAllViews();});body.addView(clear);
        selected=column();body.addView(selected);
        body.addView(label("OCR Engine",16));engine=spinner(ENGINES);engine.setSelection(4);body.addView(engine);
        body.addView(label("Image preprocessing",16));variant=spinner(new String[]{"Compare all supported variants",VARIANTS[0],VARIANTS[1],VARIANTS[2],VARIANTS[3],VARIANTS[4]});body.addView(variant);
        body.addView(label("ပုံအရေအတွက်",16));batch=spinner(new String[]{"Selected all","1","5","10","20","50"});body.addView(batch);
        body.addView(label("Repeat (engine reinitialized each trial)",14));repeats=spinner(new String[]{"1","3","5"});body.addView(repeats);
        run=button("စမ်းသပ်မည်",this::start);body.addView(run);
        cancel=button("ရပ်မည်",()->{stop.set(true);status.setText("ရပ်ရန် တောင်းဆိုထားသည်။ လက်ရှိပုံပြီးမှ ရပ်မည်။");});body.addView(cancel);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);body.addView(progress);
        status=label("ပုံရွေးပါ။",15);body.addView(status);
        export=button("JSON ရလဒ် သိမ်းမည်",this::requestExport);body.addView(export);
        summary=column();body.addView(summary);results=column();body.addView(results);renderSelection();setBusy(false);
    }
    private void setBusy(boolean value){
        busy=value;fixture.setEnabled(!value);fixtureImport.setEnabled(!value);pick.setEnabled(!value);samples.setEnabled(!value);clear.setEnabled(!value);run.setEnabled(!value);engine.setEnabled(!value);variant.setEnabled(!value);batch.setEnabled(!value);repeats.setEnabled(!value);cancel.setEnabled(value);export.setEnabled(!value&&!trials.isEmpty());
        if(value)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void pickFixtureZip(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,FIXTURE_ZIP);
    }
    private String digest(byte[] bytes)throws Exception{
        StringBuilder s=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();
    }
    private byte[] bounded(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("Fixture entry too large");out.write(buf,0,n);}return out.toByteArray();
    }
    private void retainFixture(File dir,byte[] data)throws Exception{
        String hash=digest(data);if(!Arrays.asList(FIXTURE_HASHES).contains(hash))return;
        File dest=new File(dir,hash+".png");File tmp=new File(dir,hash+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);}
        if(dest.exists()&&!dest.delete())throw new IOException("Cannot replace fixture");
        if(!tmp.renameTo(dest))throw new IOException("Cannot retain fixture");
    }
    private void loadFixture(Uri zip){
        setBusy(true);status.setText("18 ပုံ မူရင်းနှင့် တိုက်စစ်နေသည်");
        worker.execute(()->{
            ArrayList<Item> found=new ArrayList<>();String error=null;
            try{
                File dir=new File(getFilesDir(),"regression18");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Fixture folder");
                if(zip!=null){
                    try(java.util.zip.ZipInputStream zin=new java.util.zip.ZipInputStream(stream(zip))){
                        java.util.zip.ZipEntry e;int count=0;
                        while((e=zin.getNextEntry())!=null){if(++count>100)throw new IOException("Too many ZIP entries");if(!e.isDirectory())retainFixture(dir,bounded(zin));zin.closeEntry();}
                    }
                }else{
                    for(android.content.UriPermission grant:getContentResolver().getPersistedUriPermissions()){
                        if(!grant.isReadPermission())continue;
                        try(InputStream in=stream(grant.getUri())){retainFixture(dir,bounded(in));}catch(Exception ignored){}
                    }
                }
                for(int i=0;i<FIXTURE_HASHES.length;i++){String hash=FIXTURE_HASHES[i];File f=new File(dir,hash+".png");
                    if(f.isFile()&&hash.equals(sha(Uri.fromFile(f))))found.add(new Item(Uri.fromFile(f),"regression-"+(i+1),null));
                }
            }catch(Exception e){error=e.getMessage();}
            final String failure=error;
            runOnUiThread(()->{setBusy(false);if(found.size()==18&&failure==null){
                images.clear();images.addAll(found);trials.clear();summary.removeAllViews();results.removeAllViews();renderSelection();status.setText("18/18 • SHA-256 verified • Ready");
            }else status.setText("မူရင်းပုံ "+found.size()+"/18 • ZIP တစ်ကြိမ်ထည့်ပါ"+(failure==null?"":" • "+failure));});
        });
    }
    private void pickImages(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(request==FIXTURE_ZIP&&data.getData()!=null){loadFixture(data.getData());return;}
        if(request==PICK){
            ArrayList<Uri> uris=new ArrayList<>();
            if(data.getClipData()!=null)for(int j=0;j<data.getClipData().getItemCount();j++)uris.add(data.getClipData().getItemAt(j).getUri());
            else if(data.getData()!=null)uris.add(data.getData());
            for(Uri uri:uris){if(images.size()>=100)break;if(images.stream().anyMatch(it->it.uri.equals(uri)))continue;try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}images.add(new Item(uri,"image-"+UUID.randomUUID(),null));}
            renderSelection();
        }else if(request==EXPORT&&data.getData()!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("Cannot open export");out.write(exportJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));status.setText("JSON သိမ်းပြီး");}catch(Exception e){status.setText("Export failed: "+e.getMessage());}}
    }
    private void renderSelection(){
        selected.removeAllViews();selected.addView(label("ရွေးထားသောပုံ "+images.size()+" ပုံ • နှိပ်၍ မူရင်းစာသားထည့်ရန်",14));
        for(int k=0;k<images.size();k++){final Item item=images.get(k);final int index=k;Button b=button((k+1)+". "+(item.truth==null?"Ground truth မရှိ":"Ground truth ရှိ"),()->{if(!busy)truthDialog(item,index);});selected.addView(b);}
        export.setEnabled(!busy&&!trials.isEmpty());
    }
    private void truthDialog(Item item,int index){
        LinearLayout l=column();ImageView view=new ImageView(this);view.setAdjustViewBounds(true);view.setMaxHeight(dp(200));l.addView(view);
        worker.execute(()->{try{Bitmap bmp=decode(item.uri,true);runOnUiThread(()->view.setImageBitmap(bmp));}catch(Exception ignored){}});
        EditText t=new EditText(this);t.setMinLines(3);t.setHint("ပုံထဲက စာသားအတိအကျ");t.setText(item.truth==null?"":item.truth);l.addView(t);
        new AlertDialog.Builder(this).setTitle("ပုံ "+(index+1)).setView(l).setPositiveButton("သိမ်းမည်",(d,w)->{item.truth=t.getText().toString().isEmpty()?null:t.getText().toString();renderSelection();}).setNeutralButton("ဖယ်မည်",(d,w)->{images.remove(item);renderSelection();}).setNegativeButton("ပိတ်",null).show();
    }
    private void generateSamples(){
        setBusy(true);status.setText("နမူနာပုံများ ဖန်တီးနေသည်");
        worker.execute(()->{
            String[] corpus={"456ခွေပူး1000","20.39R2000","9ပါ1000","38ဘရိတ်2000","7ထိပ်1000","6ပိတ်500","၀၈ ၁၀၀၀","08 500\n09 1000","00 500\n00 500","၁၂.၃၄ R ၂၀၀၀","23 BK 1000","3 ပူး 500","12 100\n34 200\n56 300","12 100\nSeen","09:30\n07 1000","၀၀ ၅၀၀\n၉၉ ၁၀၀၀","9 အာ 1000","456 ခွေ 2000","02R 1500\n20R 2500","၁၂ ၁၀၀\n၁၂ ၁၀၀"};
            ArrayList<Item> made=new ArrayList<>();String err=null;
            try{
                File dir=new File(getCacheDir(),"synthetic");if(!dir.exists()&&!dir.mkdirs())throw new IOException("sample dir");
                for(int k=0;k<corpus.length;k++){
                    boolean dark=k%2==1;String[] lines=corpus[k].split("\\n",-1);
                    Bitmap bmp=Bitmap.createBitmap(720,90+lines.length*64,Bitmap.Config.ARGB_8888);
                    try{Canvas c=new Canvas(bmp);c.drawColor(dark?0xff20242a:0xffe8f4ff);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(dark?0xfffafafa:0xff171717);p.setTextSize(38);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));for(int z=0;z<lines.length;z++)c.drawText(lines[z],30,65+64*z,p);File f=new File(dir,"sample-"+k+".png");try(OutputStream out=new FileOutputStream(f)){if(!bmp.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG");}made.add(new Item(Uri.fromFile(f),"synthetic-"+(k+1),corpus[k]));}finally{bmp.recycle();}
                }
            }catch(Exception e){err=e.toString();}
            String failure=err;runOnUiThread(()->{images.clear();images.addAll(made);renderSelection();setBusy(false);status.setText(failure==null?"Synthetic samples ready (not real chat screenshots)":failure);});
        });
    }
    private InputStream stream(Uri u)throws IOException{InputStream in=getContentResolver().openInputStream(u);if(in==null)throw new IOException("Unreadable image");return in;}
    private Bitmap decode(Uri uri,boolean thumb)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=stream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Unsupported image");
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        if(thumb){int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>900)sample*=2;opts.inSampleSize=sample;}
        else if((long)bounds.outWidth*bounds.outHeight>12000000L||Math.max(bounds.outWidth,bounds.outHeight)>6000)throw new IOException("Image too large; crop screenshot first");
        try(InputStream in=stream(uri)){Bitmap b=BitmapFactory.decodeStream(in,null,opts);if(b==null)throw new IOException("Decode failed");return b;}
    }
    private String sha(Uri uri)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=stream(uri)){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)md.update(buf,0,n);}StringBuilder s=new StringBuilder();for(byte v:md.digest())s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    private long now(){return SystemClock.elapsedRealtime();}
    private void start(){
        if(images.isEmpty()){status.setText("ပုံရွေးပါ။");return;}
        int mode=engine.getSelectedItemPosition();
        int n=batch.getSelectedItemPosition()==0?images.size():Integer.parseInt(batch.getSelectedItem().toString());if(images.size()<n){status.setText(n+" ပုံလိုသည်။ "+images.size()+" ပုံသာ ရှိသည်။");return;}
        int repeat=Integer.parseInt(repeats.getSelectedItem().toString());ArrayList<Item> input=new ArrayList<>();for(Item i:images.subList(0,n))input.add(new Item(i.uri,i.id,i.truth));
        int[] modes=mode==4?new int[]{0,1,2,3}:new int[]{mode};
        int vi=variant.getSelectedItemPosition();int[] variants=vi==0?new int[]{0,1,2,3,4}:new int[]{vi-1};
        int count=0;for(int m:modes)for(int v:variants)if(m<2||v<3)count++;
        if(count==0){status.setText("Layout variants are Tesseract only");return;}
        trials.clear();summary.removeAllViews();results.removeAllViews();stop.set(false);setBusy(true);progress.setMax(n*repeat*count);progress.setProgress(0);
        worker.execute(()->{int done=0;try{for(int m:modes)for(int v:variants)for(int r=1;r<=repeat&&!stop.get();r++){
            if(m>=2&&v>=3)continue;
            final int base=done;Trial t=trial(m,v,r,input,base);done+=n;runOnUiThread(()->{trials.add(t);renderTrial(t);renderSummary();});
        }}finally{runOnUiThread(()->{setBusy(false);status.setText(stop.get()?"Cancelled — partial outcomes retained":"ပြီးပါပြီ • JSON ရလဒ် သိမ်းနိုင်သည်");});}});
    }
    private File prepare(String kind)throws Exception{
        File root=new File(getFilesDir(),kind);File dir=new File(root,"tessdata");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Model directory");
        for(String name:new String[]{"mya.traineddata","eng.traineddata"}){
            File f=new File(dir,name);if(!f.exists()){File tmp=new File(dir,name+".tmp");try(InputStream in=getAssets().open(kind+"/tessdata/"+name);OutputStream out=new FileOutputStream(tmp)){byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}if(!tmp.renameTo(f))throw new IOException("Model extraction");}
        }return root;
    }
    private long pss(){Debug.MemoryInfo info=new Debug.MemoryInfo();Debug.getMemoryInfo(info);return info.getTotalPss();}
    private Trial trial(int mode,int v,int repeat,List<Item> input,int base){
        String name=ENGINES[mode];long start=now(),init=0,baseMem=pss();long[] peak={baseMem,0,0};ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(()->{synchronized(peak){peak[0]=Math.max(peak[0],pss());peak[1]=Math.max(peak[1],Debug.getNativeHeapAllocatedSize());Runtime rt=Runtime.getRuntime();peak[2]=Math.max(peak[2],rt.totalMemory()-rt.freeMemory());}},0,150,TimeUnit.MILLISECONDS);
        TessBaseAPI tess=null;TextRecognizer ml=null;CrnnEngine crnn=null;ArrayList<Row> rows=new ArrayList<>();String failure=null;
        try{
            if(mode==2)ml=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            else if(mode==3)crnn=new CrnnEngine(getAssets());
            else{File root=prepare(mode==0?"fast":"best");tess=new TessBaseAPI();if(!tess.init(root.getAbsolutePath(),"mya+eng",TessBaseAPI.OEM_LSTM_ONLY))throw new IOException("Tesseract init failed");tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);}
            init=now()-start;
            for(int k=0;k<input.size();k++){
                Item item=input.get(k);if(stop.get()){rows.add(new Row(item,"","","","CANCELLED",0,0,0,0,0,0));continue;}
                final int pos=k;runOnUiThread(()->status.setText(name+" • "+(pos+1)+"/"+input.size()+" • trial "+repeat));
                long begin=now(),dec=0,ocr=0,norm=0;String hash="",raw="",normalized="",err=null;Bitmap bitmap=null,processed=null;int w=0,h=0;
                try{hash=sha(item.uri);long d=now();bitmap=decode(item.uri,false);dec=now()-d;w=bitmap.getWidth();h=bitmap.getHeight();long o=now();
                    processed=ImageVariants.prepare(bitmap,v>=3?1:v);
                    if(tess!=null){if(v==3)tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);else if(v==4)tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);tess.setImage(processed);try{raw=tess.getUTF8Text();}finally{tess.clear();}}
                    else if(crnn!=null)raw=crnn.read(processed);
                    else raw=Tasks.await(ml.process(InputImage.fromBitmap(processed,0))).getText();
                    ocr=now()-o;if(raw==null||raw.trim().isEmpty()){raw=raw==null?"":raw;err="EMPTY";}
                    long z=now();normalized=TextMetrics.normalize(raw);norm=now()-z;
                }catch(Exception|LinkageError|OutOfMemoryError e){err=e.getClass().getSimpleName()+": "+e.getMessage();}
                finally{if(processed!=null&&processed!=bitmap)processed.recycle();if(bitmap!=null)bitmap.recycle();}
                rows.add(new Row(item,hash,raw,normalized,err,dec,ocr,norm,now()-begin,w,h));final int p=base+k+1;runOnUiThread(()->progress.setProgress(p));
            }
        }catch(Exception|LinkageError|OutOfMemoryError e){failure=e.getClass().getSimpleName()+": "+e.getMessage();init=now()-start;}
        finally{try{if(tess!=null)tess.recycle();}finally{try{if(ml!=null)ml.close();if(crnn!=null)crnn.close();}catch(Exception e){failure="Cleanup: "+e.getMessage();}finally{sampler.shutdownNow();}}}
        for(int k=rows.size();k<input.size();k++)rows.add(new Row(input.get(k),"","","",failure==null?"NOT_RUN":failure,0,0,0,0,0,0));
        long[] measured; synchronized(peak){measured=peak.clone();}return new Trial(name,VARIANTS[v],repeat,init,now()-start,measured[0],baseMem,pss(),measured[1],measured[2],rows,failure);
    }
    private void renderSummary(){
        summary.removeAllViews();
        TextView title=label("Accuracy comparison • Annotated images only",18);title.setTextColor(cherry);summary.addView(title);
        Map<String,List<Row>> byImage=new LinkedHashMap<>();
        for(Trial t:trials){
            int scored=0,exact=0,numeric=0,read=0;
            for(Row r:t.rows){
                if(r.error==null)read++;
                if(r.item.truth!=null){scored++;String truth=TextMetrics.normalize(r.item.truth);
                    if(r.error==null&&truth.equals(r.normalized))exact++;
                    if(r.error==null&&TextMetrics.numericTokens(truth).equals(TextMetrics.numericTokens(r.normalized)))numeric++;
                }
                byImage.computeIfAbsent(r.item.id,k->new ArrayList<>()).add(r);
            }
            summary.addView(label(t.engine+" / "+t.variant+" #"+t.repeat+"\nRead "+read+"/"+t.rows.size()+" • Exact "+(scored==0?"—":exact+"/"+scored)+" • Numeric tokens "+(scored==0?"—":numeric+"/"+scored)+" • "+t.totalMs+" ms",13));
        }
        summary.addView(label("Numeric token match is not amount/format validation. Review original image; no automatic winner.",13));
        int number=0;
        for(Map.Entry<String,List<Row>> e:byImage.entrySet()){
            int index=++number;
            Set<String> values=new HashSet<>();
            for(Row r:e.getValue())values.add(r.error==null?"READ:"+r.normalized:"ERROR:"+r.error);
            if(values.size()>1)summary.addView(button("ပုံ "+index+" • ဖတ်ချက်ကွဲလွဲ",()->compareImage(e.getKey(),index)));
        }
    }
    private void compareImage(String id,int index){
        LinearLayout l=column();Row first=null;
        for(Trial t:trials)for(Row r:t.rows)if(r.item.id.equals(id)){
            if(first==null)first=r;
            l.addView(label(t.engine+" / "+t.variant+" #"+t.repeat+"\n"+(r.error==null?r.raw:r.error),15));
        }
        if(first!=null){final Row image=first;ImageView im=new ImageView(this);im.setAdjustViewBounds(true);im.setMaxHeight(dp(300));l.addView(im,0);
            worker.execute(()->{try{Bitmap bmp=decode(image.item.uri,true);runOnUiThread(()->im.setImageBitmap(bmp));}catch(Exception ignored){}});
            l.addView(label("Ground truth: "+(image.item.truth==null?"Not annotated":image.item.truth),14),1);
        }
        ScrollView sc=new ScrollView(this);sc.addView(l);new AlertDialog.Builder(this).setTitle("ပုံ "+index+" • Compare").setView(sc).setPositiveButton("ပိတ်",null).show();
    }
    private void renderTrial(Trial t){
        int ok=0,scored=0,exact=0;long sum=0;ArrayList<Long> timings=new ArrayList<>();
        for(Row r:t.rows){if(r.error==null)ok++;if(r.total>0){sum+=r.total;timings.add(r.total);}if(r.item.truth!=null){scored++;if(r.error==null&&TextMetrics.normalize(r.item.truth).equals(r.normalized))exact++;}}
        Collections.sort(timings);long p90=timings.isEmpty()?0:timings.get((int)Math.ceil(timings.size()*.9)-1);
        TextView heading=label(t.engine+" • "+t.variant+" • Trial "+t.repeat,18);heading.setTextColor(cherry);results.addView(heading);
        results.addView(label("Read "+ok+"/"+t.rows.size()+" • Init "+t.initMs+" ms • Wall "+t.totalMs+" ms\nMean attempted "+(timings.isEmpty()?"—":sum/timings.size()+" ms")+" • p90 "+(timings.isEmpty()?"—":p90+" ms")+"\nExact normalized "+(scored==0?"Not scored":exact+"/"+scored)+" • Peak sampled PSS "+t.peakPss+" KB",14));
        if(t.error!=null)results.addView(label(t.error,13));
        for(int j=0;j<t.rows.size();j++){Row r=t.rows.get(j);int number=j+1;results.addView(button("ပုံ "+number+" • "+(r.error==null?r.total+" ms":r.error),()->showResult(r,number)));}
    }
    private void showResult(Row r,int number){
        LinearLayout l=column();ImageView im=new ImageView(this);im.setAdjustViewBounds(true);im.setMaxHeight(dp(240));l.addView(im);worker.execute(()->{try{Bitmap b=decode(r.item.uri,true);runOnUiThread(()->im.setImageBitmap(b));}catch(Exception ignored){}});
        l.addView(label("Raw OCR\n"+r.raw,16));l.addView(label("Normalized\n"+r.normalized,16));l.addView(label("Ground truth\n"+(r.item.truth==null?"Not annotated":r.item.truth),14));l.addView(label("Decode "+r.decode+" ms • OCR "+r.ocr+" ms • Normalize "+r.norm+" ms",13));
        if(r.error!=null)l.addView(label(r.error,14));ScrollView sc=new ScrollView(this);sc.addView(l);new AlertDialog.Builder(this).setTitle("ပုံ "+number).setView(sc).setPositiveButton("ပိတ်",null).show();
    }
    private void requestExport(){
        try{
            JSONObject root=new JSONObject();root.put("schema",1).put("device",android.os.Build.MODEL).put("sdk",android.os.Build.VERSION.SDK_INT).put("abis",new JSONArray(Arrays.asList(android.os.Build.SUPPORTED_ABIS))).put("apk_bytes",new File(getApplicationInfo().sourceDir).length()).put("tesseract_version","4.9.0").put("mlkit_version","16.0.1").put("crnn_model_revision","456fa72d49996f1542fa7a957fd41c348f27ddca").put("onnxruntime_version","1.22.0").put("crnn_conversion","ConvInteger float accumulator compatibility rewrite v1").put("protocol","Each trial initializes a new engine; first inference may include lazy initialization; no fresh-process cold-run claim.");
            try(InputStream in=getAssets().open("models.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);root.put("models",new JSONArray(out.toString("UTF-8")));}
            JSONArray ts=new JSONArray();
            for(Trial t:trials){JSONObject tj=new JSONObject().put("engine",t.engine).put("variant",t.variant).put("repeat",t.repeat).put("init_ms",t.initMs).put("wall_ms",t.totalMs).put("base_pss_kb",t.basePss).put("sampled_peak_pss_kb",t.peakPss).put("after_pss_kb",t.afterPss).put("sampled_peak_native_bytes",t.peakNative).put("sampled_peak_java_bytes",t.peakJava).put("error",t.error==null?JSONObject.NULL:t.error);JSONArray rs=new JSONArray();
                for(Row r:t.rows){JSONObject j=new JSONObject().put("id",r.item.id).put("sha256",r.sha).put("raw",r.raw).put("normalized",r.normalized).put("truth",r.item.truth==null?JSONObject.NULL:r.item.truth).put("status",r.error==null?"READ":r.error).put("decode_ms",r.decode).put("ocr_ms",r.ocr).put("normalize_ms",r.norm).put("total_ms",r.total).put("width",r.width).put("height",r.height);
                    if(r.item.truth!=null){String nt=TextMetrics.normalize(r.item.truth);j.put("raw_edit_distance",TextMetrics.distance(r.item.truth,r.raw)).put("raw_truth_codepoints",r.item.truth.codePointCount(0,r.item.truth.length())).put("normalized_edit_distance",TextMetrics.distance(nt,r.normalized)).put("normalized_truth_codepoints",nt.codePointCount(0,nt.length())).put("normalized_exact",r.error==null&&nt.equals(r.normalized)).put("ordered_line_edit_distance",TextMetrics.lineDistance(nt,r.normalized)).put("numeric_tokens_exact",r.error==null&&TextMetrics.numericTokens(nt).equals(TextMetrics.numericTokens(r.normalized)));}rs.put(j);}
                tj.put("actual_selected_count",t.rows.size()).put("images",rs);ts.put(tj);}
            root.put("trials",ts);exportJson=root.toString(2);
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"cherry-ocr-benchmark.json");startActivityForResult(i,EXPORT);
        }catch(Exception e){status.setText("Export preparation failed: "+e.getMessage());}
    }
    @Override protected void onDestroy(){stop.set(true);worker.shutdown();super.onDestroy();}
}
