package com.myanmar.cherrydirectcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class PermissionStateTest {
    @Test public void exactComponentOnly(){
        String pkg="com.example",cls="com.example.CaptureService";
        assertTrue(PermissionState.containsComponent("other/.Service:com.example/.CaptureService",pkg,cls));
        assertTrue(PermissionState.containsComponent("com.example/com.example.CaptureService",pkg,cls));
        assertFalse(PermissionState.containsComponent("com.example.fake/.CaptureService:com.example/.CaptureServiceOther",pkg,cls));
        assertFalse(PermissionState.containsComponent(null,pkg,cls));
    }
    @Test public void permissionAndConnectionIndependent(){
        assertEquals(PermissionState.Status.DISABLED,PermissionState.resolve(true,false,true,true));
        assertEquals(PermissionState.Status.CONNECTING,PermissionState.resolve(true,true,true,false));
        assertEquals(PermissionState.Status.CONNECTING,PermissionState.resolve(true,true,false,true));
        assertEquals(PermissionState.Status.READY,PermissionState.resolve(true,true,true,true));
        assertEquals(PermissionState.Status.UNKNOWN,PermissionState.resolve(false,true,true,true));
    }
    @Test public void reconnectDoesNotRequireNewGrant(){
        assertEquals(PermissionState.Status.READY,PermissionState.resolve(true,true,true,true));
        assertEquals(PermissionState.Status.CONNECTING,PermissionState.resolve(true,true,true,false));
        assertEquals(PermissionState.Status.READY,PermissionState.resolve(true,true,true,true));
    }
}
