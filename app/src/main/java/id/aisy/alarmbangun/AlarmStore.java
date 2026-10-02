package id.aisy.alarmbangun;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AlarmStore {
    private static final String PREF = "alarms";
    private static final String KEY = "list";

    public static List<Alarm> load(Context c) {
        List<Alarm> r = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                r.add(Alarm.fromJson(o));
            }
        } catch (Exception e) {
            // data rusak: abaikan
        }
        return r;
    }

    public static void save(Context c, List<Alarm> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : list) arr.put(a.toJson());
        } catch (Exception e) {
            return;
        }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).commit();
    }

    /** id 0 = alarm tes (tidak disimpan). */
    public static Alarm find(Context c, int id) {
        if (id == 0) return testAlarm(c);
        for (Alarm a : load(c)) if (a.id == id) return a;
        return null;
    }

    public static void upsert(Context c, Alarm a) {
        List<Alarm> l = load(c);
        if (a.id == 0) {
            int max = 0;
            for (Alarm x : l) if (x.id > max) max = x.id;
            a.id = max + 1;
            l.add(a);
        } else {
            boolean found = false;
            for (int i = 0; i < l.size(); i++) {
                if (l.get(i).id == a.id) {
                    l.set(i, a);
                    found = true;
                    break;
                }
            }
            if (!found) l.add(a);
        }
        save(c, l);
    }

    public static void remove(Context c, int id) {
        List<Alarm> l = load(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id == id) l.remove(i);
        save(c, l);
    }

    public static List<String> sounds(Context c) {
        List<String> r = new ArrayList<>();
        try {
            String[] f = c.getAssets().list("sounds");
            if (f != null) {
                for (String s : f) if (s.toLowerCase(Locale.ROOT).endsWith(".mp3")) r.add(s);
            }
        } catch (IOException e) {
            // tidak ada folder sounds
        }
        Collections.sort(r, String.CASE_INSENSITIVE_ORDER);
        return r;
    }

    public static String nice(String file) {
        return file.toLowerCase(Locale.ROOT).endsWith(".mp3") ? file.substring(0, file.length() - 4) : file;
    }

    private static Alarm testAlarm(Context c) {
        Alarm a = new Alarm();
        a.id = 0;
        a.label = "Tes alarm";
        a.snooze = 1;
        a.ramp = false;
        a.forceMax = false;
        a.vibrate = true;
        List<String> s = sounds(c);
        a.sound = s.isEmpty() ? "" : s.get(0);
        return a;
    }
}
