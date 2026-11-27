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

    @Test public void overlayRoutingUsesFocusedAppWithoutRelaxingCaptureGate() {
        assertEquals("com.viber.voip",CapturePolicy.overlayTarget("com.myanmar.cherrydirectcapture","com.viber.voip"));
        assertEquals("com.facebook.orca",CapturePolicy.overlayTarget("","com.facebook.orca"));
        assertEquals("",CapturePolicy.overlayTarget("com.viber.voip","com.android.settings"));
        assertEquals("",CapturePolicy.overlayTarget("com.viber.voip",""));
        assertEquals("",CapturePolicy.overlayTarget("com.viber.voip","com.facebook.orca"));
        assertEquals("com.viber.voip",CapturePolicy.overlayTarget("com.viber.voip",null));
        assertEquals("",CapturePolicy.overlayTarget("com.android.settings",null));
        assertFalse(CapturePolicy.allow(true,"com.viber.voip","com.myanmar.cherrydirectcapture"));
    }
}
