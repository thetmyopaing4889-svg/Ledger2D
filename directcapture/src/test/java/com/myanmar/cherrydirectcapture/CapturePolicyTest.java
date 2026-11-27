package com.myanmar.cherrydirectcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class CapturePolicyTest {
    @Test public void requiresExplicitSession() {
        assertFalse(CapturePolicy.allow(false,"com.viber.voip","com.viber.voip"));
        assertTrue(CapturePolicy.allow(true,"com.viber.voip","com.viber.voip"));
        assertTrue(CapturePolicy.allow(true,"com.facebook.orca","com.facebook.orca"));
    }
    @Test public void rejectsMismatchedAndOtherApps() {
        assertFalse(CapturePolicy.allow(true,"com.viber.voip","com.facebook.orca"));
        assertFalse(CapturePolicy.allow(true,"com.viber.voip","com.android.settings"));
        assertFalse(CapturePolicy.allow(true,"com.android.settings","com.android.settings"));
        assertFalse(CapturePolicy.allow(true,null,null));
    }

    @Test public void explicitCaptureRequiresUnlockedTargetAndArmedSession() {
        assertTrue(CapturePolicy.allowExplicit(true,false,false,false,"com.viber.voip"));
        assertTrue(CapturePolicy.allowExplicit(true,false,false,false,"com.facebook.orca"));
        assertFalse(CapturePolicy.allowExplicit(false,false,false,false,"com.viber.voip"));
        assertFalse(CapturePolicy.allowExplicit(true,true,false,false,"com.viber.voip"));
        assertFalse(CapturePolicy.allowExplicit(true,false,true,false,"com.viber.voip"));
        assertFalse(CapturePolicy.allowExplicit(true,false,false,true,"com.viber.voip"));
        assertFalse(CapturePolicy.allowExplicit(true,false,false,false,"com.android.settings"));
        assertFalse(CapturePolicy.allowExplicit(true,false,false,false,null));
    }
}
