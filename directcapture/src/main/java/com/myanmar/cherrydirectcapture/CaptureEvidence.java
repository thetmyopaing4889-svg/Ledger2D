package com.myanmar.cherrydirectcapture;
import java.util.*;
/** Evidence only. No text equality, path or timestamp constitutes a stable message ID. */
final class CaptureEvidence {
    static final class Node {
        final String body,path,bounds,kind,parent,viewId;
        Node(String body,String path,String bounds,String kind,String parent,String viewId){
            this.body=body;this.path=path;this.bounds=bounds;this.kind=kind;this.parent=parent;this.viewId=viewId;
        }
        boolean text(){return "TEXT".equals(kind);}
    }
    static String parentPath(String path){int i=path.lastIndexOf('/');return i<0?"":path.substring(0,i);}
    static boolean[] aliases(List<Node> nodes){
        boolean[] flags=new boolean[nodes.size()];
        Map<String,Integer> paths=new HashMap<>();
        for(int i=0;i<nodes.size();i++)if(!nodes.get(i).path.isEmpty())paths.put(nodes.get(i).path,i);
        for(int i=0;i<nodes.size();i++){
            Node n=nodes.get(i);
            if(!n.text()||n.path.isEmpty()||n.bounds.isEmpty())continue;
            String parent=parentPath(n.path);
            while(!parent.isEmpty()){
                Integer index=paths.get(parent);
                if(index!=null){
                    Node p=nodes.get(index);
                    if(p.text()&&p.body.equals(n.body)&&p.bounds.equals(n.bounds)){
                        flags[index]=true;flags[i]=true;
                    }
                }
                parent=parentPath(parent);
            }
        }
        return flags;
    }
    static boolean[] overlap(List<String> previous,List<String> current){
        boolean[] flags=new boolean[current.size()];
        // Only sequences of at least two different bodies count as stronger overlap evidence.
        // Bound comparison cost; entire identical screens are still only review candidates.
        int max=Math.min(200,Math.min(previous.size(),current.size()));
        for(int n=max;n>=2;n--){
            List<String> a=previous.subList(previous.size()-n,previous.size()),b=current.subList(0,n);
            if(a.equals(b)&&new HashSet<>(b).size()>1){Arrays.fill(flags,0,n,true);break;}
        }
        for(int n=max;n>=2;n--){
            List<String> a=current.subList(current.size()-n,current.size()),b=previous.subList(0,n);
            if(a.equals(b)&&new HashSet<>(a).size()>1){Arrays.fill(flags,current.size()-n,current.size(),true);break;}
        }
        return flags;
    }
    static String timestampHint(List<Node> nodes,int index){
        Node n=nodes.get(index);String parent=parentPath(n.path);
        if(!n.text()||parent.isEmpty()||parent.equals("0"))return "";
        String time="";int texts=0,times=0;
        for(Node other:nodes){
            if(!parentPath(other.path).equals(parent))continue;
            if(other.text())texts++;
            if("POSSIBLE_TIME".equals(other.kind)){times++;time=other.body;}
        }
        return texts==1&&times==1?time:"";
    }
    private CaptureEvidence(){}
}
