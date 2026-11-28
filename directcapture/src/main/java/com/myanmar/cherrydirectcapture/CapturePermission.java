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
    private CapturePermission(){}
}
