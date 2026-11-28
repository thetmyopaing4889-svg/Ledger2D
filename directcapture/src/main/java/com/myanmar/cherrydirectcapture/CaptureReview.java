package com.myanmar.cherrydirectcapture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Candidate blocks, not verified message bubbles. Raw traversal order is retained. */
final class CaptureReview {
    static final class Entry {
        final int index;
        final String text,description,viewId,bounds;
        final CaptureTextReview.Kind kind;
        Entry(int index,JSONObject node) {
            this.index=index;
            text=node.optString("text","");
            description=node.optString("description","");
            viewId=node.isNull("view_id")?"":node.optString("view_id","");
            bounds=node.optString("bounds","");
            kind=CaptureTextReview.kind(text,description);
        }
    }
    static List<Entry> entries(JSONArray nodes) throws org.json.JSONException {
        List<Entry> out=new ArrayList<>();
        for(int i=0;i<nodes.length();i++) out.add(new Entry(i+1,nodes.getJSONObject(i)));
        return out;
    }
    private CaptureReview() {}
}
