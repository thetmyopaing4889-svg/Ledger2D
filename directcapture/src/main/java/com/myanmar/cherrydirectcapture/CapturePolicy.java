package com.myanmar.cherrydirectcapture;

final class CapturePolicy {
    static boolean target(String pkg) {
        return "com.viber.voip".equals(pkg) || "com.facebook.orca".equals(pkg);
    }
    static boolean allow(boolean session, String eventPackage, String rootPackage) {
        return session && target(eventPackage) && eventPackage.equals(rootPackage);
    }
    static boolean allowExplicit(boolean session,boolean locked,boolean ownActivity,boolean selfTest,String activePackage) {
        return session && !locked && !ownActivity && !selfTest && target(activePackage);
    }
    private CapturePolicy() {}
}
