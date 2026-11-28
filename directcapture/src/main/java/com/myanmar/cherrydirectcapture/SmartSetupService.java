package com.myanmar.cherrydirectcapture;

import android.app.*;
import android.content.*;
import android.os.*;
import android.content.pm.PackageManager;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Temporary, isolated user-started setup process. No general-purpose shell UI,
 * no remote host selection, no stored ADB key, no capture or chat access.
 * ADB runs one fixed development-permission grant for this test package.
 */
public final class SmartSetupService extends Service {
    private static final int ID=7181, RESULT=7182;
    private static final String CHANNEL="cherry_smart_setup";
    private static SmartSetupService active;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String token;
    private boolean running,done;
    private final Runnable timeout=()->finish(false,"Setup အချိန်ပြည့်ပါပြီ။ ပြန်စမ်းနိုင်ပါသည်။");
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(done)return START_NOT_STICKY;
        if(active==this)return START_NOT_STICKY;
        active=this;token=UUID.randomUUID().toString();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Cherry Smart Setup",NotificationManager.IMPORTANCE_HIGH));
        nm.cancel(RESULT);
        startForeground(ID,notification("Wireless debugging မှ Pairing code ၆ လုံးကို ထည့်ပါ။",true));
        main.postDelayed(timeout,120000);
        if(Build.VERSION.SDK_INT<30||CapturePermission.special(this))
            finish(CapturePermission.special(this),"အထူး Permission အခြေအနေ စစ်ဆေးပြီးပါပြီ။");
        return START_NOT_STICKY;
    }
    private PendingIntent action(String name,boolean reply){
        Intent i=new Intent(this,SmartSetupReply.class).setAction(name).putExtra("token",token);
        int flags=PendingIntent.FLAG_CANCEL_CURRENT|(reply&&Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:reply?0:PendingIntent.FLAG_IMMUTABLE);
        return PendingIntent.getBroadcast(this,reply?7183:7184,i,flags);
    }
    private PendingIntent home(){
        return PendingIntent.getActivity(this,7185,new Intent(this,BatchActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification(String message,boolean input){
        Notification.Builder b=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("Cherry Smart Setup")
            .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(home()).setOngoing(true);
        if(input){
            RemoteInput r=new RemoteInput.Builder("pair_code").setLabel("Pairing code ၆ လုံး").build();
            b.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_send,"Code ထည့်ရန်",action("pair",true)).addRemoteInput(r).build());
        }
        b.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"ပယ်ဖျက်ရန်",action("cancel",false)).build());
        return b.build();
    }
    static void cancel(String token){
        SmartSetupService s=active;
        if(s!=null&&s.token.equals(token))s.finish(false,"Setup ပယ်ဖျက်ပြီးပါပြီ။");
    }
    static void submit(String token,String raw){
        SmartSetupService s=active;
        if(s==null||s.done||s.running||!s.token.equals(token))return;
        String code=raw.trim();
        if(!code.matches("[0-9]{6}")){
            s.getSystemService(NotificationManager.class).notify(ID,s.notification("Android မှ ပြသော code ၆ လုံးသာ ထည့်ပါ။",true));return;
        }
        s.running=true;
        s.getSystemService(NotificationManager.class).notify(ID,s.notification("ဖုန်းအတွင်း ချိတ်ဆက်နေပါသည်…",false));
        s.main.removeCallbacks(s.timeout);s.main.postDelayed(s.timeout,35000);
        s.worker.execute(()->s.pairAndGrant(code));
    }
    private int discover(String type) throws Exception{
        CountDownLatch latch=new CountDownLatch(1);
        AtomicInteger port=new AtomicInteger(-1);
        AdbMdns mdns=new AdbMdns(getApplicationContext(),type,(address,p)->{
            if(address!=null&&p>0&&p<=65535&&port.compareAndSet(-1,p))latch.countDown();
        });
        mdns.start();
        try{if(!latch.await(8000,TimeUnit.MILLISECONDS))throw new java.io.IOException("Discovery timeout");return port.get();}
        finally{mdns.stop();}
    }
    private void pairAndGrant(String code){
        boolean ok=false;
        try(LocalConnection adb=new LocalConnection()){
            int pairing=discover(AdbMdns.SERVICE_TYPE_TLS_PAIRING);
            if(!adb.pair("127.0.0.1",pairing,code))throw new java.io.IOException("Pair failed");
            int connect=discover(AdbMdns.SERVICE_TYPE_TLS_CONNECT);
            if(!adb.connect("127.0.0.1",connect))throw new java.io.IOException("Connect failed");
            if(!"com.myanmar.cherrydirectcapture".equals(getPackageName()))throw new SecurityException("Unexpected package");
            // Explicitly authorized local ADB setup; no user supplied shell commands or arguments.
            try(AdbStream stream=adb.openStream("shell:pm grant com.myanmar.cherrydirectcapture android.permission.WRITE_SECURE_SETTINGS")){
                for(int i=0;i<25&&!CapturePermission.special(this);i++)Thread.sleep(200);
                ok=CapturePermission.special(this);
            }
        }catch(Exception|LinkageError e){ok=CapturePermission.special(this);}
        final boolean granted=ok;
        main.post(()->finish(granted,granted?"အထူး Permission ရရှိပြီ။ Wireless debugging ပိတ်ပြီး Cherry pairing ကို Forget လုပ်ပါ။":"Setup မအောင်မြင်ပါ။ Wi-Fi၊ Wireless debugging နှင့် pairing code ပြန်စစ်ပါ။"));
    }
    private void finish(boolean success,String message){
        if(done)return;done=true;active=null;main.removeCallbacksAndMessages(null);worker.shutdownNow();
        NotificationManager nm=getSystemService(NotificationManager.class);
        stopForeground(STOP_FOREGROUND_REMOVE);
        nm.notify(RESULT,new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(success?"Cherry Setup အောင်မြင်ပါပြီ":"Cherry Setup").setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message)).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(home()).setAutoCancel(true).build());
        stopSelf();
    }
    @Override public void onDestroy(){
        active=null;done=true;main.removeCallbacksAndMessages(null);worker.shutdownNow();
        super.onDestroy();
        // This dedicated, non-sticky setup process owns no ledger/capture state.
        // Termination closes any stalled upstream socket and drops the ephemeral key.
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    private static final class LocalConnection extends AbsAdbConnectionManager {
        private final PrivateKey key;
        private final Certificate cert;
        LocalConnection() throws Exception{
            setApi(Build.VERSION.SDK_INT);setTimeout(8,TimeUnit.SECONDS);setThrowOnUnauthorised(true);
            KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");
            g.initialize(2048,new SecureRandom());java.security.KeyPair pair=g.generateKeyPair();
            key=pair.getPrivate();
            X500Name name=new X500Name("CN=Cherry Smart Setup");
            long now=System.currentTimeMillis();
            cert=new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(name,new java.math.BigInteger(128,new SecureRandom()).add(java.math.BigInteger.ONE),
                    new Date(now-60000),new Date(now+86400000),name,pair.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(key)));
        }
        @Override protected PrivateKey getPrivateKey(){return key;}
        @Override protected Certificate getCertificate(){return cert;}
        @Override protected String getDeviceName(){return "Cherry Smart Setup";}
    }
}
