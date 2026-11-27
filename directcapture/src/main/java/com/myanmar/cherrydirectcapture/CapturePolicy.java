package com.myanmar.cherrydirectcapture;

final class CapturePolicy {
    static boolean target(String pkg) {
        return "com.viber.voip".equals(pkg) || "com.facebook.orca".equals(pkg);
    }
    static boolean allow(boolean session, String eventPackage, String rootPackage) {
        return session && target(eventPackage) && eventPackage.equals(rootPackage);
    }
    private CapturePolicy() {}
}
