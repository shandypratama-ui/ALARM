package id.aisy.alarmbangun;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

public class RingService extends Service {
    static final String CH = "alarm_ring";
    static final String ACT_STOP = "id.aisy.alarmbangun.STOP";
    static final String ACT_SNOOZE = "id.aisy.alarmbangun.SNOOZE";
    static final String ACT_STOPPED = "id.aisy.alarmbangun.STOPPED";
    static final int NID = 4242;
    static final long AUTO_STOP_MS = 10 * 60 * 1000L;

    private MediaPlayer mp;
    private Vibrator vib;
    private AudioManager am;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wl;
    private Alarm alarm;
    private long startAt;
    private int oldVol = -1;
    private boolean fellBack = false;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final AudioAttributes alarmAttrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    private final Runnable rampTick = new Runnable() {
        @Override
        public void run() {
            if (mp == null || alarm == null || !alarm.ramp) return;
            float k = Math.min(1f, (System.currentTimeMillis() - startAt) / 30000f);
            float v = 0.15f + 0.85f * k;
            try {
                mp.setVolume(v, v);
            } catch (Exception e) {
                // abaikan
            }
            if (k < 1f) h.postDelayed(this, 1000);
        }
    };

    private final Runnable autoStop = new Runnable() {
        @Override
        public void run() {
            finishRing(false);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent != null ? intent.getAction() : null;
        if (ACT_STOP.equals(act)) {
            finishRing(false);
            return START_NOT_STICKY;
        }
        if (ACT_SNOOZE.equals(act)) {
            finishRing(true);
            return START_NOT_STICKY;
        }

        int aid = intent != null ? intent.getIntExtra("id", -99) : -99;
        Alarm a = AlarmStore.find(this, aid);

        ensureChannel();
        Notification n = buildNotification(a);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NID, n);
        }

        if (a == null) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        releaseAll();
        alarm = a;
        startAt = System.currentTimeMillis();
        fellBack = false;
        am = (AudioManager) getSystemService(AUDIO_SERVICE);

        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmbangun:ring");
            wl.acquire(AUTO_STOP_MS + 30000L);
        } catch (Exception e) {
            // abaikan
        }

        if (a.forceMax) setMaxVolume();
        requestFocus();
        startSound();
        if (a.vibrate) startVibrate();

        h.removeCallbacks(rampTick);
        h.post(rampTick);
        h.removeCallbacks(autoStop);
        h.postDelayed(autoStop, AUTO_STOP_MS);
        return START_NOT_STICKY;
    }

    private void ensureChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CH, "Alarm berbunyi", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(Alarm a) {
        String label = (a == null || a.label.isEmpty()) ? "Alarm" : a.label;
        int id = a == null ? 0 : a.id;
        int imm = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

        Intent full = new Intent(this, RingActivity.class)
                .putExtra("id", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullPi = PendingIntent.getActivity(this, 1, full, imm);
        PendingIntent stopPi = PendingIntent.getService(this, 2,
                new Intent(this, RingService.class).setAction(ACT_STOP), imm);
        PendingIntent snoozePi = PendingIntent.getService(this, 3,
                new Intent(this, RingService.class).setAction(ACT_SNOOZE), imm);

        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(label)
                .setContentText("Waktunya bangun!")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true);
        if (a != null && a.snooze > 0) b.addAction(0, "Tunda " + a.snooze + " mnt", snoozePi);
        b.addAction(0, "Matikan", stopPi);
        return b.build();
    }

    private void setMaxVolume() {
        try {
            oldVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (Exception e) {
            oldVol = -1;
        }
    }

    private void requestFocus() {
        try {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(alarmAttrs).build();
            am.requestAudioFocus(focus);
        } catch (Exception e) {
            focus = null;
        }
    }

    private void startSound() {
        boolean ok = false;
        if (!alarm.sound.isEmpty()) ok = prepare(alarm.sound);
        if (!ok) prepare(null);
    }

    private boolean prepare(String asset) {
        MediaPlayer m = new MediaPlayer();
        try {
            m.setAudioAttributes(alarmAttrs);
            if (asset != null) {
                AssetFileDescriptor afd = getAssets().openFd("sounds/" + asset);
                m.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } else {
                Uri u = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
                if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                m.setDataSource(this, u);
            }
            m.setLooping(true);
            float v = alarm.ramp ? 0.15f : 1f;
            m.setVolume(v, v);
            m.setOnErrorListener((p, what, extra) -> {
                if (!fellBack && p == mp) {
                    fellBack = true;
                    try {
                        mp.release();
                    } catch (Exception e) {
                        // abaikan
                    }
                    mp = null;
                    prepare(null);
                }
                return true;
            });
            m.prepare();
            m.start();
            mp = m;
            return true;
        } catch (Exception e) {
            try {
                m.release();
            } catch (Exception x) {
                // abaikan
            }
            return false;
        }
    }

    private void startVibrate() {
        try {
            vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) {
                vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 500}, 0), alarmAttrs);
            }
        } catch (Exception e) {
            // abaikan
        }
    }

    private void releaseAll() {
        h.removeCallbacks(rampTick);
        h.removeCallbacks(autoStop);
        if (mp != null) {
            try {
                mp.stop();
            } catch (Exception e) {
                // abaikan
            }
            try {
                mp.release();
            } catch (Exception e) {
                // abaikan
            }
            mp = null;
        }
        if (vib != null) {
            try {
                vib.cancel();
            } catch (Exception e) {
                // abaikan
            }
        }
        if (oldVol >= 0 && am != null) {
            try {
                am.setStreamVolume(AudioManager.STREAM_ALARM, oldVol, 0);
            } catch (Exception e) {
                // abaikan
            }
            oldVol = -1;
        }
        if (focus != null && am != null) {
            try {
                am.abandonAudioFocusRequest(focus);
            } catch (Exception e) {
                // abaikan
            }
            focus = null;
        }
        if (wl != null && wl.isHeld()) {
            try {
                wl.release();
            } catch (Exception e) {
                // abaikan
            }
        }
        wl = null;
    }

    private void finishRing(boolean snooze) {
        Alarm a = alarm;
        releaseAll();
        alarm = null;
        if (snooze && a != null && a.snooze > 0) {
            AlarmScheduler.scheduleSnooze(this, a, a.snooze);
            Toast.makeText(this, "Ditunda " + a.snooze + " menit", Toast.LENGTH_LONG).show();
        }
        sendBroadcast(new Intent(ACT_STOPPED).setPackage(getPackageName()));
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        releaseAll();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
