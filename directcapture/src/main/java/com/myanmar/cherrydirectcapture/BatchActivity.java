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
    private LinearLayout panel,root,footer;
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
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
        scroll=new ScrollView(this);scroll.setFillViewport(true);panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12),dp(12),dp(12),dp(12));scroll.addView(panel);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        footer=new LinearLayout(this);footer.setOrientation(LinearLayout.VERTICAL);footer.setPadding(dp(12),dp(6),dp(12),dp(10));root.addView(footer);
        footer.setVisibility(View.GONE);setContentView(root);
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
        panel.removeAllViews();footer.removeAllViews();footer.setVisibility(View.GONE);panel.setBackgroundColor(Color.WHITE);
        if(page!=1)label("🍒 Direct Capture Test 16",24);
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
        java.util.List<CaptureApps.App> installed=new java.util.ArrayList<>();
        for(CaptureApps.App app:CaptureApps.ALL)if(getPackageManager().getLaunchIntentForPackage(app.pkg)!=null)installed.add(app);
        if(installed.isEmpty()){new AlertDialog.Builder(this).setMessage("စမ်းသပ်နိုင်သော Messaging App မတွေ့ပါ။").setPositiveButton("OK",null).show();return;}
        java.util.List<CaptureApps.App> primary=new java.util.ArrayList<>(),more=new java.util.ArrayList<>();
        for(CaptureApps.App app:installed){if(app.pkg.equals("com.viber.voip")||app.pkg.equals("com.facebook.orca"))primary.add(app);else more.add(app);}
        java.util.List<String> names=new java.util.ArrayList<>();
        for(CaptureApps.App app:primary)names.add(app.name+(app.observed?"":" (စမ်းသပ်)"));
        if(!more.isEmpty())names.add("More");
        new AlertDialog.Builder(this).setTitle("Import").setItems(names.toArray(new String[0]),(di,i)->{
            if(i<primary.size())startCapture(primary.get(i).pkg);
            else{String[] labels=new String[more.size()];for(int k=0;k<more.size();k++)labels[k]=more.get(k).name+" (စမ်းသပ်)";
                new AlertDialog.Builder(this).setTitle("More").setItems(labels,(dd,k)->startCapture(more.get(k).pkg)).setNegativeButton("မလုပ်တော့ပါ",null).show();}
        }).setNegativeButton("မလုပ်တော့ပါ",null).show();
    }
    private void startCapture(String pkg){
        if(!CaptureService.connected()){startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}
        new AlertDialog.Builder(this).setTitle("စာဖမ်းယူရန်").setMessage("ရည်ရွယ်ထားသော စမ်းသပ် Chat တစ်ခုကိုသာ ဖွင့်ပါ။ 🍒 နှိပ်မှ စာဖမ်းမည်။ Chat မပြောင်းပါနှင့်။")
          .setNegativeButton("မစတင်ပါ",null).setPositiveButton("စတင်မည်",(d,w)->{
            try{
                Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
                if(launch==null)throw new IllegalStateException("App unavailable");
                if(!CaptureService.beginSession(pkg))throw new IllegalStateException("Overlay unavailable");
                batch=store.latestBatch();page=0;draw();
                try{startActivity(launch);}catch(RuntimeException ex){CaptureService.endSession();throw ex;}
            }catch(Exception e){error(e);}
          }).show();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private android.graphics.drawable.GradientDrawable rounded(int color,int radius){
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;
    }
    private TextView text(String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;
    }
    private void review(){
        try{
            JSONArray rows=store.review(batch);int count=0,hidden=0,uncertain=0;
            String pkg=store.batchPackage(batch);
            CaptureApps.App style=CaptureApps.find(pkg);boolean viber=CaptureApps.observed(pkg),mixed=store.mixedChats(batch);
            String suggested=store.suggestedChat(batch),confirmed=store.batchChat(batch);
            for(int i=0;i<rows.length();i++){
                JSONObject r=rows.getJSONObject(i);
                if(r.optBoolean("include"))count++;else hidden++;
                if(r.optBoolean("unlinked_capture")||"UNRECOGNIZED_NODE".equals(r.optString("kind")))uncertain++;
            }
            panel.setBackgroundColor(style==null?0xfff2f3f7:style.background);
            LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setPadding(dp(14),dp(12),dp(14),dp(12));
            header.setBackground(rounded(CHERRY,16));
            header.addView(text("စာသားတိုက်စစ်ရန်",21,Color.WHITE));
            header.addView(text(CaptureApps.name(pkg)+"  •  "+count+" စောင်",14,Color.WHITE));
            panel.addView(header);
            LinearLayout source=new LinearLayout(this);source.setGravity(Gravity.CENTER_VERTICAL);source.setPadding(0,dp(12),0,dp(10));
            TextView name=text(confirmed.isEmpty()?(suggested.isEmpty()?"Chat အမည် အတည်ပြုရန်":suggested):confirmed,19,0xff17171b);
            source.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
            Button menu=make("⋮",v->reviewMenu());menu.setContentDescription("Review options");source.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(44)));panel.addView(source);
            if(mixed)label("Chat အမည်မတူပါ။ Chat တစ်ခုချင်း ပြန်ဖမ်းပါ။",16);
            if(!viber){
                label("စမ်းသပ်အဆင့် — စာစောင်အမှန်ကို မူရင်းနှင့် တိုက်စစ်ပြီး ထည့်ပါ။",15);
                if(confirmed.isEmpty())button("Chat အမည် အတည်ပြုရန်",v->confirmChat());
            }
            if(uncertain>0)label("မသေချာသောစာများကို တိုက်စစ်ပါ။",14);
            for(int i=0;i<rows.length();i++){
                JSONObject r=rows.getJSONObject(i);boolean included=r.optBoolean("include");
                if(!included&&!excluded)continue;
                final int index=i;
                LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.TOP);
                LinearLayout bubble=new LinearLayout(this);bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setBackground(rounded(style==null?Color.WHITE:style.bubble,style==null?14:style.radius));
                bubble.setPadding(dp(12),dp(10),dp(12),dp(8));
                TextView body=text(r.getString("body"),17,0xff17171b);body.setTextIsSelectable(true);bubble.addView(body);
                String time=r.optString("sent_time","");
                if(!time.isEmpty()){TextView stamp=text(time,12,0xff666666);stamp.setGravity(Gravity.END);bubble.addView(stamp);}
                if(!included||r.optBoolean("unlinked_capture")||r.optBoolean("repeat")||r.optBoolean("overlap_hint")||r.optBoolean("alias_hint")){
                    String note=!included?"ချန်ထားသောစာ":r.optBoolean("unlinked_capture")?"အစီအစဉ် တိုက်စစ်ရန်":"ထပ်နေမှု တိုက်စစ်ရန်";
                    bubble.addView(text(note,12,0xff94601a));
                }
                LinearLayout.LayoutParams bubbleParams=new LinearLayout.LayoutParams(0,-2,1f);
                bubbleParams.setMargins(0,0,dp(6),dp(8));line.addView(bubble,bubbleParams);
                Button options=make("⋮",v->messageMenu(v,rows,index));
                options.setContentDescription("စာစောင် ပြင်ဆင်ရန်");
                options.setMinWidth(0);options.setMinimumWidth(0);options.setPadding(0,0,0,0);
                line.addView(options,new LinearLayout.LayoutParams(dp(44),dp(44)));panel.addView(line);
            }
            if(hidden>0)button(excluded?"ချန်ထားသောစာ ပိတ်ရန်":"ချန်ထားသောစာများ ပြန်စစ်ရန်",v->{excluded=!excluded;draw();});
            if(!viber||uncertain>0||excluded)button("စာစောင် ထပ်ထည့်ရန်",v->editReview(rows,-1));
            if(!viber||confirmed.isEmpty()&&suggested.isEmpty())button("Chat အမည် ပြင်ဆင်ရန်",v->confirmChat());
            footer.setVisibility(View.VISIBLE);
            footer.addView(make("မူရင်း Chat ပြန်ကြည့်ရန်",v->reopenChat()));
            final int total=count,skipped=hidden;
            Button confirm=make("အတည်ပြုမည်",v->new AlertDialog.Builder(this)
                .setTitle("မူရင်း Chat နှင့် တိုက်စစ်ပြီးပြီလား?")
                .setMessage("စာ "+total+" စောင်နှင့် ချန်ထားသောစာ "+skipped+" ခုကို တိုက်စစ်ပါ။ Chat တစ်ခုတည်းမှ စာစောင်များ ဖြစ်ကြောင်း အတည်ပြုပါ။")
                .setNegativeButton("ပြန်စစ်မည်",null).setPositiveButton("အတည်ပြုမည်",(di,w)->{
                    try{if(store.batchChat(batch).isEmpty()&&!suggested.isEmpty())store.saveBatchChat(batch,suggested);
                        store.transferBatch(batch);page=2;active=Math.max(0,store.entries().length()-total);draw();}catch(Exception e){error(e);}
                }).show());
            android.graphics.drawable.GradientDrawable glossy=new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xffec6185,0xffb51b46,0xff801132});
            glossy.setCornerRadius(dp(16));confirm.setBackground(glossy);confirm.setTextColor(Color.WHITE);confirm.setElevation(dp(3));
            confirm.setEnabled(count>0&&!mixed&&(!confirmed.isEmpty()||!suggested.isEmpty()));
            confirm.setAlpha(confirm.isEnabled()?1f:.45f);
            footer.addView(confirm,new LinearLayout.LayoutParams(-1,dp(54)));
        }catch(Exception e){error(e);button("ပြန်သွားရန်",v->{page=0;draw();});}
    }
    private void reviewMenu(){
        new AlertDialog.Builder(this).setTitle("စာသားတိုက်စစ်ရန်").setItems(new String[]{"Chat အမည် ပြင်ဆင်ရန်","စာစောင် ထပ်ထည့်ရန်","Evidence ဖိုင် ထုတ်မည်","Test Input Box ကြည့်ရန်","မပို့ဘဲ ပယ်ဖျက်မည်"},(di,i)->{
            try{
                if(i==0)confirmChat();else if(i==1)editReview(store.review(batch),-1);else if(i==2)requestExport();
                else if(i==3){page=2;draw();}
                else new AlertDialog.Builder(this).setMessage("ယခု session ကို မပို့ဘဲ ပယ်ဖျက်မလား?").setNegativeButton("မဖျက်ပါ",null)
                    .setPositiveButton("ပယ်ဖျက်မည်",(dd,w)->{store.discardBatch(batch);page=0;draw();}).show();
            }catch(Exception e){error(e);}
        }).show();
    }
    private void messageMenu(View anchor,JSONArray rows,int index){
        try{
            JSONObject r=rows.getJSONObject(index);boolean included=r.optBoolean("include");
            PopupMenu menu=new PopupMenu(this,anchor);
            menu.getMenu().add(0,1,0,"ပြင်ဆင်ရန်");menu.getMenu().add(0,2,1,included?"ဖယ်ရှားရန်":"ပြန်ထည့်ရန်");
            if(included){
                menu.getMenu().add(0,3,2,"စာစောင် ခွဲရန်");
                int next=nextIncluded(rows,index,1),prev=nextIncluded(rows,index,-1);
                if(next>=0){menu.getMenu().add(0,4,3,"နောက်စာနှင့် ပေါင်းရန်");menu.getMenu().add(0,6,5,"အောက်ရွှေ့ရန်");}
                if(prev>=0)menu.getMenu().add(0,5,4,"အပေါ်ရွှေ့ရန်");
            }
            menu.setOnMenuItemClickListener(item->{
                switch(item.getItemId()){
                    case 1:editReview(rows,index);break;
                    case 2:
                        if(included)new AlertDialog.Builder(this).setMessage("ဒီစာကို ဖယ်ရှားမလား?").setNegativeButton("မဖယ်ပါ",null)
                            .setPositiveButton("ဖယ်မည်",(di,w)->mutate(rows,()->r.put("include",false))).show();
                        else mutate(rows,()->r.put("include",true));break;
                    case 3:splitReview(rows,index);break;
                    case 4:mergeReview(rows,index,nextIncluded(rows,index,1));break;
                    case 5:moveReview(rows,index,-1);break;
                    case 6:moveReview(rows,index,1);break;
                    default:return false;
                }return true;
            });menu.show();
        }catch(Exception e){error(e);}
    }
    private int nextIncluded(JSONArray rows,int index,int direction){
        for(int n=index+direction;n>=0&&n<rows.length();n+=direction)if(rows.optJSONObject(n).optBoolean("include"))return n;
        return -1;
    }
    private void moveReview(JSONArray rows,int index,int direction){
        int to=nextIncluded(rows,index,direction);if(to<0)return;
        mutate(rows,()->{JSONObject r=rows.getJSONObject(index),other=rows.getJSONObject(to);rows.put(index,other);rows.put(to,r);});
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
            JSONObject payload=new JSONObject();payload.put("schema",1);payload.put("build",16);
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
                if(i>=0){rows.getJSONObject(i).put("body",body);rows.getJSONObject(i).put("correction","manual-edit");}
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
