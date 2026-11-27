package com.myanmar.cherryocrbenchmark;
import org.junit.Test;
import static org.junit.Assert.*;
public class AccuracyTest {
    @Test public void vocabularyAndBlankArePinned(){
        java.util.List<String> v=CrnnEngine.vocab();assertEquals(272,v.size());assertEquals("<blank>",v.get(22));
        int a=v.indexOf("1"),b=v.indexOf("၂");
        assertEquals("11၂",CrnnEngine.ctc(new int[]{a,a,22,a,b,b,22}));
    }
    @Test public void numericTokensRetainZerosAndAmountErrors(){
        assertEquals(java.util.Arrays.asList("08","1000"),TextMetrics.numericTokens("၀၈ ၁၀၀၀"));
        assertNotEquals(TextMetrics.numericTokens("12 100"),TextMetrics.numericTokens("12 900"));
        assertNotEquals(TextMetrics.numericTokens("12 100\n12 100"),TextMetrics.numericTokens("12 100"));
        assertNotEquals(TextMetrics.numericTokens("12 100"),TextMetrics.numericTokens("12100"));
    }
}
