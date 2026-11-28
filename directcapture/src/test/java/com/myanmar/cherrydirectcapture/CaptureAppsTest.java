package com.myanmar.cherrydirectcapture;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class CaptureAppsTest {
    @Test public void explicitlyAllowlistedPackagesOnly(){
        Set<String> seen=new HashSet<>();
        for(CaptureApps.App app:CaptureApps.ALL){
            assertTrue(seen.add(app.pkg));assertTrue(CapturePolicy.target(app.pkg));
            assertTrue(CapturePolicy.allowExplicit(true,false,false,false,app.pkg));
            assertFalse(CapturePolicy.allowExplicit(true,true,false,false,app.pkg));
        }
        assertFalse(CapturePolicy.target("com.android.settings"));
        assertFalse(CapturePolicy.target("com.whatsapp.fake"));
        assertFalse(CapturePolicy.target(null));
    }
    @Test public void onlyViberHasObservedAdapter(){
        assertTrue(CaptureApps.observed("com.viber.voip"));
        for(CaptureApps.App app:CaptureApps.ALL)if(!app.pkg.equals("com.viber.voip"))assertFalse(app.observed);
    }
}
