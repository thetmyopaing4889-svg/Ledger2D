package com.myanmar.cherrydirectcapture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityButtonController;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CaptureService extends AccessibilityService {
    // Session is explicit and in-memory; process death or unbind disarms.
    private static boolean session;
    private static CaptureService instance;
    private String foreground="";
    private final HashMap<Integer,String> targetWindows=new HashMap<>();
    private WindowManager manager;
    private LinearLayout overlay;
    private Button capture;
    private CaptureStore store;
    private AccessibilityButtonController buttonController;
    private final AccessibilityButtonController.AccessibilityButtonCallback shortcut =
        new AccessibilityButtonController.AccessibilityButtonCallback() {
            @Override public void onClicked(AccessibilityButtonController controller) {
                captureOnce(true);
            }
            @Override public void onAvailabilityChanged(AccessibilityButtonController controller, boolean available) {
                record("shortcut availability: "+available);
            }
        };
    static String shortcutStatus() {
        if(instance==null || instance.buttonController==null) return "not connected";
        return instance.buttonController.isAccessibilityButtonAvailable()?"available":"unavailable";
    }
    static String targetDiagnostic(Context c) {
        return c.getSharedPreferences("capture_health",MODE_PRIVATE).getString("target","no target event yet");
    }
    static String captureDiagnostic(Context c) {
        return c.getSharedPreferences("capture_health",MODE_PRIVATE).getString("capture","no capture attempt yet");
    }
    private void recordTarget(String reason) {
        getSharedPreferences("capture_health",MODE_PRIVATE).edit().putString("target",reason).apply();
    }
    private void recordCapture(String reason) {
        getSharedPreferences("capture_health",MODE_PRIVATE).edit().putString("capture",reason).apply();
    }

    static boolean connected() { return instance!=null; }
    static boolean armed() { return connected() && session; }
    static boolean beginSession() {
        if(instance==null) return false;
        session=true;
        instance.record("session started; open target chat");
        instance.recordTarget("no target event yet");
        instance.recordCapture("no capture attempt yet");
        return true;
    }
    static void endSession() {
        session=false;
        if(instance!=null) {
            instance.hide();
            instance.record("session stopped by user");
        }
    }
    static String diagnostic(Context c) {
        return c.getSharedPreferences("capture_health",MODE_PRIVATE)
                .getString("state","no service connection recorded");
    }
    private void record(String reason) {
        // Never include text, node contents, chat names or non-target package names.
        getSharedPreferences("capture_health",MODE_PRIVATE).edit()
            .putString("state",reason).apply();
    }
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        session=false;
        instance=this;
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(store==null) store=new CaptureStore(this);
        targetWindows.clear();
        if(buttonController!=null) buttonController.unregisterAccessibilityButtonCallback(shortcut);
        buttonController=getAccessibilityButtonController();
        buttonController.registerAccessibilityButtonCallback(shortcut);
        record("service connected; start session");
    }
    private boolean locked() {
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        return km!=null && km.isKeyguardLocked();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if(e==null || !armed()) return;
        if(locked()) { hide(); return; }
        int kind=e.getEventType();
        if(kind!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
           kind!=AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
           kind!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
        String pkg=e.getPackageName()==null?"":e.getPackageName().toString();
        boolean target=CapturePolicy.target(pkg);
        // Non-target content events never contribute metadata or trigger work.
        if(kind==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !target) return;
        if(target && e.getWindowId()>=0) targetWindows.put(e.getWindowId(),pkg);
        if(kind==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !target && e.getWindowId()>=0)
            targetWindows.remove(e.getWindowId());
        // Use only window IDs/types/active/focused flags, not node text or root.
        List<AccessibilityWindowInfo> windows=getWindows();
        String activeTarget="";
        boolean activeOther=false;
        boolean activeKnown=false;
        boolean hasApplication=false;
        HashSet<Integer> live=new HashSet<>();
        for(AccessibilityWindowInfo w:windows) {
            try {
                live.add(w.getId());
                if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION) {
                    hasApplication=true;
                    if(w.isActive() || w.isFocused()) {
                        activeKnown=true;
                        String known=targetWindows.get(w.getId());
                        if(known!=null) activeTarget=known; else activeOther=true;
                    }
                } else if(w.getType()==AccessibilityWindowInfo.TYPE_SYSTEM && w.isActive()) {
                    activeOther=true;
                }
            } finally { w.recycle(); }
        }
        if(!windows.isEmpty()) targetWindows.keySet().retainAll(live);
        if(target) recordTarget(pkg+" event="+kind+"; active target="+(!activeTarget.isEmpty())+"; other active="+activeOther+"; windows="+windows.size());
        if(activeOther) {
            foreground=""; hide();
            if(kind!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) record("waiting: other window active");
        } else if(CapturePolicy.target(activeTarget)) {
            foreground=activeTarget; show();
        } else if(target && !activeKnown && !hasApplication &&
                  kind==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Compatibility fallback; actual root package still checked on explicit click.
            foreground=pkg; show();
        } else {
            foreground=""; hide();
            if(kind!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) record("waiting: target window not identified");
        }
    }
    private void show() {
        if(overlay!=null || manager==null) return;
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        capture=new Button(this); capture.setAllCaps(false);
        capture.setTextColor(Color.WHITE); capture.setBackgroundColor(Color.rgb(170,24,61));
        refresh();
        capture.setOnClickListener(v->captureOnce(false));
        Button stop=new Button(this); stop.setAllCaps(false); stop.setText("ရပ်မည်");
        stop.setOnClickListener(v->endSession());
        layout.addView(capture); layout.addView(stop);
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.END|Gravity.CENTER_VERTICAL;
        try { manager.addView(layout,params); overlay=layout; record("capture overlay shown: "+foreground); recordTarget("capture overlay shown: "+foreground); }
        catch(RuntimeException e) { capture=null; record("overlay failed: "+e.getClass().getSimpleName()); recordTarget("overlay failed: "+e.getClass().getSimpleName()); }
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
    private void captureOnce(boolean viaShortcut) {
        String source=viaShortcut?"shortcut":"overlay";
        if(!armed() || locked()) {
            recordCapture(source+": refused; session stopped or locked"); toast("ဖမ်းယူမှု စတင်ပါ"); return;
        }
        if(!viaShortcut && !CapturePolicy.target(foreground)) {
            recordCapture(source+": refused; target not identified"); hide(); return;
        }
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) { recordCapture(source+": no active root"); toast("ဖမ်းယူ၍မရပါ"); return; }
        try {
            String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
            // Explicit shortcut does not depend on overlay's event routing.
            // Read no node text unless the actual active root belongs to the target.
            if(!CapturePolicy.target(pkg) || (!viaShortcut && !CapturePolicy.allow(session,foreground,pkg))) {
                recordCapture(source+": active root not target");
                if(!viaShortcut) hide();
                toast("Viber / Messenger Chat ကို ဖွင့်ပါ"); return;
            }
            JSONArray nodes=new JSONArray();
            scan(root,pkg,nodes,new int[]{0,0},0);
            if(nodes.length()==0) { recordCapture(source+": no visible text nodes"); toast("မြင်ရသောစာသား မရရှိပါ"); return; }
            if(!armed() || locked() || (!viaShortcut && !pkg.equals(foreground))) {
                recordCapture(source+": cancelled"); return;
            }
            AccessibilityNodeInfo check=getRootInActiveWindow();
            if(check==null) {recordCapture(source+": root lost before save");return;}
            try {
                if(check.getPackageName()==null || !pkg.contentEquals(check.getPackageName()) ||
                    check.getWindowId()!=root.getWindowId()) {
                    recordCapture(source+": active window changed before save"); return;
                }
            } finally {check.recycle();}
            store.save(pkg,nodes);
            refresh();
            recordCapture(source+": saved "+pkg+"; nodes="+nodes.length());
            toast("သိမ်းပြီး — ဖမ်းယူမှု "+store.count()+" ကြိမ်");
        } catch(Exception e) { recordCapture(source+": failed "+e.getClass().getSimpleName()); toast("မသိမ်းနိုင်ပါ — ထပ်စမ်းပါ"); }
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
    @Override public void onInterrupt() {
        // Feedback interruption is not an unbind. No feedback stream to stop.
        record("feedback interrupted; session "+(session?"armed":"stopped"));
    }
    private void disconnect() {
        session=false; hide(); targetWindows.clear(); foreground="";
        if(buttonController!=null) {buttonController.unregisterAccessibilityButtonCallback(shortcut);buttonController=null;}
        if(instance==this) instance=null;
        record("service disconnected; session stopped");
    }
    @Override public boolean onUnbind(Intent intent) {
        disconnect(); return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        disconnect();
        if(store!=null) {store.close(); store=null;}
        super.onDestroy();
    }
}
