package com.myanmar.cherrydirectcapture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.CheckBox;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CaptureActivity extends Activity {
    private CaptureStore store;
    private LinearLayout panel;
    private int offset;
    private boolean rawView;
    private boolean draftView;
    private ScrollView scroll;
    private TextView selectedLabel;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store=new CaptureStore(this);
        scroll=new ScrollView(this);
        panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24,48,24,32); scroll.addView(panel); setContentView(scroll);
    }
    @Override protected void onResume() { super.onResume(); CaptureService.activityVisible(true); draw(); }
    @Override protected void onPause() { CaptureService.activityVisible(false); super.onPause(); }
    private void label(String s,int size) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size);
        t.setPadding(0,12,0,12); t.setTextIsSelectable(true); panel.addView(t);
    }
    private void button(String s,View.OnClickListener action) {
        Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setOnClickListener(action); panel.addView(b);
    }
    private void draw() {
        panel.removeAllViews();
        label("🍒 Direct Capture Test 11",24);
        if(draftView) {drawDraft();return;}
        label("စမ်းသပ် Chat စာသားကိုသာ အသုံးပြုပါ။ ဖမ်းမည်နှိပ်မှ မြင်ရသော UI စာသားကို ဖုန်းထဲ သိမ်းမည်။ စာရင်းသွင်းခြင်း မရှိပါ။",16);
        label("ဖမ်းယူမှု "+store.count()+" ကြိမ် (စာစောင်အရေအတွက် မဟုတ်) · "+(CaptureService.armed()?"ဖွင့်ထား":"ရပ်ထား"),16);
        label("Service: "+(CaptureService.connected()?"Connected":"Not connected"),16);
        if(CaptureService.armed()) label("ယခုဖမ်းယူမှု "+CaptureService.sessionSaved()+" ကြိမ်",16);
        button(CaptureService.armed()?"ဖမ်းယူမှု ရပ်မည်":"ဖမ်းယူမှု စတင်မည်",v->{
            if(CaptureService.armed()) {CaptureService.endSession(); draw();return;}
            new AlertDialog.Builder(this).setTitle("ဖမ်းယူမှု စတင်မည်")
                .setMessage("Session ဖွင့်ထားချိန် Cherry ခလုတ်သည် အခြား App ပေါ်တွင်လည်း ရှိနိုင်သည်။ Viber / Messenger ရှိ ခွင့်ပြုထားသော စမ်းသပ်စာများကိုသာ ဖမ်းမည်နှိပ်ပါ။ ရပ်မည် သို့မဟုတ် ဖုန်းမျက်နှာပြင်ပိတ်လျှင် Session ပိတ်မည်။")
                .setNegativeButton("မစတင်ပါ",null).setPositiveButton("စတင်မည်",(d,w)->{
                    if(!CaptureService.beginSession()) new AlertDialog.Builder(this)
                        .setMessage("Session မစတင်နိုင်ပါ။ Service နှင့် Overlay Diagnostic ကို စစ်ပါ။")
                        .setPositiveButton("OK",null).show();
                    draw();
                }).show();
        });
        label("ပုံမှန်စမ်းသပ်မှု: စတင်မည် → Viber / Messenger → 🍒 ဖမ်းမည် → ရပ်မည် → Test App",14);
        label("Diagnostic: "+CaptureService.diagnostic(this),14);
        label("Target: "+CaptureService.targetDiagnostic(this),14);
        label("Capture: "+CaptureService.captureDiagnostic(this),14);
        label("Overlay: "+CaptureService.overlayDiagnostic(this),14);
        label("Self-test: "+CaptureService.selfTestDiagnostic(this),14);
        label("Windows: "+CaptureService.windowDiagnostic(this),12);
        button("Overlay ခလုတ် သီးသန့်စမ်းမည်",v->{if(CaptureService.connected()) {CaptureService.testOverlay();draw();}else label("Service not connected",16);});
        label("Overlay သီးသန့်စမ်းမည်သည် အမှားရှာရန်သာ ဖြစ်သည်။ ပုံမှန်ဖမ်းယူမှုအတွက် မနှိပ်ပါနှင့်။",14);
        button("အခြေအနေ ပြန်စစ်မည်",v->draw());
        button("Accessibility Setting",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button("သိမ်းထားသောစာအားလုံး ဖျက်မည်",v->new AlertDialog.Builder(this)
            .setMessage("စမ်းသပ်မှတ်တမ်း၊ ရွေးချယ်မှုနှင့် Test Input Box အားလုံး ဖျက်မည်လား?")
            .setNegativeButton("မဖျက်ပါ",null).setPositiveButton("ဖျက်မည်",(d,w)->{CaptureService.endSession();store.clear();offset=0;draw();}).show());
        label("Capture Inbox",22);
        selectedLabel=new TextView(this);panel.addView(selectedLabel);refreshSelection();
        button("ရွေးထားသောစာများ Test Input Box ထဲ ထည့်မည်",v->confirmTransfer());
        button("Test Input Box ကြည့်ရန်",v->{draftView=true;draw();scroll.scrollTo(0,0);});
        button(rawView?"Inbox ကြည့်ရန်":"မူရင်း Data ကြည့်ရန်",v->{rawView=!rawView;draw();});
        if(!rawView) label("စာသားအပိုင်းများကို စစ်ပြီး ရွေးပါ။ Chat အမည်နှင့် အချိန်ကို ကိုယ်တိုင် အတည်ပြုပါ။ ထပ်နေနိုင်သည် ဆိုသည်မှာ အတည်မပြုရသေးသော အချက်သာဖြစ်သည်။",14);
        try {
            JSONArray page=store.page(offset);
            label("မှတ်တမ်း "+(page.length()==0?0:offset+1)+" – "+(offset+page.length()),16);
            if(offset>0) button("ရှေ့မှတ်တမ်းများ",v->{offset=Math.max(0,offset-5);draw();});
            if(offset+page.length()<store.count()) button("နောက်မှတ်တမ်းများ",v->{offset+=5;draw();});
            for(int i=0;i<page.length();i++) {
                JSONObject snap=page.getJSONObject(i);
                JSONArray nodes=snap.getJSONArray("nodes");
                label("#"+snap.getLong("id")+" • "+snap.getString("package")+" • "+nodes.length()+" UI nodes",18);
                if(rawView) {
                    StringBuilder out=new StringBuilder();
                    for(int j=0;j<nodes.length();j++) {
                        JSONObject n=nodes.getJSONObject(j);
                        out.append(j+1).append(". ").append(n.optString("text"));
                        if(!n.optString("description").isEmpty()) out.append("\n[description] ").append(n.optString("description"));
                        out.append("\n[view_id] ").append(n.optString("view_id"));
                        out.append("\n").append(n.optString("bounds")).append("\n\n");
                    }
                    label(out.toString(),14);
                } else {
                    drawInbox(snap.getLong("id"),snap.getString("package"),nodes);
                }
            }
        } catch(Exception e) { label("မှတ်တမ်း ဖတ်၍မရပါ",16); }
    }
    private void refreshSelection() {
        if(selectedLabel!=null) selectedLabel.setText("ရွေးထားသော စာသားအပိုင်း "+store.selectedCount()+" ခု");
    }
    private EditText editor(String value,String hint,boolean multi) {
        EditText e=new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT|(multi?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));
        e.setSingleLine(!multi); e.setText(value);e.setHint(hint);return e;
    }
    private void drawInbox(long id,String pkg,JSONArray nodes) throws org.json.JSONException {
        String chat=store.chat(id);
        label("Chat: "+(chat.isEmpty()?"အတည်မပြုရသေး":chat),16);
        button("Chat အမည် သတ်မှတ်မည်",v->{
            EditText e=editor(store.chat(id),"Chat အမည်",false);
            new AlertDialog.Builder(this).setTitle("Chat အမည် အတည်ပြုရန်").setView(e)
                .setNegativeButton("မပြင်ပါ",null).setPositiveButton("သိမ်းမည်",(d,w)->{store.saveChat(id,e.getText().toString());draw();}).show();
        });
        java.util.List<CaptureReview.Entry> entries=CaptureReview.entries(nodes);
        java.util.List<String> current=new java.util.ArrayList<>();
        for(CaptureReview.Entry e:entries) if(e.kind==CaptureTextReview.Kind.TEXT) current.add(e.text);
        boolean[] possible=new boolean[current.size()];
        JSONObject prev=store.previous(id,pkg,chat);
        if(prev!=null) {
            java.util.List<String> previous=new java.util.ArrayList<>();
            for(CaptureReview.Entry e:CaptureReview.entries(prev.getJSONArray("nodes")))
                if(e.kind==CaptureTextReview.Kind.TEXT) previous.add(e.text);
            possible=InboxLogic.overlap(previous,current);
        }
        int pos=0;
        for(CaptureReview.Entry e:entries) {
            if(e.kind!=CaptureTextReview.Kind.TEXT) continue;
            drawItem(id,e,possible[pos++]);
        }
        label("UI / အချိန် / description — သီးခြားစစ်ဆေးရန်",18);
        for(CaptureReview.Entry e:entries) if(e.kind!=CaptureTextReview.Kind.TEXT) drawItem(id,e,false);
    }
    private void drawItem(long id,CaptureReview.Entry e,boolean possible) {
        final String original=e.text.trim().isEmpty()?e.description:e.text;
        CaptureStore.Decision state=store.decision(id,e.index,original);
        label("#"+e.index+" · ✓ မှတ်တမ်းရှိ"+(state.transferred?" · Test Input သို့ပို့ပြီး":"")+
            (possible?" · ထပ်နေနိုင်သည်":"")+(e.kind==CaptureTextReview.Kind.TEXT?"":" · "+e.kind.name()),13);
        label(state.edited,16);
        if(!state.stamp.isEmpty()) label("အချိန် (အတည်ပြုထား): "+state.stamp,13);
        if(!e.description.isEmpty() && !e.description.equals(original)) label("description: "+e.description,12);
        if(state.transferred || original.trim().isEmpty()) return;
        CheckBox check=new CheckBox(this); check.setText("ထည့်ရန် ရွေးမည်");check.setChecked(state.selected);
        check.setEnabled(!state.edited.trim().isEmpty());
        check.setOnCheckedChangeListener((b,checked)->{state.selected=checked;store.saveDecision(id,e.index,original,state);refreshSelection();});
        panel.addView(check);
        button("စာသား / အချိန် ပြင်မည်",v->{
            LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);
            EditText body=editor(state.edited,"စာသား",true), time=editor(state.stamp,"စာပို့ချိန် (မသေချာလျှင် ချန်ထားပါ)",false);
            fields.addView(body);fields.addView(time);
            new AlertDialog.Builder(this).setTitle("မူရင်း Data မပြောင်းပါ").setView(fields).setNegativeButton("မပြင်ပါ",null)
                .setPositiveButton("သိမ်းမည်",(d,w)->{
                    state.edited=body.getText().toString();state.stamp=time.getText().toString();
                    if(state.edited.trim().isEmpty()) state.selected=false;
                    store.saveDecision(id,e.index,original,state);draw();
                }).show();
        });
    }
    private void confirmTransfer() {
        int count=store.selectedCount();
        if(count==0) {label("ထည့်ရန် စာသား မရွေးရသေးပါ",16);return;}
        new AlertDialog.Builder(this).setTitle("Test Input Box")
            .setMessage("ရွေးထားသော စာသားအပိုင်း "+count+" ခုကို ဖမ်းယူမှုအစီအစဉ်အတိုင်း ထည့်မည်။ စာရင်းသွင်းခြင်း မရှိပါ။")
            .setNegativeButton("မထည့်ပါ",null).setPositiveButton("ထည့်မည်",(d,w)->{
                try {store.transfer();draftView=true;draw();scroll.scrollTo(0,0);}
                catch(Exception ex) {new AlertDialog.Builder(this).setMessage("မထည့်နိုင်ပါ။ စာသားအလွတ် သို့မဟုတ် အရွယ်အစား စစ်ပါ။").setPositiveButton("OK",null).show();}
            }).show();
    }
    private void drawDraft() {
        label("Test Input Box — စာရင်းသွင်းခြင်း မရှိပါ",18);
        EditText input=editor(store.draft(),"ရွေးထားသော စာသား",true);
        input.setMinLines(8);panel.addView(input);
        button("Draft သိမ်းမည်",v->{store.saveDraft(input.getText().toString());label("သိမ်းပြီး",14);});
        button("Inbox ပြန်သွားမည်",v->{
            if(!input.getText().toString().equals(store.draft())) {
                new AlertDialog.Builder(this).setMessage("ပြင်ထားသော Draft ကို သိမ်းမလား?")
                    .setNeutralButton("ဆက်ပြင်မည်",null)
                    .setNegativeButton("မသိမ်းပါ",(d,w)->{draftView=false;draw();})
                    .setPositiveButton("သိမ်းမည်",(d,w)->{store.saveDraft(input.getText().toString());draftView=false;draw();}).show();
            } else {draftView=false;draw();}
        });
    }
    @Override protected void onDestroy() { store.close(); super.onDestroy(); }
}
