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
            OrchestrationRunLog runLog = new OrchestrationRunLog(context);
            runLog.record(store, "REBOOT_RECOVERY_START", "source=boot");
            Intent service = new Intent(context, OrchestrationService.class).setAction(OrchestrationService.ACTION_RUN);
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
                runLog.record(store, "REBOOT_RECOVERY_STARTED", "source=boot");
            } catch (RuntimeException error) {
                store.fail("BOOT_RECOVERY_FAILED", "기기 재시작 후 오토런 중계 서비스를 복구하지 못했습니다.");
                runLog.record(store, "REBOOT_RECOVERY_FAILED", "source=boot");
                if (NotificationHelper.orchestrationAlertsEnabled(context)) {
                    NotificationHelper.orchestrationError(context, store.monitoringSide(), store.runJobId(),
                            store.currentStep(), store.currentRound(), "기기 재시작 후 중계 서비스를 복구하지 못했습니다.");
                }
            }
        }
    }
}
