package id.aisy.alarmbangun;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class AlarmScheduler {
    static final String ACTION_FIRE = "id.aisy.alarmbangun.FIRE";

    private static PendingIntent fire(Context c, int id, boolean snooze) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra("id", id)
                .putExtra("snooze", snooze);
        int req = snooze ? 1000000 + id : id;
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent show(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void setAt(Context c, long t, PendingIntent op) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        try {
            // setAlarmClock = jenis alarm yang sama dengan aplikasi Jam bawaan Android
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show(c)), op);
        } catch (SecurityException e) {
            // izin alarm belum diberikan; MainActivity akan memintanya
        }
    }

    public static void schedule(Context c, Alarm a) {
        if (!a.enabled) {
            cancel(c, a);
            return;
        }
        setAt(c, a.nextTrigger(System.currentTimeMillis()), fire(c, a.id, false));
    }

    public static void cancel(Context c, Alarm a) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(fire(c, a.id, false));
        am.cancel(fire(c, a.id, true));
    }

    public static void scheduleSnooze(Context c, Alarm a, int minutes) {
        setAt(c, System.currentTimeMillis() + minutes * 60000L, fire(c, a.id, true));
    }

    public static void scheduleTest(Context c, int seconds) {
        setAt(c, System.currentTimeMillis() + seconds * 1000L, fire(c, 0, false));
    }
}
