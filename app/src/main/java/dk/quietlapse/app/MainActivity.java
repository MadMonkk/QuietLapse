package dk.quietlapse.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.graphics.Color;

public class MainActivity extends Activity {
    EditText interval;
    Spinner unit;
    TextView status;
    Button start, stop;
    private boolean recording = false;
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            recording = intent.getBooleanExtra("running", false);
            if (message != null) status.setText(message);
            start.setEnabled(!recording);
            stop.setEnabled(recording);
            if (message != null && (message.startsWith("FÆRDIG") || message.startsWith("VIDEOFEJL") || message.startsWith("FEJL"))) {
                start.setEnabled(true); stop.setEnabled(false);
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 32, 28, 24);
        root.setBackgroundColor(Color.rgb(248,248,248));

        TextView title = new TextView(this);
        title.setText("QuietLapse"); title.setTextSize(28); title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView help = new TextView(this);
        help.setText("TIMELAPSE\nVælg tiden mellem billederne. Hold telefonen stabil, og lad appen være åben eller køre i baggrunden.");
        help.setTextSize(16); help.setPadding(0, 18, 0, 12); root.addView(help);

        TextView intervalLabel = new TextView(this); intervalLabel.setText("Interval mellem billeder:"); intervalLabel.setTextSize(16); root.addView(intervalLabel);
        interval = new EditText(this); interval.setSingleLine(true); interval.setInputType(2); interval.setText("60"); interval.setHint("Skriv et tal, fx 10"); root.addView(interval);
        unit = new Spinner(this);
        unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"sekunder", "minutter"}));
        unit.setSelection(1); root.addView(unit);

        start = new Button(this); start.setText("▶  START OPTAGELSE"); root.addView(start);
        stop = new Button(this); stop.setText("■  STOP OG LAV VIDEO"); stop.setEnabled(false); root.addView(stop);

        TextView statusTitle = new TextView(this); statusTitle.setText("STATUS"); statusTitle.setTextSize(14); statusTitle.setTextColor(Color.DKGRAY); statusTitle.setPadding(0, 20, 0, 4); root.addView(statusTitle);
        status = new TextView(this); status.setText("Klar til optagelse.\nBilleder og video gemmes i Pictures/QuietLapse."); status.setTextSize(16); status.setTextColor(Color.BLACK); status.setPadding(14,14,14,14); status.setBackgroundColor(Color.rgb(230,238,245)); root.addView(status);
        setContentView(root);

        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
                status.setText("Tillad kamera og lager, og tryk derefter Start igen."); return;
            }
            int n;
            try { n = Integer.parseInt(interval.getText().toString().trim()); }
            catch (Exception e) { status.setText("FEJL: Skriv et gyldigt interval."); return; }
            if (n < 10) { status.setText("FEJL: Brug mindst 10 sekunder mellem billeder."); return; }
            long ms = n * (unit.getSelectedItemPosition()==1 ? 60000L : 1000L);
            Intent i = new Intent(this, CaptureService.class); i.setAction(CaptureService.ACTION_START); i.putExtra("interval_ms", ms);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            recording = true; start.setEnabled(false); stop.setEnabled(true);
            status.setText("STARTER KAMERA …\nVent på status om det første billede.");
        });
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, CaptureService.class); i.setAction(CaptureService.ACTION_STOP);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            stop.setEnabled(false); status.setText("STOPPER …\nVideoen bliver lavet. Vent, til der står FÆRDIG.");
        });
    }
    @Override protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(CaptureService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(statusReceiver, f);
    }
    @Override protected void onPause() { try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {} super.onPause(); }
}
