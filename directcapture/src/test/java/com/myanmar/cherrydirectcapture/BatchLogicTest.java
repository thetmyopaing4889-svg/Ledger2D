package com.myanmar.cherrydirectcapture;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class BatchLogicTest {
    @Test public void preserveSeparateEqualAndMultilineBodies(){
        List<String> rows=Arrays.asList("manual","00\n\n12 ","00\n\n12 ");
        BatchLogic.validate(rows);assertEquals(3,rows.size());assertEquals("00\n\n12 ",rows.get(1));assertEquals(rows.get(1),rows.get(2));
    }
    @Test public void ancestorPathsNotIdentity(){
        assertTrue(BatchLogic.relatedPath("0/1","0/1/2"));
        assertFalse(BatchLogic.relatedPath("0/1","0/10"));assertFalse(BatchLogic.relatedPath("","0/1"));
    }
    @Test(expected=IllegalArgumentException.class) public void emptyRejected(){BatchLogic.validate(Arrays.asList("good"," "));}
    @Test(expected=IllegalArgumentException.class) public void emptyBatchRejected(){BatchLogic.validate(Collections.emptyList());}
}
