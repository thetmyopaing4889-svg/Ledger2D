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
import org.json.JSONArray;
import org.json.JSONObject;

public final class CaptureActivity extends Activity {
    private CaptureStore store;
    private LinearLayout panel;
    private int offset;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store=new CaptureStore(this);
        ScrollView scroll=new ScrollView(this);
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
        label("🍒 Direct Capture Test 9",24);
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
            .setMessage("စမ်းသပ်မှတ်တမ်းများ အားလုံး ဖျက်မည်လား?")
            .setNegativeButton("မဖျက်ပါ",null).setPositiveButton("ဖျက်မည်",(d,w)->{CaptureService.endSession();store.clear();offset=0;draw();}).show());
        try {
            JSONArray page=store.page(offset);
            label("မှတ်တမ်း "+(page.length()==0?0:offset+1)+" – "+(offset+page.length()),16);
            if(offset>0) button("ရှေ့မှတ်တမ်းများ",v->{offset=Math.max(0,offset-5);draw();});
            if(offset+page.length()<store.count()) button("နောက်မှတ်တမ်းများ",v->{offset+=5;draw();});
            for(int i=0;i<page.length();i++) {
                JSONObject snap=page.getJSONObject(i);
                JSONArray nodes=snap.getJSONArray("nodes");
                label("#"+snap.getLong("id")+" • "+snap.getString("package")+" • "+nodes.length()+" UI nodes",18);
                StringBuilder out=new StringBuilder();
                for(int j=0;j<nodes.length();j++) {
                    JSONObject n=nodes.getJSONObject(j);
                    out.append(j+1).append(". ").append(n.optString("text"));
                    if(!n.optString("description").isEmpty()) out.append("\n[description] ").append(n.optString("description"));
                    out.append("\n").append(n.optString("bounds")).append("\n\n");
                }
                label(out.toString(),14);
            }
        } catch(Exception e) { label("မှတ်တမ်း ဖတ်၍မရပါ",16); }
    }
    @Override protected void onDestroy() { store.close(); super.onDestroy(); }
}
