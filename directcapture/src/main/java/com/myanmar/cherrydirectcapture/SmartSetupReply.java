package com.myanmar.cherrydirectcapture;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Only explicit, private notification actions for a live user-started setup. */
public final class SmartSetupReply extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null)return;
        String token=intent.getStringExtra("token");
        if("cancel".equals(intent.getAction())){SmartSetupService.cancel(token);return;}
        if(!"pair".equals(intent.getAction()))return;
        Bundle values=RemoteInput.getResultsFromIntent(intent);
        CharSequence code=values==null?null:values.getCharSequence("pair_code");
        SmartSetupService.submit(token,code==null?"":code.toString());
    }
}
