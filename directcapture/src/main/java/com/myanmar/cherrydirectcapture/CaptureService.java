package com.myanmar.cherrydirectcapture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityButtonController;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
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
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean selfTest;
    private int sessionBaseline;
    private boolean screenReceiverRegistered;
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent intent) {
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                endSession();
                record("screen off; session stopped");
            }
        }
    };
    private static boolean ownActivityVisible;
    private final Runnable testTimeout=()->finishSelfTest("timeout; no self-test click");
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
        if(instance==null || instance.locked() || !instance.screenReceiverRegistered) return false;
        endSession();
        session=true;
        instance.sessionBaseline=instance.store.count();
        instance.record("session started; open authorized test chat");
        instance.recordTarget("checked only on explicit capture");
        instance.recordCapture("no capture attempt yet");
        instance.recordOverlay("creating session overlay");
        instance.show();
        if(instance.overlay==null) {
            session=false;
            instance.record("session could not start: overlay unavailable");
            return false;
        }
        return true;
    }
    static void endSession() {
        session=false;
        if(instance!=null) {
            instance.handler.removeCallbacksAndMessages(null);
            if(instance.selfTest) instance.finishSelfTest("stopped");
            instance.hide();
            instance.record("session stopped");
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
        if(!screenReceiverRegistered) {
            IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if(Build.VERSION.SDK_INT>=33) registerReceiver(screenReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver,filter);
            screenReceiverRegistered=true;
        }
        if(buttonController!=null) buttonController.unregisterAccessibilityButtonCallback(shortcut);
        buttonController=getAccessibilityButtonController();
        buttonController.registerAccessibilityButtonCallback(shortcut);
        record("service connected; start session");
    }
    private boolean locked() {
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        return (km!=null && km.isKeyguardLocked()) || (pm!=null && !pm.isInteractive());
    }
    private String category(String pkg) {
        if(CapturePolicy.target(pkg)) return pkg;
        if(getPackageName().equals(pkg)) return "test app";
        if("com.android.systemui".equals(pkg)) return "system UI";
        return pkg.isEmpty()?"no root":"other app";
    }
    // The session overlay is intentionally independent of Activity focus and roots.
    // No package/tree query on app transitions. Self-test remains own-app only.
    static void activityVisible(boolean visible) {
        ownActivityVisible=visible;
        if(!visible && instance!=null && instance.selfTest)
            instance.finishSelfTest("cancelled: left test app");
    }
    static int sessionSaved() {
        return armed() && instance.store!=null
            ? Math.max(0,instance.store.count()-instance.sessionBaseline) : 0;
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
        // No app detection, tree access, or text collection on events.
        if((armed() || selfTest) && locked()) endSession();
    }
    private void requestShortcut() {
        captureOnce(true);
    }
    private void show() {
        if(overlay!=null) return;
        if(manager==null) {
            if(selfTest) finishSelfTest("failed: WindowManager unavailable");
            else recordOverlay("failed: WindowManager unavailable");
            return;
        }
        final boolean testing=selfTest;
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        capture=new Button(this); capture.setAllCaps(false);
        capture.setTextColor(Color.WHITE); capture.setBackgroundColor(Color.rgb(170,24,61));
        refresh();
        if(testing) capture.setText("Overlay စမ်းမည်");
        capture.setOnClickListener(v->{
            if(testing) {
                if(selfTest && ownActivityVisible && !locked()) {
                    finishSelfTest("PASS: self-test click received; no chat read");
                    toast("Overlay ခလုတ် အလုပ်လုပ်သည်");
                }
            } else if(!selfTest) captureOnce(false);
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
            } else recordOverlay("session overlay added; retained until stop");
        } catch(RuntimeException e) {
            capture=null;
            if(testing) finishSelfTest("addView failed: "+e.getClass().getSimpleName());
            else recordOverlay("overlay failed: "+e.getClass().getSimpleName());
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
    private void captureOnce(boolean viaShortcut) {
        String source=viaShortcut?"shortcut":"overlay";
        if(!armed() || locked() || ownActivityVisible || selfTest) {
            recordCapture(source+": refused; session stopped, locked or test app visible");
            toast("Viber / Messenger Chat ကို ဖွင့်ပါ"); return;
        }
        // An explicit tap is the only place where active chat content is accessed.
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) {
            recordCapture(source+": no active root");
            recordWindows(source);
            toast("Chat ကို ထိပြီး ထပ်ဖမ်းပါ"); return;
        }
        try {
            String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
            recordTarget("explicit tap: "+category(pkg));
            if(!CapturePolicy.allowExplicit(armed(),locked(),ownActivityVisible,selfTest,pkg)) {
                recordCapture(source+": active root not target: "+category(pkg));
                toast("Viber / Messenger Chat ကို ဖွင့်ပါ"); return;
            }
            JSONArray nodes=new JSONArray();
            scan(root,pkg,nodes,new int[]{0,0},0);
            if(nodes.length()==0) { recordCapture(source+": no visible text nodes"); toast("မြင်ရသောစာသား မရရှိပါ"); return; }
            if(!CapturePolicy.allowExplicit(armed(),locked(),ownActivityVisible,selfTest,pkg)) {
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
        session=false; if(selfTest) finishSelfTest("cancelled: service disconnected");
        handler.removeCallbacksAndMessages(null); hide();
        if(screenReceiverRegistered) {
            unregisterReceiver(screenReceiver);
            screenReceiverRegistered=false;
        }
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
