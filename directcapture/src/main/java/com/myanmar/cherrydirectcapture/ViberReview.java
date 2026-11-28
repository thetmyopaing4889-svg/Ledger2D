package com.myanmar.cherrydirectcapture;
import org.json.*;
import java.util.*;
final class ViberReview {
    static boolean supported(JSONArray captures){
        if(captures.length()==0)return false;
        for(int i=0;i<captures.length();i++)if(!ViberStructure.PKG.equals(captures.optJSONObject(i).optString("package")))return false;
        return true;
    }
    static ViberStructure.Screen screen(JSONObject snap)throws JSONException{
        JSONArray nodes=snap.getJSONArray("nodes");List<ViberStructure.Node> list=new ArrayList<>();
        for(int i=0;i<nodes.length();i++){
            JSONObject n=nodes.getJSONObject(i);JSONArray b=n.optJSONArray("bounds");int[] bounds=new int[b==null?0:b.length()];
            for(int k=0;k<bounds.length;k++)bounds[k]=b.optInt(k);
            list.add(new ViberStructure.Node(i,n.optString("view_id",""),n.optString("parent_id",""),n.optString("path",""),n.optString("text",""),n.optString("description",""),bounds));
        }return ViberStructure.parse(list);
    }
    static String suggestedChat(JSONArray captures)throws JSONException{
        if(!supported(captures))return "";
        String title=null;
        for(int i=0;i<captures.length();i++){String t=screen(captures.getJSONObject(i)).title;if(t.isEmpty()||title!=null&&!title.equals(t))return "";title=t;}
        return title==null?"":title;
    }
    static boolean mixedChats(JSONArray captures)throws JSONException{
        if(!supported(captures))return false;
        Set<String> titles=new HashSet<>();
        for(int i=0;i<captures.length();i++){String t=screen(captures.getJSONObject(i)).title;if(!t.isEmpty())titles.add(t);}
        return titles.size()>1;
    }
    static JSONArray build(JSONArray captures)throws JSONException{
        List<JSONObject> active=new ArrayList<>(),hidden=new ArrayList<>();
        ViberStructure.Screen previous=null;List<JSONObject> prevRows=new ArrayList<>();
        for(int c=0;c<captures.length();c++){
            JSONObject snap=captures.getJSONObject(c);ViberStructure.Screen screen=screen(snap);
            JSONArray nodes=snap.getJSONArray("nodes");List<JSONObject> next=new ArrayList<>();Set<Integer> used=new HashSet<>();
            for(ViberStructure.Message m:screen.messages){
                JSONObject row=row(snap,nodes.getJSONObject(m.node.index),m.node.index);
                row.put("kind","VIBER_MESSAGE");row.put("include",true);row.put("sent_time",m.time);
                row.put("chat_hint",screen.title);next.add(row);used.add(m.node.index);
            }
            ViberStructure.Match match=previous==null?null:ViberStructure.overlap(previous,screen);
            // Only merge at a contiguous edge of the accumulated ordered list. If the scroll
            // jumps into an interior or the structure is uncertain, retain all for source review.
            int anchor=-1;
            if(match!=null){
                anchor=active.indexOf(prevRows.get(match.oldStart));
                if(anchor<0||anchor+match.count>active.size())match=null;
                else{
                    for(int k=0;k<match.count;k++)if(active.get(anchor+k)!=prevRows.get(match.oldStart+k)){match=null;break;}
                }
                if(match!=null&&((match.newStart>0&&anchor!=0)||(match.newStart+match.count<next.size()&&anchor+match.count!=active.size())))match=null;
            }
            if(match==null){
                for(JSONObject r:next){r.put("unlinked_capture",c>0);active.add(r);}
            }else{
                for(int k=0;k<match.count;k++){
                    int n=match.newStart+k;JSONObject duplicate=next.get(n),original=prevRows.get(match.oldStart+k);
                    duplicate.put("include",false);duplicate.put("kind","SCROLL_OVERLAP");duplicate.put("overlap_of",original.getString("source"));
                    hidden.add(duplicate);next.set(n,original);
                }
                if(match.newStart>0)active.addAll(0,next.subList(0,match.newStart));
                if(match.newStart+match.count<next.size())active.addAll(next.subList(match.newStart+match.count,next.size()));
            }
            for(int i=0;i<nodes.length();i++)if(!used.contains(i)){
                JSONObject n=nodes.getJSONObject(i);JSONObject r=row(snap,n,i);
                if(r.getString("body").trim().isEmpty())continue;
                r.put("include",false);r.put("kind",screen.knownUi.contains(i)?"VIBER_UI":"UNRECOGNIZED_NODE");hidden.add(r);
            }
            previous=screen;prevRows=next;
        }
        JSONArray out=new JSONArray();for(JSONObject r:active)out.put(r);for(JSONObject r:hidden)out.put(r);return out;
    }
    private static JSONObject row(JSONObject snap,JSONObject node,int index)throws JSONException{
        String t=node.optString("text",""),body=t.trim().isEmpty()?node.optString("description",""):t;
        JSONObject r=new JSONObject();r.put("source",snap.getLong("capture")+":"+(index+1));r.put("capture",snap.getLong("capture"));
        r.put("original",body);r.put("body",body);r.put("path",node.optString("path",""));r.put("bounds",node.optString("bounds",""));
        r.put("view_id",node.optString("view_id",""));r.put("captured_ms",snap.optLong("captured_ms",0));return r;
    }
    private ViberReview(){}
}
