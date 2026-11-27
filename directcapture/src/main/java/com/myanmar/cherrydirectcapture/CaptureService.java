package com.myanmar.cherrydirectcapture;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CaptureService extends AccessibilityService {
    // Explicit in-memory opt-in; process death disarms. Window events only use package metadata.
    static volatile boolean session;
    private static CaptureService instance;
    private String foreground="";
    private WindowManager manager;
    private LinearLayout overlay;
    private Button capture;
    private CaptureStore store;

    static void endSession() {
        session=false;
        if(instance!=null) instance.hide();
    }
    @Override protected void onServiceConnected() {
        instance=this;
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        store=new CaptureStore(this);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if(e==null || e.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        foreground=e.getPackageName()==null?"":e.getPackageName().toString();
        if(session && CapturePolicy.target(foreground)) show(); else hide();
    }
    private void show() {
        if(overlay!=null || manager==null) return;
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        capture=new Button(this); capture.setAllCaps(false); refresh();
        capture.setOnClickListener(v->captureOnce());
        Button stop=new Button(this); stop.setAllCaps(false); stop.setText("ရပ်မည်");
        stop.setOnClickListener(v->endSession());
        layout.addView(capture); layout.addView(stop);
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.END|Gravity.CENTER_VERTICAL;
        try { manager.addView(layout,params); overlay=layout; }
        catch(RuntimeException ignored) { capture=null; }
    }
    private void refresh() {
        if(capture!=null) capture.setText("🍒 ဖမ်းမည် ("+(store==null?0:store.count())+")");
    }
    private void hide() {
        if(overlay!=null && manager!=null) {
            try { manager.removeViewImmediate(overlay); } catch(RuntimeException ignored) {}
        }
        overlay=null; capture=null;
    }
    private void captureOnce() {
        if(!session || !CapturePolicy.target(foreground)) { hide(); return; }
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) { toast("ဖမ်းယူ၍မရပါ"); return; }
        try {
            String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
            if(!CapturePolicy.allow(session,foreground,pkg)) { hide(); toast("Chat app ကို ပြန်ဖွင့်ပါ"); return; }
            JSONArray nodes=new JSONArray();
            scan(root,pkg,nodes,new int[]{0,0},0);
            if(nodes.length()==0) { toast("မြင်ရသောစာသား မရရှိပါ"); return; }
            if(!session || !pkg.equals(foreground)) { toast("ဖမ်းယူမှု ရပ်ထားသည်"); return; }
            store.save(pkg,nodes);
            refresh();
            toast("သိမ်းပြီး — ဖမ်းယူမှု "+store.count()+" ကြိမ်");
        } catch(Exception e) { toast("မသိမ်းနိုင်ပါ — ထပ်စမ်းပါ"); }
        finally { root.recycle(); }
    }
    // No content is read on an accessibility event. Only this explicit click traverses the tree.
    // Preserve raw parent/child candidates and duplicates for feasibility review, not message counts.
    private static void scan(AccessibilityNodeInfo node,String pkg,JSONArray out,int[] budget,int depth) throws Exception {
        if(depth>100 || ++budget[0]>5000) throw new IllegalStateException("Tree limit");
        if(!node.isVisibleToUser() || node.isPassword() || node.isEditable()) return;
        if(node.getPackageName()==null || !pkg.contentEquals(node.getPackageName())) return;
        String text=node.getText()==null?"":node.getText().toString();
        String desc=node.getContentDescription()==null?"":node.getContentDescription().toString();
        if(!text.isEmpty() || !desc.isEmpty()) {
            budget[1]+=text.length()+desc.length();
            if(budget[1]>100000) throw new IllegalStateException("Text limit");
            Rect r=new Rect(); node.getBoundsInScreen(r);
            if(r.width()>0 && r.height()>0) {
                JSONObject n=new JSONObject();
                n.put("text",text); n.put("description",desc);
                n.put("view_id",node.getViewIdResourceName());
                n.put("class",String.valueOf(node.getClassName()));
                n.put("depth",depth);
                n.put("bounds",new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom));
                out.put(n);
            }
        }
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null) try { scan(child,pkg,out,budget,depth+1); } finally { child.recycle(); }
        }
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    @Override public void onInterrupt() { endSession(); }
    @Override public void onDestroy() {
        endSession(); instance=null;
        if(store!=null) store.close();
        super.onDestroy();
    }
}
