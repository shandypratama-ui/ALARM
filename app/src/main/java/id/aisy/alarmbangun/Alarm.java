package id.aisy.alarmbangun;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

public class Alarm {
    public int id = 0, hour = 6, minute = 0, days = 0, snooze = 5;
    public String label = "", sound = "";
    public boolean vibrate = true, ramp = true, forceMax = true, enabled = true;

    private static final String[] NAMES = {"Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"};
    private static final int[] ORDER = {1, 2, 3, 4, 5, 6, 0};

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("hour", hour);
        o.put("minute", minute);
        o.put("days", days);
        o.put("snooze", snooze);
        o.put("label", label);
        o.put("sound", sound);
        o.put("vibrate", vibrate);
        o.put("ramp", ramp);
        o.put("forceMax", forceMax);
        o.put("enabled", enabled);
        return o;
    }

    public static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optInt("id", 0);
        a.hour = o.optInt("hour", 6);
        a.minute = o.optInt("minute", 0);
        a.days = o.optInt("days", 0);
        a.snooze = o.optInt("snooze", 5);
        a.label = o.optString("label", "");
        a.sound = o.optString("sound", "");
        a.vibrate = o.optBoolean("vibrate", true);
        a.ramp = o.optBoolean("ramp", true);
        a.forceMax = o.optBoolean("forceMax", true);
        a.enabled = o.optBoolean("enabled", true);
        return a;
    }

    public String timeText() {
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    public String daysText() {
        if (days == 0) return "Sekali";
        if (days == 127) return "Setiap hari";
        if (days == 62) return "Senin – Jumat";
        if (days == 65) return "Akhir pekan";
        StringBuilder sb = new StringBuilder();
        for (int d : ORDER) {
            if ((days & (1 << d)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(NAMES[d]);
            }
        }
        return sb.toString();
    }

    /** Waktu bunyi berikutnya (epoch ms) setelah waktu 'from'. */
    public long nextTrigger(long from) {
        for (int i = 0; i < 8; i++) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(from);
            c.add(Calendar.DAY_OF_YEAR, i);
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            boolean dayOk = days == 0 || (days & (1 << (c.get(Calendar.DAY_OF_WEEK) - 1))) != 0;
            if (c.getTimeInMillis() > from && dayOk) return c.getTimeInMillis();
        }
        return from + 24L * 60 * 60 * 1000;
    }
}
