package com.myanmar.cherrydirectcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class PermissionRecoveryTest {
    private static final String P="com.myanmar.cherrydirectcapture", C=P+".CaptureService", OWN=P+"/"+C;
    @Test public void emptyList(){assertEquals(OWN,PermissionState.appendOwn(null,P,C));assertEquals(OWN,PermissionState.appendOwn("",P,C));}
    @Test public void preserveOtherComponents(){String other="a.b/.First:c.d/c.d.Second";assertEquals(other+":"+OWN,PermissionState.appendOwn(other,P,C));}
    @Test public void alreadyListed(){String list="a.b/.First:"+P+"/.CaptureService";assertEquals(list,PermissionState.appendOwn(list,P,C));}
    @Test public void trailingSeparator(){assertEquals("a.b/.First:"+OWN,PermissionState.appendOwn("a.b/.First:",P,C));}
    @Test public void similarNameNotOwn(){String list=P+"/.Other";assertEquals(list+":"+OWN,PermissionState.appendOwn(list,P,C));}
}
