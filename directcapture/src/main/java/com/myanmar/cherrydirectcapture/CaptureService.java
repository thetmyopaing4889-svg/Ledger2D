package com.myanmar.cherrydirectcapture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityButtonController;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
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
    // Session is explicit and in-memory; process death or unbind disarms.
    private static boolean session;
    private static CaptureService instance;
    private String foreground="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean selfTest;
    private String lastTarget="";
    private final Runnable routeLater=()->route();
    private WindowManager manager;
    private LinearLayout overlay;
    private Button capture;
    private CaptureStore store;
    private AccessibilityButtonController buttonController;
    private final AccessibilityButtonController.AccessibilityButtonCallback shortcut =
        new AccessibilityButtonController.AccessibilityButtonCallback() {
            @Override public void onClicked(AccessibilityButtonController controller) {
                requestShortcut();
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
        instance.lastTarget="";
        instance.route();
        return true;
    }
    static void endSession() {
        session=false;
        if(instance!=null) {
            instance.handler.removeCallbacksAndMessages(null);
            instance.selfTest=false;
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
        lastTarget="";
        if(buttonController!=null) buttonController.unregisterAccessibilityButtonCallback(shortcut);
        buttonController=getAccessibilityButtonController();
        buttonController.registerAccessibilityButtonCallback(shortcut);
        record("service connected; start session");
    }
    private boolean locked() {
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        return km!=null && km.isKeyguardLocked();
    }
    private String category(String pkg) {
        if(CapturePolicy.target(pkg)) return pkg;
        if(getPackageName().equals(pkg)) return "test app";
        if("com.android.systemui".equals(pkg)) return "system UI";
        return pkg.isEmpty()?"no root":"other app";
    }
    // Inspect only active root package metadata for routing. No node text is read
    // until a user presses an explicit capture control.
    private String activePackage() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) return "";
        try { return root.getPackageName()==null?"":root.getPackageName().toString(); }
        finally {root.recycle();}
    }
    static void testOverlay() {
        if(instance==null) return;
        instance.selfTest=true;
        instance.hide();
        instance.route();
    }
    static String overlayDiagnostic(Context c) {
        return c.getSharedPreferences("capture_health",MODE_PRIVATE).getString("overlay","not tested");
    }
    private void recordOverlay(String reason) {
        getSharedPreferences("capture_health",MODE_PRIVATE).edit().putString("overlay",reason).apply();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if(e==null || (!armed() && !selfTest)) return;
        if(locked()) { selfTest=false; hide(); return; }
        int kind=e.getEventType();
        if(kind!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
           kind!=AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
           kind!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
        String pkg=e.getPackageName()==null?"":e.getPackageName().toString();
        if(kind==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !CapturePolicy.target(pkg)) return;
        if(CapturePolicy.target(pkg)) {
            lastTarget=pkg;
            recordTarget(pkg+" event="+kind+"; active="+category(activePackage()));
        }
        route();
        // A window transition can report the previous root; one bounded recheck.
        if(kind!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handler.removeCallbacks(routeLater);
            handler.postDelayed(routeLater,300);
        }
    }
    private void route() {
        if((!armed() && !selfTest) || locked()) {hide();return;}
        String pkg=activePackage();
        if(selfTest) {
            if(getPackageName().equals(pkg)) { foreground=""; show(); return; }
            selfTest=false; hide();
        }
        if(armed() && CapturePolicy.target(pkg)) {
            lastTarget=pkg; foreground=pkg; show();
        } else {
            foreground=""; hide();
        }
        record("active root: "+category(pkg));
    }
    private void requestShortcut() {
        if(!armed() || locked()) {
            recordCapture("shortcut: refused; session stopped or locked"); return;
        }
        String pkg=activePackage();
        recordCapture("shortcut initial root: "+category(pkg));
        if(CapturePolicy.target(pkg)) { captureOnce(true,pkg); return; }
        // Do not select hidden windows or read another app. If the system shortcut
        // temporarily owns focus, permit one delayed check of the same recent target.
        if(!"com.android.systemui".equals(pkg) || !CapturePolicy.target(lastTarget)) {
            toast("Viber / Messenger Chat ကို ဖွင့်ပါ"); return;
        }
        final String expected=lastTarget;
        handler.postDelayed(()->{
            if(!armed() || locked()) return;
            String now=activePackage();
            if(!expected.equals(now)) {
                recordCapture("shortcut: root stayed "+category(now));return;
            }
            captureOnce(true,expected);
        },350);
    }
    private void show() {
        if(overlay!=null || manager==null) return;
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        capture=new Button(this); capture.setAllCaps(false);
        capture.setTextColor(Color.WHITE); capture.setBackgroundColor(Color.rgb(170,24,61));
        refresh();
        if(selfTest) capture.setText("Overlay စမ်းမည်");
        capture.setOnClickListener(v->{
            if(selfTest) {
                recordOverlay("self-test click received; no chat read");
                toast("Overlay ခလုတ် အလုပ်လုပ်သည်");
                selfTest=false; hide();
            } else captureOnce(false,foreground);
        });
        Button stop=new Button(this); stop.setAllCaps(false); stop.setText("ရပ်မည်");
        stop.setOnClickListener(v->endSession());
        layout.addView(capture); layout.addView(stop);
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.END|Gravity.CENTER_VERTICAL;
        try { manager.addView(layout,params); overlay=layout; recordOverlay(selfTest?"self-test overlay added":"target overlay added: "+foreground); }
        catch(RuntimeException e) { capture=null; recordOverlay("overlay failed: "+e.getClass().getSimpleName()); }
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
    private void captureOnce(boolean viaShortcut,String expectedPackage) {
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
            if(!CapturePolicy.target(pkg) || !pkg.equals(expectedPackage) || (!viaShortcut && !CapturePolicy.allow(session,foreground,pkg))) {
                recordCapture(source+": active root mismatch: "+category(pkg));
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
        session=false; selfTest=false; handler.removeCallbacksAndMessages(null); hide(); lastTarget=""; foreground="";
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
