package com.myanmar.cherrydirectcapture;
import android.content.Context;
import android.provider.Settings;
final class CapturePermission {
    static PermissionState.Status status(Context c){
        try{
            String enabled=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean listed=PermissionState.containsComponent(enabled,c.getPackageName(),CaptureService.class.getName());
            boolean global=Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)==1;
            return PermissionState.resolve(true,listed,global,CaptureService.connected());
        }catch(RuntimeException e){return PermissionState.Status.UNKNOWN;}
    }
    static boolean special(Context c){
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")==android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    // Called only after an explicit foreground user click. Never alter unrelated components.
    static boolean restoreOwn(Context c){
        if(!special(c)||status(c)!=PermissionState.Status.DISABLED)return false;
        try{
            String before=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String after=PermissionState.appendOwn(before,c.getPackageName(),CaptureService.class.getName());
            if(!after.equals(before)){
                // Detect changes before writing; Android provides no atomic compare-and-set here.
                if(!java.util.Objects.equals(before,Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)))return false;
                if(!Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,after))return false;
            }
            // AccessibilityManager derives its global state from bound/enabled services.
            return PermissionState.containsComponent(Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),c.getPackageName(),CaptureService.class.getName());
        }catch(RuntimeException e){return false;}
    }
    private CapturePermission(){}
}
