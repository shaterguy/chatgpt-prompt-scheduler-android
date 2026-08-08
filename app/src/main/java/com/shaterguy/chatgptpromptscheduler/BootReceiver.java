package com.shaterguy.chatgptpromptscheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmEngine.rebuildAll(context);
        if (Build.VERSION.SDK_INT >= 24 && !context.getSystemService(UserManager.class).isUserUnlocked()) return;
        OrchestrationStore store = new OrchestrationStore(context);
        if (store.active() && !store.paused() && store.runtimeConfigError().isEmpty()) {
            Intent service = new Intent(context, OrchestrationService.class).setAction(OrchestrationService.ACTION_RUN);
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
            } catch (RuntimeException error) {
                store.pause("기기 재시작 후 중계 서비스 복구 실패: " + error.getMessage());
            }
        }
    }
}
