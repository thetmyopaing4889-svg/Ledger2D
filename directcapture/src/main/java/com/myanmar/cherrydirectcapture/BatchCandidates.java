package com.myanmar.cherrydirectcapture;
import org.json.*;
import java.util.*;
/** Candidate evidence only; no automatic deletion or message identity assertion. */
final class BatchCandidates {
    static JSONArray build(JSONArray captures) throws JSONException {
        if(ViberReview.supported(captures))return ViberReview.build(captures);
        JSONArray rows=new JSONArray();Set<String> seen=new HashSet<>();
        List<String> previous=new ArrayList<>();String previousPackage="";
        for(int c=0;c<captures.length();c++){
            JSONObject snap=captures.getJSONObject(c);JSONArray nodes=snap.getJSONArray("nodes");
            List<CaptureEvidence.Node> evidence=new ArrayList<>();List<Integer> indices=new ArrayList<>();
            List<String> sequence=new ArrayList<>();
            for(int n=0;n<nodes.length();n++){
                JSONObject node=nodes.getJSONObject(n);String text=node.optString("text",""),desc=node.optString("description","");
                CaptureTextReview.Kind kind=CaptureTextReview.kind(text,desc);
                String body=text.trim().isEmpty()?desc:text;
                evidence.add(new CaptureEvidence.Node(body,node.optString("path",""),node.optString("bounds",""),kind.name(),node.optString("parent_id",""),node.optString("view_id","")));
                if(kind==CaptureTextReview.Kind.TEXT){indices.add(n);sequence.add(body);}
            }
            boolean[] alias=CaptureEvidence.aliases(evidence);
            String pkg=snap.optString("package","");
            boolean[] overlap=(!pkg.isEmpty()&&pkg.equals(previousPackage))?CaptureEvidence.overlap(previous,sequence):new boolean[sequence.size()];
            Set<Integer> overlapNodes=new HashSet<>();
            for(int i=0;i<overlap.length;i++)if(overlap[i])overlapNodes.add(indices.get(i));
            for(int n=0;n<evidence.size();n++){
                CaptureEvidence.Node e=evidence.get(n);
                if(e.kind.equals("EMPTY"))continue;
                JSONObject row=new JSONObject();
                row.put("source",snap.getLong("capture")+":"+(n+1));row.put("original",e.body);row.put("body",e.body);
                row.put("capture",snap.getLong("capture"));row.put("captured_ms",snap.optLong("captured_ms",0));
                row.put("path",e.path);row.put("bounds",e.bounds);row.put("parent_id",e.parent);row.put("view_id",e.viewId);
                row.put("kind",e.kind);row.put("include",e.text());
                row.put("repeat",e.text()&&!seen.add(e.body));row.put("alias_hint",alias[n]);row.put("overlap_hint",overlapNodes.contains(n));
                row.put("time_hint",CaptureEvidence.timestampHint(evidence,n));
                rows.put(row);
            }
            previous=sequence;previousPackage=pkg;
        }return rows;
    }
    private BatchCandidates(){}
}
