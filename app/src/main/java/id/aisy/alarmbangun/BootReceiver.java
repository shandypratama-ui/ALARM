package id.aisy.alarmbangun;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        for (Alarm a : AlarmStore.load(c)) {
            if (a.enabled) AlarmScheduler.schedule(c, a);
        }
    }
}
