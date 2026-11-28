package com.myanmar.cherrydirectcapture;

import java.util.Locale;
import java.util.regex.Pattern;

/** Read-only classification. Never modifies, normalizes or deduplicates source text. */
final class CaptureTextReview {
    enum Kind { TEXT, POSSIBLE_UI, POSSIBLE_TIME, DESCRIPTION_ONLY, EMPTY }
    private static final Pattern CLOCK=Pattern.compile(
        "^(?:[0-9၀-၉]{1,2}):[0-9၀-၉]{2}(?:[ \\t]*(?:AM|PM|am|pm))?$");
    static Kind kind(String text,String description) {
        if(text==null || text.trim().isEmpty())
            return description==null || description.trim().isEmpty()?Kind.EMPTY:Kind.DESCRIPTION_ONLY;
        // Exact labels only; even exact matches may be genuine messages.
        // Multiline messages and numeric payloads remain untouched.
        String key=text.trim().toLowerCase(Locale.ROOT);
        if(text.indexOf('\n')<0 && text.indexOf('\r')<0 &&
            (key.equals("translate") || key.equals("no likes") ||
             key.equals("suggest replies") || key.equals("free call") ||
             key.equals("video call"))) return Kind.POSSIBLE_UI;
        if(CLOCK.matcher(text.trim()).matches()) return Kind.POSSIBLE_TIME;
        return Kind.TEXT;
    }
    private CaptureTextReview() {}
}
