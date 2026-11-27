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
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;
import android.view.View;
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
    private boolean readyOverlay;
    private int sessionBaseline;
    private int routeRetries;
    private static final int[] ROUTE_DELAYS={150,400,900};
    private final Runnable retryRoute=()->retryRouting();
    private static boolean ownActivityVisible;
    private final Runnable testTimeout=()->finishSelfTest("timeout; no self-test click");
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
        // Starting is independent of the diagnostic self-test.
        endSession();
        session=true;
        instance.sessionBaseline=instance.store.count();
        instance.record("session started; open target chat");
        instance.recordTarget("no target event yet");
        instance.recordCapture("no capture attempt yet");
        instance.lastTarget="";
        instance.recordOverlay("session started; awaiting chat");
        // Warm up the same overlay while our own activity is visible, with
        // a non-capture control. Never read chat in this mode.
        if(ownActivityVisible && !instance.locked()) {
            instance.readyOverlay=true;
            instance.show();
        } else instance.route();
        return true;
    }
    static void endSession() {
        session=false;
        if(instance!=null) {
            instance.handler.removeCallbacksAndMessages(null);
            if(instance.selfTest) instance.finishSelfTest("stopped");
            instance.readyOverlay=false;
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
    // Activity lifecycle, not Accessibility root identity, owns the self-test.
    static void activityVisible(boolean visible) {
        ownActivityVisible=visible;
        if(instance==null) return;
        if(!visible && instance.selfTest)
            instance.finishSelfTest("cancelled: left test app");
        if(instance.readyOverlay) {
            if(!visible) {
                instance.readyOverlay=false;
                instance.hide();
                instance.scheduleRouting();
            }
            return;
        }
        if(visible && armed()) {
            instance.foreground="";
            instance.handler.removeCallbacks(instance.retryRoute);
            instance.handler.removeCallbacks(instance.routeLater);
            instance.hide();
        }
    }
    static int sessionSaved() {
        return armed() && instance.store!=null
            ? Math.max(0,instance.store.count()-instance.sessionBaseline) : 0;
    }
    private void scheduleRouting() {
        handler.removeCallbacks(retryRoute);
        routeRetries=0;
        if(armed() && !ownActivityVisible && !locked())
            handler.postDelayed(retryRoute,ROUTE_DELAYS[0]);
    }
    private void retryRouting() {
        if(!armed() || ownActivityVisible || locked() || selfTest) return;
        route();
        if(overlay==null && ++routeRetries<ROUTE_DELAYS.length)
            handler.postDelayed(retryRoute,ROUTE_DELAYS[routeRetries]);
    }
    static void testOverlay() {
        if(instance==null) return;
        endSession();
        if(!ownActivityVisible || instance.locked()) {
            instance.recordSelfTest("refused: test app not visible or device locked");
            return;
        }
        instance.selfTest=true;
        instance.recordSelfTest("creating accessibility overlay");
        instance.show(); // independent of getRootInActiveWindow and event routing
        if(instance.selfTest) instance.handler.postDelayed(instance.testTimeout,15000);
    }
    static String selfTestDiagnostic(Context c) {
        return c.getSharedPreferences("capture_health",MODE_PRIVATE).getString("self_test","not tested");
    }
    private void recordSelfTest(String reason) {
        getSharedPreferences("capture_health",MODE_PRIVATE).edit().putString("self_test",reason).apply();
    }
    private void finishSelfTest(String reason) {
        handler.removeCallbacks(testTimeout);
        selfTest=false;
        hide();
        recordSelfTest(reason);
    }
    static String windowDiagnostic(Context c) {
        return c.getSharedPreferences("capture_health",MODE_PRIVATE).getString("windows","no capture attempt");
    }
    // Explicit capture press only: record coarse package categories and window metadata,
    // not names, message contents, accessibility descriptions or hidden-window content.
    private void recordWindows(String source) {
        StringBuilder out=new StringBuilder(source);
        try {
            List<AccessibilityWindowInfo> windows=getWindows();
            int seen=0;
            for(AccessibilityWindowInfo w:windows) {
                try {
                    if(seen++>=12) continue;
                    out.append("; type=").append(w.getType()).append(" active=").append(w.isActive())
                        .append(" focused=").append(w.isFocused());
                    if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION &&
                       (w.isActive() || w.isFocused())) {
                        AccessibilityNodeInfo root=w.getRoot();
                        if(root!=null) try {
                            out.append(" app=").append(category(root.getPackageName()==null?"":root.getPackageName().toString()));
                        } finally {root.recycle();}
                    }
                } finally {w.recycle();}
            }
        } catch(RuntimeException e) {out.append("; error=").append(e.getClass().getSimpleName());}
        getSharedPreferences("capture_health",MODE_PRIVATE).edit().putString("windows",out.toString()).apply();
    }
    static String overlayDiagnostic(Context c) {
        return c.getSharedPreferences("capture_health",MODE_PRIVATE).getString("overlay","not tested");
    }
    private void recordOverlay(String reason) {
        getSharedPreferences("capture_health",MODE_PRIVATE).edit().putString("overlay",reason).apply();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if(e==null || (!armed() && !selfTest)) return;
        if(selfTest) {
            if(!ownActivityVisible || locked()) finishSelfTest("cancelled: app hidden or locked");
            return; // no root queries or auto-hide from window event during isolated test
        }
        if(locked()) { readyOverlay=false; hide(); return; }
        if(ownActivityVisible) return;
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
            scheduleRouting();
        }
    }
    private void route() {
        if(selfTest || readyOverlay) return;
        if(!armed() || locked() || ownActivityVisible) {hide();return;}
        String pkg=activePackage();
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
        recordWindows("shortcut");
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
        if(overlay!=null) return;
        if(manager==null) {
            if(selfTest) finishSelfTest("failed: WindowManager unavailable");
            else recordOverlay("failed: WindowManager unavailable");
            return;
        }
        final boolean testing=selfTest;
        final boolean ready=readyOverlay;
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        capture=new Button(this); capture.setAllCaps(false);
        capture.setTextColor(Color.WHITE); capture.setBackgroundColor(Color.rgb(170,24,61));
        refresh();
        if(testing) capture.setText("Overlay စမ်းမည်");
        else if(ready) capture.setText("🍒 အဆင်သင့်");
        capture.setOnClickListener(v->{
            if(testing) {
                if(selfTest && ownActivityVisible && !locked()) {
                    finishSelfTest("PASS: self-test click received; no chat read");
                    toast("Overlay ခလုတ် အလုပ်လုပ်သည်");
                }
            } else if(ready) {
                toast("Viber / Messenger Chat ကို ဖွင့်ပါ");
            } else if(!selfTest && !readyOverlay) captureOnce(false,foreground);
        });
        Button stop=new Button(this); stop.setAllCaps(false); stop.setText("ရပ်မည်");
        stop.setOnClickListener(v->endSession());
        layout.addView(capture); layout.addView(stop);
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.END|Gravity.CENTER_VERTICAL;
        if(testing) layout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                if(selfTest) recordSelfTest("attached; press floating Overlay test button");
            }
            @Override public void onViewDetachedFromWindow(View v) {}
        });
        try {
            manager.addView(layout,params); overlay=layout;
            if(testing) {
                recordSelfTest(layout.isAttachedToWindow()?"attached; press floating Overlay test button":"addView returned; awaiting attachment");
            } else recordOverlay(ready?"ready overlay added; no capture":"target overlay added: "+foreground);
        } catch(RuntimeException e) {
            capture=null;
            if(testing) finishSelfTest("addView failed: "+e.getClass().getSimpleName());
            else {
                readyOverlay=false;
                recordOverlay("overlay failed: "+e.getClass().getSimpleName());
            }
        }
    }
    private void refresh() {
        if(capture!=null) capture.setText("🍒 ဖမ်းမည် ("+sessionSaved()+")");
    }
    private void hide() {
        if(overlay!=null && manager!=null) {
            try { manager.removeViewImmediate(overlay); } catch(RuntimeException ignored) {}
        }
        overlay=null; capture=null;
    }
    private void captureOnce(boolean viaShortcut,String expectedPackage) {
        String source=viaShortcut?"shortcut":"overlay";
        if(!armed() || locked() || ownActivityVisible || selfTest || readyOverlay) {
            recordCapture(source+": refused; session stopped, locked or test app visible"); toast("ဖမ်းယူမှု စတင်ပါ"); return;
        }
        if(!viaShortcut && !CapturePolicy.target(foreground)) {
            recordCapture(source+": refused; target not identified"); hide(); return;
        }
        if(!viaShortcut) recordWindows("overlay");
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
            long savedId=store.save(pkg,nodes);
            recordCapture(source+": saved "+pkg+"; id="+savedId+"; nodes="+nodes.length());
            refresh();
            toast("သိမ်းပြီး — ယခုအကြိမ် "+sessionSaved()+" ကြိမ်");
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
        session=false; readyOverlay=false; if(selfTest) finishSelfTest("cancelled: service disconnected");
        handler.removeCallbacksAndMessages(null); hide(); lastTarget=""; foreground="";
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
