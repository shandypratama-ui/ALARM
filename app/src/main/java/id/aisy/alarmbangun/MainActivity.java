package id.aisy.alarmbangun;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert;
    private static final int[] SNOOZE_VALUES = {0, 1, 3, 5, 10, 15};
    private static final String[] SNOOZE_LABELS = {"Tanpa snooze", "1 menit", "3 menit", "5 menit", "10 menit", "15 menit"};

    private LinearLayout list;
    private TextView next;
    private MediaPlayer preview;
    private boolean askedExact = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        list = findViewById(R.id.list);
        next = findViewById(R.id.next);
        findViewById(R.id.btnAdd).setOnClickListener(v -> showEdit(null));
        findViewById(R.id.btnPerm).setOnClickListener(v -> showPerms());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        checkExactAlarm();
    }

    @Override
    protected void onPause() {
        stopPreview();
        super.onPause();
    }

    private void checkExactAlarm() {
        if (Build.VERSION.SDK_INT < 31 || askedExact) return;
        AlarmManager am = getSystemService(AlarmManager.class);
        if (!am.canScheduleExactAlarms()) {
            askedExact = true;
            new AlertDialog.Builder(this, DIALOG_THEME)
                    .setTitle("Izin alarm diperlukan")
                    .setMessage("Supaya alarm bunyi tepat waktu, izinkan \"Alarm & pengingat\" untuk aplikasi ini.")
                    .setPositiveButton("Buka pengaturan", (d, w) ->
                            openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true))
                    .setNegativeButton("Nanti", null)
                    .show();
        }
    }

    // ---------------------------------------------------------------- daftar alarm

    private void refresh() {
        list.removeAllViews();
        List<Alarm> l = AlarmStore.load(this);
        Collections.sort(l, (p, q) -> (p.hour * 60 + p.minute) - (q.hour * 60 + q.minute));

        if (l.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Belum ada alarm.\nKetuk \"+ Tambah alarm\" di bawah.");
            t.setTextColor(0xFF9AA4CF);
            t.setGravity(android.view.Gravity.CENTER);
            t.setPadding(0, 80, 0, 0);
            list.addView(t);
        }

        for (final Alarm a : l) {
            final View row = getLayoutInflater().inflate(R.layout.item_alarm, list, false);
            ((TextView) row.findViewById(R.id.time)).setText(a.timeText());
            ((TextView) row.findViewById(R.id.label)).setText((a.label.isEmpty() ? "Alarm" : a.label) + " · " + a.daysText());
            ((TextView) row.findViewById(R.id.sound)).setText("♪ " + (a.sound.isEmpty() ? "Nada bawaan HP" : AlarmStore.nice(a.sound)));
            Switch sw = row.findViewById(R.id.sw);
            sw.setChecked(a.enabled);
            row.setAlpha(a.enabled ? 1f : 0.5f);
            sw.setOnCheckedChangeListener((btn, on) -> {
                a.enabled = on;
                AlarmStore.upsert(this, a);
                AlarmScheduler.schedule(this, a);
                row.setAlpha(on ? 1f : 0.5f);
                updateNext();
                if (on) toast("Alarm berbunyi dalam " + until(a.nextTrigger(System.currentTimeMillis()) - System.currentTimeMillis()));
            });
            row.setOnClickListener(v -> showEdit(a));
            list.addView(row);
        }
        updateNext();
    }

    private void updateNext() {
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        for (Alarm a : AlarmStore.load(this)) {
            if (!a.enabled) continue;
            long t = a.nextTrigger(now);
            if (t < best) best = t;
        }
        next.setText(best == Long.MAX_VALUE ? "Belum ada alarm aktif" : "Alarm berbunyi dalam " + until(best - now));
    }

    static String until(long ms) {
        long mins = Math.max(1, (ms + 59999) / 60000);
        long d = mins / 1440, h = (mins % 1440) / 60, m = mins % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(" hari ");
        if (h > 0) sb.append(h).append(" jam ");
        if (m > 0 && d == 0) sb.append(m).append(" menit");
        String s = sb.toString().trim();
        return s.isEmpty() ? "kurang dari 1 menit" : s;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ---------------------------------------------------------------- editor alarm

    private void showEdit(final Alarm existing) {
        final boolean isNew = existing == null;
        final Alarm a = isNew ? new Alarm() : existing;

        final View root = getLayoutInflater().inflate(R.layout.dialog_edit, null);
        final TimePicker tp = root.findViewById(R.id.tp);
        tp.setIs24HourView(true);
        tp.setHour(a.hour);
        tp.setMinute(a.minute);

        final EditText et = root.findViewById(R.id.label);
        et.setText(a.label);

        LinearLayout dl = root.findViewById(R.id.days);
        final ToggleButton[] tb = new ToggleButton[7];
        final int[] bits = {1, 2, 3, 4, 5, 6, 0};
        String[] nm = {"Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"};
        for (int i = 0; i < 7; i++) {
            ToggleButton t = new ToggleButton(this);
            t.setTextOn(nm[i]);
            t.setTextOff(nm[i]);
            t.setTextSize(11);
            t.setMinWidth(0);
            t.setMinimumWidth(0);
            t.setPadding(0, 0, 0, 0);
            t.setChecked((a.days & (1 << bits[i])) != 0);
            dl.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tb[i] = t;
        }

        final List<String> files = AlarmStore.sounds(this);
        List<String> names = new ArrayList<>();
        names.add("Nada alarm bawaan HP");
        for (String f : files) names.add(AlarmStore.nice(f));
        final Spinner sp = root.findViewById(R.id.sound);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        int idx = files.indexOf(a.sound);
        sp.setSelection(idx >= 0 ? idx + 1 : (isNew && !files.isEmpty() ? 1 : 0));
        if (!files.isEmpty()) root.findViewById(R.id.noSoundHint).setVisibility(View.GONE);

        final Button bp = root.findViewById(R.id.btnPreview);
        bp.setOnClickListener(x -> {
            if (preview != null) {
                stopPreview();
                bp.setText("▶ Coba suara");
                return;
            }
            int p = sp.getSelectedItemPosition();
            startPreview(p == 0 ? "" : files.get(p - 1));
            bp.setText(preview != null ? "■ Stop" : "▶ Coba suara");
        });

        final Spinner ss = root.findViewById(R.id.snooze);
        ss.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SNOOZE_LABELS));
        int si = 3;
        for (int i = 0; i < SNOOZE_VALUES.length; i++) if (SNOOZE_VALUES[i] == a.snooze) si = i;
        ss.setSelection(si);

        final CheckBox cv = root.findViewById(R.id.cbVibrate);
        final CheckBox cr = root.findViewById(R.id.cbRamp);
        final CheckBox cm = root.findViewById(R.id.cbMax);
        cv.setChecked(a.vibrate);
        cr.setChecked(a.ramp);
        cm.setChecked(a.forceMax);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(isNew ? "Alarm baru" : "Ubah alarm")
                .setView(root)
                .setPositiveButton("Simpan", (d, w) -> {
                    a.hour = tp.getHour();
                    a.minute = tp.getMinute();
                    a.label = et.getText().toString().trim();
                    int mask = 0;
                    for (int k = 0; k < 7; k++) if (tb[k].isChecked()) mask |= (1 << bits[k]);
                    a.days = mask;
                    int p = sp.getSelectedItemPosition();
                    a.sound = p == 0 ? "" : files.get(p - 1);
                    a.snooze = SNOOZE_VALUES[ss.getSelectedItemPosition()];
                    a.vibrate = cv.isChecked();
                    a.ramp = cr.isChecked();
                    a.forceMax = cm.isChecked();
                    a.enabled = true;
                    AlarmStore.upsert(this, a);
                    AlarmScheduler.schedule(this, a);
                    refresh();
                    toast("Alarm berbunyi dalam " + until(a.nextTrigger(System.currentTimeMillis()) - System.currentTimeMillis()));
                })
                .setNegativeButton("Batal", null);
        if (!isNew) {
            builder.setNeutralButton("Hapus", (d, w) -> {
                AlarmScheduler.cancel(this, a);
                AlarmStore.remove(this, a.id);
                refresh();
            });
        }
        AlertDialog dlg = builder.create();
        dlg.setOnDismissListener(d -> stopPreview());
        dlg.show();
    }

    private void startPreview(String sound) {
        stopPreview();
        MediaPlayer m = new MediaPlayer();
        try {
            m.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            if (!sound.isEmpty()) {
                AssetFileDescriptor afd = getAssets().openFd("sounds/" + sound);
                m.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } else {
                m.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            }
            m.setLooping(true);
            m.prepare();
            m.start();
            preview = m;
        } catch (Exception e) {
            try {
                m.release();
            } catch (Exception x) {
                // abaikan
            }
            toast("Gagal memutar suara");
        }
    }

    private void stopPreview() {
        if (preview != null) {
            try {
                preview.stop();
            } catch (Exception e) {
                // abaikan
            }
            try {
                preview.release();
            } catch (Exception e) {
                // abaikan
            }
            preview = null;
        }
    }

    // ---------------------------------------------------------------- izin & tes

    private static String st(boolean ok) {
        return ok ? "[OK]    " : "[BELUM] ";
    }

    private void showPerms() {
        AlarmManager am = getSystemService(AlarmManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        PowerManager pm = getSystemService(PowerManager.class);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        boolean notif = nm.areNotificationsEnabled();
        boolean full = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent();
        boolean batt = pm.isIgnoringBatteryOptimizations(getPackageName());

        String[] items = {
                st(exact) + "Izin alarm tepat waktu",
                st(notif) + "Notifikasi",
                st(full) + "Notifikasi layar penuh (tampil di lock screen)",
                st(batt) + "Bebas hemat baterai",
                "Autostart / jalan di latar belakang (Xiaomi, Oppo, Vivo, dll)",
                "TES ALARM 10 detik lagi (kunci HP setelah menekan ini)"
        };
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Izin & tes")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            if (Build.VERSION.SDK_INT >= 31) openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true);
                            else toast("Sudah aman di versi Android ini");
                            break;
                        case 1:
                            if (Build.VERSION.SDK_INT >= 33
                                    && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
                            } else {
                                try {
                                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                                } catch (Exception e) {
                                    appDetails();
                                }
                            }
                            break;
                        case 2:
                            if (Build.VERSION.SDK_INT >= 34) openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, true);
                            else toast("Sudah aman di versi Android ini");
                            break;
                        case 3:
                            openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true);
                            break;
                        case 4:
                            openAutostart();
                            break;
                        case 5:
                            AlarmScheduler.scheduleTest(this, 10);
                            toast("Kunci HP sekarang. Alarm tes bunyi 10 detik lagi.");
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void openSettings(String action, boolean withPackage) {
        try {
            Intent i = new Intent(action);
            if (withPackage) i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            appDetails();
        }
    }

    private void appDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast("Buka Pengaturan > Aplikasi > Alarm Bangun");
        }
    }

    private void openAutostart() {
        String[][] tries = {
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"}
        };
        for (String[] t : tries) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(t[0], t[1]));
                startActivity(i);
                return;
            } catch (Exception e) {
                // coba berikutnya
            }
        }
        appDetails();
        toast("Cari menu Baterai / Autostart, lalu izinkan jalan di latar belakang");
    }
}
