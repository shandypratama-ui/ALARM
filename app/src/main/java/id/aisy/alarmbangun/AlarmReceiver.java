package id.aisy.alarmbangun;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra("id", -99);
        boolean snooze = intent.getBooleanExtra("snooze", false);
        Alarm a = AlarmStore.find(c, id);
        if (a == null) return;
        if (!snooze && id != 0) {
            if (!a.enabled) return;
            if (a.days == 0) {
                a.enabled = false;            // alarm sekali: matikan setelah bunyi
                AlarmStore.upsert(c, a);
            } else {
                AlarmScheduler.schedule(c, a); // alarm berulang: jadwalkan berikutnya
            }
        }
        c.startForegroundService(new Intent(c, RingService.class).putExtra("id", id));
    }
}
