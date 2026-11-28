package com.myanmar.cherrydirectcapture;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
public class InboxLogicTest {
    @Test public void overlapsBothDirections() {
        assertArrayEquals(new boolean[]{true,true,false},InboxLogic.overlap(Arrays.asList("a","b","c"),Arrays.asList("b","c","d")));
        assertArrayEquals(new boolean[]{false,true,true},InboxLogic.overlap(Arrays.asList("b","c","d"),Arrays.asList("a","b","c")));
        assertArrayEquals(new boolean[]{false},InboxLogic.overlap(Collections.singletonList("a"),Collections.singletonList("b")));
    }
    @Test public void repeatedTextNotDeletedAndOriginalPreserved() {
        assertEquals("draft\n\n ၀၀\n12 \n\n ၀၀\n12 ",InboxLogic.append("draft",Arrays.asList(" ၀၀\n12 "," ၀၀\n12 ")));
        assertArrayEquals(new boolean[]{true,true},InboxLogic.overlap(Arrays.asList("x","x"),Arrays.asList("x","x")));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsEmptyTransfer() {
        InboxLogic.append("",Collections.singletonList(" "));
    }
}
