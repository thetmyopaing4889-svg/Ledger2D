package com.myanmar.cherrydirectcapture;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.myanmar.cherrydirectcapture.CaptureTextReview.Kind.*;
public class CaptureTextReviewTest {
    @Test public void keepsNumeralsAndMultilineText() {
        assertEquals(TEXT,CaptureTextReview.kind("၀၁ ၂၃ ၀၀\n12 99 500",""));
        assertEquals(TEXT,CaptureTextReview.kind("Translate\n၀၁",""));
        assertEquals(TEXT,CaptureTextReview.kind("No likes today",""));
        assertEquals(TEXT,CaptureTextReview.kind(" ၀၀ ",""));
        assertEquals(TEXT,CaptureTextReview.kind("12:01 50",""));
    }
    @Test public void marksOnlyExactUiAndClockCandidates() {
        assertEquals(POSSIBLE_UI,CaptureTextReview.kind("Translate",""));
        assertEquals(POSSIBLE_UI,CaptureTextReview.kind("No likes",""));
        assertEquals(POSSIBLE_TIME,CaptureTextReview.kind("12:01 PM",""));
        assertEquals(POSSIBLE_TIME,CaptureTextReview.kind("၁၂:၀၁",""));
        assertEquals(DESCRIPTION_ONLY,CaptureTextReview.kind("","၀၁ ၅၀"));
        assertEquals(EMPTY,CaptureTextReview.kind("",""));
        assertEquals(TEXT,CaptureTextReview.kind("Chat name",""));
    }
}
