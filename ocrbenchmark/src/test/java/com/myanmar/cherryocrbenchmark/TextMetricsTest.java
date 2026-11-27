package com.myanmar.cherryocrbenchmark;
import org.junit.Test;
import static org.junit.Assert.*;
public class TextMetricsTest {
    @Test public void digitsAreNormalizedOnlyInSeparateMetric(){
        assertEquals("08 ခွေ 1000",TextMetrics.normalize("၀၈ ခွေ ၁၀၀၀"));
        assertEquals(2,TextMetrics.distance("၀၈","08"));
        assertEquals(0,TextMetrics.distance(TextMetrics.normalize("၀၈"),"08"));
    }
    @Test public void missingAndRepeatedLinesCount(){
        assertEquals(1,TextMetrics.lineDistance("12 100\n12 100","12 100"));
        assertEquals(1,TextMetrics.lineDistance("12 100","12 100\n12 100"));
        assertEquals(1,TextMetrics.distance("08","09"));
    }
}
