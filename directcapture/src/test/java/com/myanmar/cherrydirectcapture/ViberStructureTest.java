package com.myanmar.cherrydirectcapture;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class ViberStructureTest {
    // De-identified regression derived from the supplied 2 captures.
    // The exact message bounds, path pattern and displayed times are preserved.
    private static final String[] B={"Sample A\nsecond\nthird\nfourth","Sample B\nsecond\nthird","Sample C\nsecond","Sample D\n2\n3\n4\n5\n6","Sample E\nsecond\nthird","Sample F","Sample G","Sample H","Sample I"};
    private static final String[] T={"12:07 PM","12:08 PM","12:08 PM","3:09 PM","3:10 PM","3:10 PM","3:10 PM","3:20 PM","3:22 PM"};
    private static ViberStructure.Node node(int index,String id,String parent,String path,String text,int[] bounds){
        return new ViberStructure.Node(index,ViberStructure.ID+id,parent,path,text,"",bounds);
    }
    private ViberStructure.Screen screen(String title,int start,int[][] bounds,int pathStart){
        List<ViberStructure.Node> nodes=new ArrayList<>();
        nodes.add(node(0,"title",ViberStructure.ID+"toolbar","0/0/1",title,new int[]{288,165,707,222}));
        nodes.add(node(1,"subtitle",ViberStructure.ID+"toolbar","0/0/2","Last seen",new int[]{288,222,812,264}));
        for(int i=0;i<bounds.length;i++){
            String p="0/1/1/"+(i+pathStart);
            nodes.add(node(nodes.size(),"textMessageView","",p+"/1",B[start+i],bounds[i]));
            nodes.add(node(nodes.size(),"timestampView","",p+"/4",T[start+i],new int[]{1,1,2,2}));
            nodes.add(node(nodes.size(),"translateButtonText",ViberStructure.ID+"translateButtonView",p+"/3/0","Translate",new int[]{1,1,2,2}));
        }
        return ViberStructure.parse(nodes);
    }
    @Test public void actualGeometryFiveAndSevenWithThreeOverlap(){
        ViberStructure.Screen a=screen("Demo",0,new int[][]{{36,322,555,646},{36,796,555,1111},{36,1261,555,1471},{36,1621,466,1963},{36,2017,535,2332}},0);
        ViberStructure.Screen b=screen("Demo",2,new int[][]{{36,335,555,545},{36,695,466,1037},{36,1091,535,1406},{36,1556,496,1661},{36,1811,653,1916},{36,2066,475,2171},{36,2321,623,2426}},1);
        assertEquals(5,a.messages.size());assertEquals(7,b.messages.size());assertEquals("Demo",a.title);
        for(int i=0;i<5;i++){assertEquals(B[i],a.messages.get(i).node.text);assertEquals(T[i],a.messages.get(i).time);}
        for(int i=0;i<7;i++)assertEquals(T[i+2],b.messages.get(i).time);
        ViberStructure.Match m=ViberStructure.overlap(a,b);assertNotNull(m);assertEquals(2,m.oldStart);assertEquals(0,m.newStart);assertEquals(3,m.count);
        assertEquals(9,a.messages.size()+b.messages.size()-m.count);
        ViberStructure.Match reverse=ViberStructure.overlap(b,a);assertNotNull(reverse);assertEquals(0,reverse.oldStart);assertEquals(2,reverse.newStart);
        assertEquals(12,a.knownUi.size());
    }
    @Test public void differentChatOrInconsistentScrollNeverMerged(){
        ViberStructure.Screen a=screen("Demo",0,new int[][]{{0,100,50,120},{0,200,50,220}},0);
        assertNull(ViberStructure.overlap(a,screen("Other",0,new int[][]{{0,90,50,110},{0,190,50,210}},0)));
        assertNull(ViberStructure.overlap(a,screen("Demo",0,new int[][]{{0,90,50,110},{0,170,50,190}},0)));
    }
    @Test public void singleMatchAndRepeatedBodyTimeNotEnough(){
        ViberStructure.Screen a=screen("Demo",0,new int[][]{{0,100,50,120}},0);
        assertNull(ViberStructure.overlap(a,a));
        ViberStructure.Message m=a.messages.get(0);
        ViberStructure.Screen repeated=new ViberStructure.Screen("Demo",Arrays.asList(m,m),Collections.emptySet());
        assertNull(ViberStructure.overlap(repeated,repeated));
    }
    @Test public void messageNamedTranslateRemainsMessage(){
        List<ViberStructure.Node> nodes=Arrays.asList(node(0,"textMessageView","","0/1/1/0/1","Translate",new int[]{1,1,2,2}),node(1,"timestampView","","0/1/1/0/2","3:10 PM",new int[]{1,1,2,2}));
        assertEquals("Translate",ViberStructure.parse(nodes).messages.get(0).node.text);
    }
}
