package com.myanmar.cherrydirectcapture;
import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Standalone only: compare candidate text with authorized dummy chats before transfer. */
public final class BatchActivity extends Activity {
    private CaptureStore store;
    private LinearLayout panel;
    private ScrollView scroll;
    private int page=0, active=0;
    private boolean excluded=false, debug=false;
    private String batch="";
    private static final int CHERRY=Color.rgb(181,27,70);
    @Override public void onCreate(Bundle b){
        super.onCreate(b);store=new CaptureStore(this);
        if(b!=null){page=b.getInt("page");active=b.getInt("active");}
        scroll=new ScrollView(this);panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(24,32,24,32);scroll.addView(panel);setContentView(scroll);
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("page",page);b.putInt("active",active);}
    @Override protected void onResume(){super.onResume();CaptureService.activityVisible(true);batch=store.latestBatch();
        if(!CaptureService.armed()&&store.batchState(batch)==0)store.stopBatch(batch);
        if(page==0&&store.batchState(batch)==1)page=1;
        draw();}
    @Override protected void onPause(){CaptureService.activityVisible(false);super.onPause();}
    @Override protected void onDestroy(){store.close();super.onDestroy();}
    private void label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(0,12,0,12);t.setTextIsSelectable(true);panel.addView(t);}
    private Button make(String s,View.OnClickListener f){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setOnClickListener(f);return b;}
    private void button(String s,View.OnClickListener f){panel.addView(make(s,f));}
    private void error(Exception e){new AlertDialog.Builder(this).setMessage("မလုပ်နိုင်ပါ။ စာသားနှင့် အခြေအနေ ပြန်စစ်ပါ။").setPositiveButton("OK",null).show();}
    private void draw(){
        panel.removeAllViews();label("🍒 Direct Capture Test 12",24);
        if(page==2){draft();return;}
        if(page==1&&!CaptureService.armed()&&store.batchState(batch)==1){review();return;}
        label("စမ်းသပ် Chat များသာ • စာရင်းအစစ် မသိမ်းပါ",16);
        if(!CaptureService.connected())button("Accessibility Setting",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        if(CaptureService.armed()){
            label("ဖမ်းယူမှု "+CaptureService.sessionSaved()+" ကြိမ်",16);
            button("Stop",v->{CaptureService.endSession();page=1;draw();});
        }else{
            button("Import",v->chooseApp());
            if(store.batchState(batch)==1)button("ယခုစာများ ပြန်ကြည့်ရန်",v->{page=1;draw();});
        }
        button("Test Input Box",v->{page=2;draw();});
        button(debug?"စမ်းသပ်မှတ်တမ်း ပိတ်မည်":"စမ်းသပ်မှတ်တမ်း",v->{debug=!debug;draw();});
        if(debug){
            label("Service: "+CaptureService.diagnostic(this),14);
            label("Capture: "+CaptureService.captureDiagnostic(this),14);
            label("Overlay: "+CaptureService.overlayDiagnostic(this),14);
            button("Raw Data",v->{try{label(store.batchRaw(batch).toString(2),12);}catch(Exception e){error(e);}});
            button("Build 11 မှတ်တမ်းများ",v->{try{label(store.page(0).toString(2),12);label("Legacy draft: "+store.draft(),14);}catch(Exception e){error(e);}});
            button("Overlay သီးသန့်စမ်းမည်",v->{CaptureService.testOverlay();draw();});
            button("စမ်းသပ် Data အားလုံး ဖျက်မည်",v->new AlertDialog.Builder(this).setMessage("မှတ်တမ်းအားလုံး ဖျက်မလား?").setNegativeButton("မဖျက်ပါ",null).setPositiveButton("ဖျက်မည်",(d,w)->{CaptureService.endSession();store.clear();batch="";active=0;draw();}).show());
        }
    }
    private void chooseApp(){
        if(store.batchState(batch)==1){page=1;draw();return;}
        new AlertDialog.Builder(this).setTitle("Import").setItems(new String[]{"Viber","Messenger"},(d,i)->startCapture(i==0?"com.viber.voip":"com.facebook.orca")).setNegativeButton("မလုပ်တော့ပါ",null).show();
    }
    private void startCapture(String pkg){
        if(!CaptureService.connected()){startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}
        new AlertDialog.Builder(this).setTitle("စာဖမ်းယူရန်").setMessage("ရည်ရွယ်ထားသော စမ်းသပ် Chat တစ်ခုကိုသာ ဖွင့်ပါ။ 🍒 နှိပ်မှ စာဖမ်းမည်။ Chat မပြောင်းပါနှင့်။")
          .setNegativeButton("မစတင်ပါ",null).setPositiveButton("စတင်မည်",(d,w)->{
            try{
                if(!CaptureService.beginSession(pkg))throw new IllegalStateException("Overlay unavailable");
                batch=store.latestBatch();page=0;draw();
                Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
                if(launch!=null)startActivity(launch);else label("ရွေးထားသော App ကို ကိုယ်တိုင် ဖွင့်ပါ။",16);
            }catch(Exception e){error(e);}
          }).show();
    }
    private void review(){
        try{
            JSONArray rows=store.review(batch);int count=0,hidden=0;
            for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optBoolean("include"))count++;else hidden++;
            label("စာဖမ်းယူမှု — "+(store.batchPackage(batch).equals("com.viber.voip")?"Viber":"Messenger"),22);
            label("စာသားအပိုင်း "+count+" ခု • မူရင်း Chat နှင့် စာစောင်၊ အစီအစဉ်၊ ထပ်နေမှု တိုက်စစ်ပါ။",16);
            label("ယခု build တွင် စာစောင်အစစ် အလိုအလျောက် အတည်မပြုနိုင်သေးပါ။",14);
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);boolean included=row.optBoolean("include");
                if(!included&&!excluded)continue;
                final int idx=i;
                label(row.getString("body"),18);
                if(row.optBoolean("repeat"))label("စာသားတူနေသည် — မူရင်းနှင့် တိုက်စစ်ရန်",13);
                if(!included)label("ချန်ထားသောစာသား • "+row.optString("kind"),13);
                LinearLayout actions=new LinearLayout(this);
                actions.addView(make("ပြင်ရန်",v->editReview(rows,idx)));
                actions.addView(make(included?"ဖယ်ရန်":"ထည့်ရန်",v->mutate(rows,()->row.put("include",!included))));
                if(included&&i>0)actions.addView(make("↑",v->mutate(rows,()->{JSONObject prev=rows.getJSONObject(idx-1);rows.put(idx-1,row);rows.put(idx,prev);})));
                if(included&&i+1<rows.length())actions.addView(make("↓",v->mutate(rows,()->{JSONObject next=rows.getJSONObject(idx+1);rows.put(idx+1,row);rows.put(idx,next);})));
                panel.addView(actions);
            }
            button((excluded?"ချန်ထားသောစာ ပိတ်ရန်":"ချန်ထားသောစာ ကြည့်ရန်")+" ("+hidden+")",v->{excluded=!excluded;draw();});
            button("ပြင်ဆင်စာစောင် ထပ်ထည့်ရန်",v->editReview(rows,-1));
            final int total=count, skipped=hidden;
            Button confirm=make("စာသားအတည်ပြုပြီး Test Input Box ထဲထည့်မည်",v->new AlertDialog.Builder(this)
                .setTitle("မူရင်း Chat နှင့် တိုက်စစ်ပြီးပြီလား?")
                .setMessage("စာစောင် "+total+" ခု၏ စာသား၊ အစီအစဉ်၊ ထပ်နေမှုနှင့် ချန်ထားသော "+skipped+" ခုကို အားလုံးတိုက်စစ်ပြီး တစ်စောင်ချင်း မှန်ကန်မှ အတည်ပြုပါ။")
                .setNegativeButton("ပြန်စစ်မည်",null).setPositiveButton("အတည်ပြုမည်",(d,w)->{
                    try{store.transferBatch(batch);page=2;active=Math.max(0,store.entries().length()-total);draw();}catch(Exception e){error(e);}
                }).show());
            confirm.setEnabled(count>0);panel.addView(confirm);
            button("မပို့ဘဲ ပယ်ဖျက်မည်",v->new AlertDialog.Builder(this).setMessage("ယခု session ကို မပို့ဘဲ ပယ်ဖျက်မလား?").setNegativeButton("မဖျက်ပါ",null).setPositiveButton("ပယ်ဖျက်မည်",(d,w)->{store.discardBatch(batch);page=0;draw();}).show());
            button("Test Input Box ကြည့်ရန်",v->{page=2;draw();});
        }catch(Exception e){error(e);button("ပြန်သွားရန်",v->{page=0;draw();});}
    }
    private interface Change {void run() throws Exception;}
    private void mutate(JSONArray rows,Change change){try{change.run();store.saveReview(batch,rows);draw();}catch(Exception e){error(e);}}
    private void editReview(JSONArray rows,int i){
        try{
            EditText input=new EditText(this);input.setMinLines(3);
            if(i>=0)input.setText(rows.getJSONObject(i).getString("body"));
            new AlertDialog.Builder(this).setTitle("စာသား ပြင်ဆင်ရန်").setView(input).setNegativeButton("မပြင်ပါ",null).setPositiveButton("သိမ်းမည်",(d,w)->mutate(rows,()->{
                String body=input.getText().toString();BatchLogic.requireBody(body);
                if(i>=0)rows.getJSONObject(i).put("body",body);
                else{JSONObject row=new JSONObject();row.put("source","manual-correction");row.put("original","");row.put("body",body);row.put("kind","MANUAL_CORRECTION");row.put("include",true);rows.put(row);}
            })).show();
        }catch(Exception e){error(e);}
    }
    private void draft(){
        try{
            JSONArray rows=store.entries();active=Math.max(0,Math.min(active,rows.length()-1));
            label("Test Input Box • "+rows.length()+" စောင်",22);
            HorizontalScrollView hsv=new HorizontalScrollView(this);LinearLayout tabs=new LinearLayout(this);
            for(int i=0;i<rows.length();i++){final int idx=i;Button b=make(""+(i+1),v->{active=idx;draw();});if(i==active)b.setTextColor(CHERRY);tabs.addView(b);}
            tabs.addView(make("+",v->editEntry(-1,"")));hsv.addView(tabs);panel.addView(hsv);
            if(rows.length()>0){JSONObject row=rows.getJSONObject(active);long id=row.getLong("id");String body=row.getString("body");label(body,18);
                button("ပြင်ရန်",v->editEntry(id,body));
                button("ဖယ်ရန်",v->new AlertDialog.Builder(this).setMessage("ယခုစာကို ဖယ်ရှားမလား?").setNegativeButton("မဖယ်ပါ",null).setPositiveButton("ဖယ်မည်",(d,w)->{store.deleteDraft(id);draw();}).show());}
            if(store.batchState(batch)==1)button("စာဖမ်းယူမှု ပြန်စစ်ရန်",v->{page=1;draw();});
            else if(!CaptureService.armed())button("Import",v->chooseApp());
            button("ပြန်သွားရန်",v->{page=0;draw();});
        }catch(Exception e){error(e);}
    }
    private void editEntry(long id,String body){
        EditText input=new EditText(this);input.setMinLines(4);input.setText(body);
        new AlertDialog.Builder(this).setTitle("စာရင်းထည့်ရန်").setView(input).setNegativeButton("မသိမ်းပါ",null).setPositiveButton("သိမ်းမည်",(d,w)->{
            try{String value=input.getText().toString();if(id<0){store.addManual(value);active=store.entries().length()-1;}else store.editDraft(id,value);draw();}catch(Exception e){error(e);}
        }).show();
    }
}
