package com.shaterguy.chatgptpromptscheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String scheduleId = intent.getStringExtra("scheduleId");
        if (scheduleId == null || scheduleId.isBlank()) return;

        QueueStore queueStore = new QueueStore(context);
        QueueStore.EnqueueResult result;
        try {
            result = queueStore.enqueue(scheduleId, false);
        } catch (RuntimeException error) {
            return;
        }
        if (!result.added) return;

        Intent service = new Intent(context, ExecutionService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
        } catch (RuntimeException error) {
            queueStore.finish(result.runId);
        }
    }
}
