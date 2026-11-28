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
    private String exportBatch="";
    private static final int EXPORT_REQUEST=701;
    static final String REVIEW_ACTION="com.myanmar.cherrydirectcapture.REVIEW_STOPPED";
    private static final int CHERRY=Color.rgb(181,27,70);
    @Override public void onCreate(Bundle b){
        super.onCreate(b);store=new CaptureStore(this);
        if(b!=null){page=b.getInt("page");active=b.getInt("active");exportBatch=b.getString("exportBatch","");}
        scroll=new ScrollView(this);panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(24,32,24,32);scroll.addView(panel);setContentView(scroll);
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("page",page);b.putInt("active",active);b.putString("exportBatch",exportBatch);}
    @Override protected void onResume(){super.onResume();CaptureService.activityVisible(true);batch=store.latestBatch();
        if(!CaptureService.armed()&&store.batchState(batch)==0)store.stopBatch(batch);
        if(REVIEW_ACTION.equals(getIntent().getAction())){
            String requested=getIntent().getStringExtra("review_batch");
            if(requested!=null&&requested.equals(batch)&&store.batchState(batch)==1&&!CaptureService.armed()){
                page=1;getSharedPreferences("capture_health",MODE_PRIVATE).edit().putString("return","review resumed").apply();
            }
            getIntent().setAction(null);
        }
        if(page==0&&store.batchState(batch)==1&&!CaptureService.armed())page=1;
        draw();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);page=0;}
    @Override protected void onPause(){CaptureService.activityVisible(false);super.onPause();}
    @Override protected void onDestroy(){store.close();super.onDestroy();}
    private void label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(0,12,0,12);t.setTextIsSelectable(true);panel.addView(t);}
    private Button make(String s,View.OnClickListener f){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setOnClickListener(f);return b;}
    private void button(String s,View.OnClickListener f){panel.addView(make(s,f));}
    private void error(Exception e){new AlertDialog.Builder(this).setMessage("မလုပ်နိုင်ပါ။ စာသားနှင့် အခြေအနေ ပြန်စစ်ပါ။").setPositiveButton("OK",null).show();}
    private void draw(){
        panel.removeAllViews();label("🍒 Direct Capture Test 15",24);
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
            label("Return: "+getSharedPreferences("capture_health",MODE_PRIVATE).getString("return","not tested"),14);
            button("Raw Data",v->{try{label(store.batchRaw(batch).toString(2),12);}catch(Exception e){error(e);}});
            if(!CaptureService.armed()&&!batch.isEmpty())button("စမ်းသပ် Evidence ဖိုင် ထုတ်မည်",v->requestExport());
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
            JSONArray rows=store.review(batch);int count=0,hidden=0,overlap=0,unknown=0;
            boolean viber=ViberStructure.PKG.equals(store.batchPackage(batch)),mixed=store.mixedChats(batch);
            String suggested=store.suggestedChat(batch);
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                if(row.optBoolean("include"))count++;else hidden++;
                if("SCROLL_OVERLAP".equals(row.optString("kind"))&&!row.optBoolean("include"))overlap++;
                if("UNRECOGNIZED_NODE".equals(row.optString("kind")))unknown++;
            }
            label("စာဖမ်းယူမှု — "+(store.batchPackage(batch).equals("com.viber.voip")?"Viber":"Messenger"),22);
            label("စာသားအပိုင်း "+count+" ခု • မူရင်း Chat နှင့် စာစောင်၊ အစီအစဉ်၊ ထပ်နေမှု တိုက်စစ်ပါ။",16);
            label("Chat: "+(store.batchChat(batch).isEmpty()?(suggested.isEmpty()?"အတည်မပြုရသေး":suggested):store.batchChat(batch)),18);
            if(mixed)label("Chat အမည် မတူသော ဖမ်းယူမှုများ ပါဝင်နေသည်။ ပယ်ဖျက်ပြီး Chat တစ်ခုချင်း ပြန်ဖမ်းပါ။",16);
            if(viber)label("ဖမ်းယူမှု "+store.batchRaw(batch).length()+" ကြိမ် • ထပ်နေမှု "+overlap+" • စာ "+count+" စောင်",16);
            if(unknown>0)label("မခွဲခြားနိုင်သေးသော Node "+unknown+" ခု — ချန်ထားသောစာထဲ စစ်ပါ။",14);
            if(suggested.isEmpty()||!store.batchChat(batch).isEmpty())button("Chat အမည် အတည်ပြုရန်",v->confirmChat());
            button("မူရင်း App ပြန်ကြည့်ရန်",v->reopenChat());
            button("စမ်းသပ် Evidence ဖိုင် ထုတ်မည်",v->requestExport());
            label(viber?"မူရင်း Chat နှင့် စာစောင်၊ ထပ်နေမှု၊ ချန်ထားသောစာများကို တိုက်စစ်ပါ။":"မူရင်းစာစောင် အလိုအလျောက် အတည်မပြုနိုင်သေးပါ။",14);
            long lastCapture=-1;
            
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);boolean included=row.optBoolean("include");
                if(!included&&!excluded)continue;
                final int idx=i;
                long captureId=row.optLong("capture",0);
                if((!viber||!included)&&captureId!=lastCapture && captureId>0){label("ဖမ်းယူမှု #"+captureId,13);lastCapture=captureId;}
                
                label(row.getString("body"),18);
                if(!row.optString("sent_time","").isEmpty())label(row.optString("sent_time"),13);
                if(row.optBoolean("unlinked_capture"))label("Scroll ဆက်နွယ်မှု မသေချာ — တိုက်စစ်ရန်",13);
                if("SCROLL_OVERLAP".equals(row.optString("kind")))label("ထပ်နေမှုဖြစ်နိုင်သောစာ — ပြန်ထည့်နိုင်သည်",13);
                if(row.optBoolean("alias_hint"))label("တစ်မျက်နှာတည်း parent/child စာသားထပ်နေနိုင်သည်",13);
                if(row.optBoolean("overlap_hint"))label("ယခင်ဖမ်းယူမှုနှင့် အစီအစဉ်တူနေသည် — ထပ်နေနိုင်သည်",13);
                else if(row.optBoolean("repeat"))label("စာသားတူနေသည် — သီးခြားစာစောင်လည်း ဖြစ်နိုင်သည်",13);
                if(!row.optString("time_hint","").isEmpty())label("အချိန်ဖြစ်နိုင်ခြေ: "+row.optString("time_hint")+" (မအတည်ပြုရသေး)",13);
                if(!included)label("ချန်ထားသောစာသား • "+row.optString("kind"),13);
                LinearLayout actions=new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.addView(make("ပြင်ရန်",v->editReview(rows,idx)));
                if(included)actions.addView(make("ခွဲရန်",v->splitReview(rows,idx)));
                actions.addView(make(included?"ဖယ်ရန်":"ထည့်ရန်",v->mutate(rows,()->row.put("include",!included))));
                if(included&&i>0)actions.addView(make("↑",v->mutate(rows,()->{JSONObject prev=rows.getJSONObject(idx-1);rows.put(idx-1,row);rows.put(idx,prev);})));
                if(included&&i+1<rows.length())actions.addView(make("↓",v->mutate(rows,()->{JSONObject next=rows.getJSONObject(idx+1);rows.put(idx+1,row);rows.put(idx,next);})));
                panel.addView(actions);
                if(included){
                    int next=idx+1;
                    while(next<rows.length()&&!rows.getJSONObject(next).optBoolean("include"))next++;
                    if(next<rows.length()){final int target=next;button("နောက်စာနှင့် ပေါင်းရန်",v->mergeReview(rows,idx,target));}
                }
            }
            button((excluded?"ချန်ထားသောစာ ပိတ်ရန်":"ချန်ထားသောစာ ကြည့်ရန်")+" ("+hidden+")",v->{excluded=!excluded;draw();});
            button("ပြင်ဆင်စာစောင် ထပ်ထည့်ရန်",v->editReview(rows,-1));
            final int total=count, skipped=hidden;
            Button confirm=make("စာသားအတည်ပြုပြီး Test Input Box ထဲထည့်မည်",v->new AlertDialog.Builder(this)
                .setTitle("မူရင်း Chat နှင့် တိုက်စစ်ပြီးပြီလား?")
                .setMessage("စာစောင် "+total+" ခု၏ စာသား၊ အစီအစဉ်၊ ထပ်နေမှုနှင့် ချန်ထားသော "+skipped+" ခုကို အားလုံးတိုက်စစ်ပြီး တစ်စောင်ချင်း မှန်ကန်မှ အတည်ပြုပါ။")
                .setNegativeButton("ပြန်စစ်မည်",null).setPositiveButton("အတည်ပြုမည်",(d,w)->{
                    try{if(store.batchChat(batch).isEmpty()&&!suggested.isEmpty())store.saveBatchChat(batch,suggested);
                        store.transferBatch(batch);page=2;active=Math.max(0,store.entries().length()-total);draw();}catch(Exception e){error(e);}
                }).show());
            confirm.setEnabled(count>0&&!mixed&&(!store.batchChat(batch).isEmpty()||!suggested.isEmpty()));panel.addView(confirm);
            button("မပို့ဘဲ ပယ်ဖျက်မည်",v->new AlertDialog.Builder(this).setMessage("ယခု session ကို မပို့ဘဲ ပယ်ဖျက်မလား?").setNegativeButton("မဖျက်ပါ",null).setPositiveButton("ပယ်ဖျက်မည်",(d,w)->{store.discardBatch(batch);page=0;draw();}).show());
            button("Test Input Box ကြည့်ရန်",v->{page=2;draw();});
        }catch(Exception e){error(e);button("ပြန်သွားရန်",v->{page=0;draw();});}
    }
    private void confirmChat(){
        EditText name=new EditText(this);name.setSingleLine(true);name.setText(store.batchChat(batch));
        name.setHint("မူရင်း စမ်းသပ် Chat အမည်");
        new AlertDialog.Builder(this).setTitle("Chat အမည် ကိုယ်တိုင် အတည်ပြုရန်").setMessage("ဖမ်းယူမှုအားလုံး Chat တစ်ခုတည်းမှ ဖြစ်ကြောင်း မူရင်းနှင့် တိုက်စစ်ပါ။ အမည်တူရုံဖြင့် Chat identity အတည်မပြုနိုင်ပါ။")
            .setView(name).setNegativeButton("မပြင်ပါ",null).setPositiveButton("အတည်ပြုမည်",(di,w)->{
                try{store.saveBatchChat(batch,name.getText().toString());draw();}catch(Exception e){error(e);}
            }).show();
    }
    private void reopenChat(){
        try{
            Intent launch=getPackageManager().getLaunchIntentForPackage(store.batchPackage(batch));
            if(launch==null)throw new IllegalStateException("App unavailable");startActivity(launch);
        }catch(Exception e){error(e);}
    }
    private void requestExport(){
        if(CaptureService.armed()||batch.isEmpty())return;
        new AlertDialog.Builder(this).setTitle("စမ်းသပ် Evidence ထုတ်ရန်")
            .setMessage("စမ်းသပ်စာအတုများသာ ပါဝင်ကြောင်း စစ်ပါ။ ဖိုင်ထဲတွင် မြင်ရသောစာသား၊ Chat အမည်နှင့် UI အချက်အလက်များ ပါနိုင်သည်။ မည်သည့်နေရာသို့မှ အလိုအလျောက် မပို့ပါ။")
            .setNegativeButton("မထုတ်ပါ",null).setPositiveButton("ဖိုင်ထုတ်မည်",(di,w)->{
                exportBatch=batch;
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.setType("application/json");intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE,"cherry-test-evidence-"+System.currentTimeMillis()+".json");
                try{startActivityForResult(intent,EXPORT_REQUEST);}catch(Exception e){error(e);}
            }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=EXPORT_REQUEST||result!=RESULT_OK||data==null||data.getData()==null||exportBatch.isEmpty())return;
        try{
            JSONObject payload=new JSONObject();payload.put("schema",1);payload.put("build",15);
            payload.put("package",store.batchPackage(exportBatch));payload.put("manual_chat_label",store.batchChat(exportBatch));
            payload.put("captures",store.batchRaw(exportBatch));
            if(store.batchState(exportBatch)==1)payload.put("review",store.review(exportBatch));
            try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){
                if(out==null)throw new IllegalStateException("File unavailable");
                out.write(payload.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Toast.makeText(this,"Evidence ဖိုင် ထုတ်ပြီး",Toast.LENGTH_SHORT).show();
        }catch(Exception e){error(e);}finally{exportBatch="";}
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
    private void splitReview(JSONArray rows,int index){
        try{
            JSONObject row=rows.getJSONObject(index);
            LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);
            EditText first=new EditText(this),second=new EditText(this);
            first.setMinLines(2);second.setMinLines(2);first.setText(row.getString("body"));
            first.setHint("ပထမစာစောင်");second.setHint("ဒုတိယစာစောင်");fields.addView(first);fields.addView(second);
            new AlertDialog.Builder(this).setTitle("မူရင်း Chat အတိုင်း စာနှစ်စောင် ခွဲရန်").setView(fields).setNegativeButton("မခွဲပါ",null).setPositiveButton("ခွဲမည်",(di,w)->{
                try{
                    String one=first.getText().toString(),two=second.getText().toString();BatchLogic.requireBody(one);BatchLogic.requireBody(two);
                    JSONObject left=new JSONObject(row.toString()),right=new JSONObject(row.toString());
                    left.put("body",one);right.put("body",two);left.put("correction","manual-split-first");right.put("correction","manual-split-second");
                    JSONArray revised=new JSONArray();
                    for(int i=0;i<rows.length();i++){if(i==index){revised.put(left);revised.put(right);}else revised.put(rows.getJSONObject(i));}
                    store.saveReview(batch,revised);draw();
                }catch(Exception e){error(e);}
            }).show();
        }catch(Exception e){error(e);}
    }
    private void mergeReview(JSONArray rows,int leftIndex,int rightIndex){
        try{
            JSONObject left=rows.getJSONObject(leftIndex),right=rows.getJSONObject(rightIndex);
            EditText body=new EditText(this);body.setMinLines(4);body.setText(left.getString("body")+"\n"+right.getString("body"));
            new AlertDialog.Builder(this).setTitle("မူရင်းစာတစ်စောင်ဖြစ်မှ ပေါင်းပါ").setView(body).setNegativeButton("မပေါင်းပါ",null).setPositiveButton("ပေါင်းမည်",(di,w)->{
                try{
                    String value=body.getText().toString();BatchLogic.requireBody(value);
                    JSONObject merged=new JSONObject(left.toString());merged.put("body",value);merged.put("correction","manual-merge");merged.put("merged_source",right.toString());
                    JSONArray revised=new JSONArray();
                    for(int i=0;i<rows.length();i++){if(i==leftIndex)revised.put(merged);else if(i!=rightIndex)revised.put(rows.getJSONObject(i));}
                    store.saveReview(batch,revised);draw();
                }catch(Exception e){error(e);}
            }).show();
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
