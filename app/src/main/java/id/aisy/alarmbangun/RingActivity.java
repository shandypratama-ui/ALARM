package id.aisy.alarmbangun;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class RingActivity extends Activity {
    private final BroadcastReceiver stopped = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ring);

        Alarm a = AlarmStore.find(this, getIntent().getIntExtra("id", -99));
        TextView label = findViewById(R.id.rlabel);
        label.setText(a == null || a.label.isEmpty() ? "Alarm" : a.label);

        Button snooze = findViewById(R.id.rsnooze);
        if (a == null || a.snooze <= 0) {
            snooze.setVisibility(View.GONE);
        } else {
            snooze.setText("Tunda " + a.snooze + " mnt");
        }
        snooze.setOnClickListener(v -> {
            startService(new Intent(this, RingService.class).setAction(RingService.ACT_SNOOZE));
            finish();
        });
        findViewById(R.id.rstop).setOnClickListener(v -> {
            startService(new Intent(this, RingService.class).setAction(RingService.ACT_STOP));
            finish();
        });

        IntentFilter f = new IntentFilter(RingService.ACT_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stopped, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopped, f);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(stopped);
        } catch (Exception e) {
            // abaikan
        }
        super.onDestroy();
    }
}
