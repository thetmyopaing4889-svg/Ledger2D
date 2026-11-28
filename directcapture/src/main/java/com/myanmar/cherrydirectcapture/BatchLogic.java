package com.myanmar.cherrydirectcapture;
import java.util.List;
final class BatchLogic {
    static void requireBody(String s){
        if(s==null||s.trim().isEmpty()||s.length()>250000)throw new IllegalArgumentException("Invalid body");
    }
    static void validate(List<String> rows){
        if(rows.isEmpty())throw new IllegalArgumentException("No messages");
        long size=0;
        for(String s:rows){requireBody(s);size+=s.length();}
        if(size>250000)throw new IllegalArgumentException("Batch too large");
    }
    static boolean relatedPath(String a,String b){return !a.isEmpty()&&!b.isEmpty()&&(a.startsWith(b+"/")||b.startsWith(a+"/"));}
    private BatchLogic(){}
}
