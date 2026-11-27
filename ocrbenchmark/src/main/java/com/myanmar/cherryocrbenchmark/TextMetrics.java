package com.myanmar.cherryocrbenchmark;

public final class TextMetrics {
    private TextMetrics() {}
    public static String normalize(String text) {
        StringBuilder out = new StringBuilder();
        text.codePoints().forEach(c -> out.appendCodePoint(c >= 0x1040 && c <= 0x1049 ? '0'+c-0x1040 : c));
        return out.toString().replace("\r\n","\n").replace('\r','\n').trim();
    }
    public static int distance(String a, String b) {
        int[] x=a.codePoints().toArray(), y=b.codePoints().toArray();
        int[] prev=new int[y.length+1], cur=new int[y.length+1];
        for(int j=0;j<=y.length;j++) prev[j]=j;
        for(int i=1;i<=x.length;i++) {
            cur[0]=i;
            for(int j=1;j<=y.length;j++) cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+(x[i-1]==y[j-1]?0:1));
            int[] t=prev; prev=cur; cur=t;
        }
        return prev[y.length];
    }
    public static int lineDistance(String a,String b) {
        String[] x=a.split("\\n",-1), y=b.split("\\n",-1);
        int[] prev=new int[y.length+1], cur=new int[y.length+1];
        for(int j=0;j<=y.length;j++) prev[j]=j;
        for(int i=1;i<=x.length;i++){
            cur[0]=i;
            for(int j=1;j<=y.length;j++)cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+(x[i-1].equals(y[j-1])?0:1));
            int[] t=prev;prev=cur;cur=t;
        }
        return prev[y.length];
    }
}
