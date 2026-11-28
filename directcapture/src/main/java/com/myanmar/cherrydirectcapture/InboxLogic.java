package com.myanmar.cherrydirectcapture;
import java.util.List;
final class InboxLogic {
    static boolean[] overlap(List<String> previous,List<String> current) {
        boolean[] result=new boolean[current.size()];
        int max=Math.min(previous.size(),current.size());
        for(int n=max;n>=1;n--) {
            if(previous.subList(previous.size()-n,previous.size()).equals(current.subList(0,n))) {
                for(int i=0;i<n;i++) result[i]=true;
                break;
            }
        }
        for(int n=max;n>=1;n--) {
            if(current.subList(current.size()-n,current.size()).equals(previous.subList(0,n))) {
                for(int i=current.size()-n;i<current.size();i++) result[i]=true;
                break;
            }
        }
        return result;
    }
    static String append(String draft,List<String> rows) {
        StringBuilder out=new StringBuilder(draft);
        for(String row:rows) {
            if(row==null || row.trim().isEmpty()) throw new IllegalArgumentException("Empty selected entry");
            if(out.length()>0) out.append("\n\n");
            out.append(row);
        }
        return out.toString();
    }
    private InboxLogic() {}
}
