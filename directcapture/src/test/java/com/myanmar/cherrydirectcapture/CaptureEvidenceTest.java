package com.myanmar.cherrydirectcapture;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class CaptureEvidenceTest {
    private CaptureEvidence.Node text(String b,String p,String bounds){return new CaptureEvidence.Node(b,p,bounds,"TEXT","","");}
    @Test public void onlyRelatedSameBoundsAreAliasCandidates(){
        List<CaptureEvidence.Node> n=Arrays.asList(text("00\n\n12","0/1","[1,2,3,4]"),text("00\n\n12","0/1/0","[1,2,3,4]"),text("00\n\n12","0/2","[1,2,3,4]"),text("00\n\n12","0/1/1","[2,3,4,5]"));
        assertArrayEquals(new boolean[]{true,true,false,false},CaptureEvidence.aliases(n));
        assertEquals(4,n.size());
    }
    @Test public void overlapBothDirectionsAndEqualSeparateMessages(){
        assertArrayEquals(new boolean[]{true,true,false},CaptureEvidence.overlap(Arrays.asList("a","b","c"),Arrays.asList("b","c","d")));
        assertArrayEquals(new boolean[]{false,true,true},CaptureEvidence.overlap(Arrays.asList("b","c","d"),Arrays.asList("a","b","c")));
        assertArrayEquals(new boolean[]{false,false},CaptureEvidence.overlap(Arrays.asList("x","x"),Arrays.asList("x","x")));
        assertArrayEquals(new boolean[]{false},CaptureEvidence.overlap(Arrays.asList("x"),Arrays.asList("x")));
    }
    @Test public void timestampIsOnlyUniqueSiblingHint(){
        CaptureEvidence.Node a=text("00","0/2/0","b");
        CaptureEvidence.Node t=new CaptureEvidence.Node("9:00","0/2/1","c","POSSIBLE_TIME","","");
        assertEquals("9:00",CaptureEvidence.timestampHint(Arrays.asList(a,t),0));
        assertEquals("",CaptureEvidence.timestampHint(Arrays.asList(a,t,text("12","0/2/2","d")),0));
        assertEquals("",CaptureEvidence.timestampHint(Arrays.asList(a,new CaptureEvidence.Node("9:00","0/3/1","e","POSSIBLE_TIME","","")),0));
    }
}
