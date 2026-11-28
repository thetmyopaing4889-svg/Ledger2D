package com.myanmar.cherrydirectcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class OrbPositionTest {
    @Test public void clampWithinScreen(){
        assertEquals(0,OrbPosition.clamp(-20,300,60));
        assertEquals(240,OrbPosition.clamp(290,300,60));
        assertEquals(140,OrbPosition.clamp(140,300,60));
        assertEquals(0,OrbPosition.clamp(140,50,60));
    }
}
