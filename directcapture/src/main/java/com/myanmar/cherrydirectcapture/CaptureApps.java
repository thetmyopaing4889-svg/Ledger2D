package com.myanmar.cherrydirectcapture;
import java.util.*;
/** Explicit test allowlist. Only Viber has observed device evidence. */
final class CaptureApps {
    static final class App {
        final String pkg,name;final int accent,background,bubble,radius;final boolean observed;
        App(String pkg,String name,int accent,int background,int bubble,int radius,boolean observed){
            this.pkg=pkg;this.name=name;this.accent=accent;this.background=background;this.bubble=bubble;this.radius=radius;this.observed=observed;
        }
    }
    static final List<App> ALL=Collections.unmodifiableList(Arrays.asList(
        new App("com.viber.voip","Viber",0xff7360f2,0xfff2f3f7,0xffffffff,14,true),
        new App("com.facebook.orca","Messenger",0xff0866ff,0xffffffff,0xffe9e9eb,22,false),
        new App("com.google.android.apps.messaging","Messages",0xff1967d2,0xffffffff,0xffedf0f5,22,false),
        new App("com.android.mms","Messages (SMS)",0xff1967d2,0xffffffff,0xffedf0f5,22,false),
        new App("com.samsung.android.messaging","Samsung Messages",0xff1967d2,0xffffffff,0xffedf0f5,22,false),
        new App("org.telegram.messenger","Telegram",0xff279ed9,0xffdce8df,0xffffffff,10,false),
        new App("org.telegram.messenger.web","Telegram",0xff279ed9,0xffdce8df,0xffffffff,10,false),
        new App("com.whatsapp","WhatsApp",0xff075e54,0xffefe8de,0xffffffff,8,false),
        new App("com.whatsapp.w4b","WhatsApp Business",0xff075e54,0xffefe8de,0xffffffff,8,false),
        new App("jp.naver.line.android","LINE",0xff06a94a,0xffb4c7de,0xffffffff,14,false),
        new App("com.tencent.mm","WeChat",0xff07a85a,0xffededed,0xffffffff,10,false)
    ));
    static App find(String pkg){for(App app:ALL)if(app.pkg.equals(pkg))return app;return null;}
    static boolean allowed(String pkg){return find(pkg)!=null;}
    static boolean observed(String pkg){App app=find(pkg);return app!=null&&app.observed;}
    static String name(String pkg){App app=find(pkg);return app==null?"Chat":app.name;}
    private CaptureApps(){}
}
