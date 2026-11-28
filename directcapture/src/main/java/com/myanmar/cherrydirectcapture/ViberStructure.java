package com.myanmar.cherrydirectcapture;
import java.util.*;
/** Adapter for observed Viber resource IDs only. Geometry is evidence, never a stable message ID. */
final class ViberStructure {
    static final String PKG="com.viber.voip", ID=PKG+":id/";
    static final class Node {
        final int index;final String id,parent,path,text,desc;final int[] bounds;
        Node(int index,String id,String parent,String path,String text,String desc,int[] bounds){
            this.index=index;this.id=id;this.parent=parent;this.path=path;this.text=text;this.desc=desc;this.bounds=bounds;
        }
        boolean is(String name){return (ID+name).equals(id);}
    }
    static final class Message {
        final Node node;final String time;
        Message(Node node,String time){this.node=node;this.time=time;}
        boolean same(Message m){return !time.isEmpty()&&time.equals(m.time)&&node.text.equals(m.node.text);}
        String key(){return time+"\u0000"+node.text;}
    }
    static final class Screen {
        final String title;final List<Message> messages;final Set<Integer> knownUi;
        Screen(String title,List<Message> messages,Set<Integer> knownUi){this.title=title;this.messages=messages;this.knownUi=knownUi;}
    }
    static Screen parse(List<Node> nodes){
        String title="";int titles=0;
        Map<String,List<Node>> times=new HashMap<>();Map<String,Integer> bodies=new HashMap<>();
        Set<Integer> knownUi=new HashSet<>();List<Message> messages=new ArrayList<>();
        for(Node n:nodes){
            if(n.is("title")&&(ID+"toolbar").equals(n.parent)){title=n.text;titles++;}
            if(n.is("timestampView"))times.computeIfAbsent(CaptureEvidence.parentPath(n.path),k->new ArrayList<>()).add(n);
            if(n.is("textMessageView")){String parent=CaptureEvidence.parentPath(n.path);bodies.put(parent,bodies.getOrDefault(parent,0)+1);}
            if((ID+"toolbar").equals(n.parent)||n.is("timestampView")||n.is("reactionView")||n.is("translateButtonText")||
                n.is("options_menu_open_stickers")||n.is("options_menu_open_gallery")||n.is("btn_send")||
                (n.id.isEmpty()&&n.text.equals("Suggest replies"))||(n.id.isEmpty()&&n.desc.equals("Polish message")))knownUi.add(n.index);
        }
        for(Node n:nodes)if(n.is("textMessageView")&&!n.text.trim().isEmpty()){
            String parent=CaptureEvidence.parentPath(n.path);List<Node> t=times.get(parent);
            String time=!parent.isEmpty()&&bodies.getOrDefault(parent,0)==1&&t!=null&&t.size()==1&&
                CaptureTextReview.kind(t.get(0).text,"")==CaptureTextReview.Kind.POSSIBLE_TIME?t.get(0).text:"";
            messages.add(new Message(n,time));
        }
        // Top-to-bottom within a snapshot; traversal order breaks exact ties.
        messages.sort(Comparator.comparingInt((Message m)->m.node.bounds.length==4?m.node.bounds[1]:Integer.MAX_VALUE).thenComparingInt(m->m.node.index));
        return new Screen(titles==1?title:"",messages,knownUi);
    }
    static final class Match {
        final int oldStart,newStart,count;
        Match(int oldStart,int newStart,int count){this.oldStart=oldStart;this.newStart=newStart;this.count=count;}
    }
    static Match overlap(Screen a,Screen b){
        if(a.title.isEmpty()||!a.title.equals(b.title))return null;
        List<Message> x=a.messages,y=b.messages;
        List<Match> candidates=new ArrayList<>();
        int max=Math.min(200,Math.min(x.size(),y.size()));
        for(int n=2;n<=max;n++){
            if(evidence(x,x.size()-n,y,0,n))candidates.add(new Match(x.size()-n,0,n));
            if(evidence(x,0,y,y.size()-n,n)){
                Match m=new Match(0,y.size()-n,n);
                if(!(x.size()==n&&y.size()==n))candidates.add(m);
            }
        }
        return candidates.size()==1?candidates.get(0):null;
    }
    private static boolean evidence(List<Message> x,int i,List<Message> y,int j,int n){
        Set<String> keys=new HashSet<>();Integer shift=null;
        for(int k=0;k<n;k++){
            Message a=x.get(i+k),b=y.get(j+k);
            if(!a.same(b)||!keys.add(a.key()))return false;
            // Repeated identical body+time anywhere in either screen makes identity ambiguous.
            if(frequency(x,a)!=1||frequency(y,b)!=1)return false;
            int[] p=a.node.bounds,q=b.node.bounds;
            if(p.length!=4||q.length!=4||p[2]<=p[0]||p[3]<=p[1]||q[2]<=q[0]||q[3]<=q[1])return false;
            if(Math.abs(p[0]-q[0])>4||Math.abs(p[2]-q[2])>4||Math.abs((p[3]-p[1])-(q[3]-q[1]))>4)return false;
            int dy=q[1]-p[1];
            if(shift==null)shift=dy;else if(Math.abs(shift-dy)>4)return false;
        }
        return true;
    }
    private static int frequency(List<Message> list,Message target){int n=0;for(Message m:list)if(m.same(target))n++;return n;}
    private ViberStructure(){}
}
