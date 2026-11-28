package com.myanmar.cherrydirectcapture;
import org.json.*;
import java.util.*;
/** Candidate nodes only. No inferred message identity, no silent deduplication. */
final class BatchCandidates {
    static JSONArray build(JSONArray captures) throws JSONException {
        JSONArray rows=new JSONArray();Set<String> seen=new HashSet<>();
        for(int c=0;c<captures.length();c++){
            JSONObject snap=captures.getJSONObject(c);JSONArray nodes=snap.getJSONArray("nodes");
            for(int n=0;n<nodes.length();n++){
                JSONObject node=nodes.getJSONObject(n);String text=node.optString("text",""),desc=node.optString("description","");
                CaptureTextReview.Kind kind=CaptureTextReview.kind(text,desc);
                if(kind==CaptureTextReview.Kind.EMPTY)continue;
                String body=text.trim().isEmpty()?desc:text;
                JSONObject row=new JSONObject();
                row.put("source",snap.getLong("capture")+":"+(n+1));row.put("original",body);row.put("body",body);
                row.put("path",node.optString("path",""));row.put("bounds",node.optString("bounds",""));
                row.put("kind",kind.name());row.put("include",kind==CaptureTextReview.Kind.TEXT);
                row.put("repeat",!seen.add(body));rows.put(row);
            }
        }return rows;
    }
    private BatchCandidates(){}
}
