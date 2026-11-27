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
}
