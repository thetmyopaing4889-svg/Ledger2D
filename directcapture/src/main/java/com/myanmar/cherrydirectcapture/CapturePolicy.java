package com.myanmar.cherrydirectcapture;

final class CapturePolicy {
    static boolean target(String pkg) {
        return "com.viber.voip".equals(pkg) || "com.facebook.orca".equals(pkg);
    }
    static boolean allow(boolean session, String eventPackage, String rootPackage) {
        return session && target(eventPackage) && eventPackage.equals(rootPackage);
    }
    // Overlay visibility only; does not authorize capturing a focused root.
    // If Android exposes a focused app, prefer it over last-touched active root.
    // A blocked/ambiguous/non-target focus must hide the overlay.
    static String overlayTarget(String active, String focused) {
        if(focused!=null) {
            if(!target(focused)) return "";
            if(target(active) && !focused.equals(active)) return "";
            return focused;
        }
        return target(active)?active:"";
    }
    private CapturePolicy() {}
}
