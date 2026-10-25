package dk.quietlapse.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
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

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 32, 28, 24);
        root.setBackgroundColor(Color.rgb(248,248,248));

        TextView title = new TextView(this);
        title.setText("QuietLapse");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView help = new TextView(this);
        help.setText("Timelapse – første testversion\nVælg interval mellem billederne:");
        help.setTextSize(17);
        help.setPadding(0, 22, 0, 12);
        root.addView(help);

        interval = new EditText(this);
        interval.setSingleLine(true);
        interval.setInputType(2);
        interval.setText("60");
        interval.setHint("Interval");
        root.addView(interval);

        unit = new Spinner(this);
        unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"sekunder", "minutter"}));
        unit.setSelection(1);
        root.addView(unit);

        TextView fps = new TextView(this);
        fps.setText("Video: 15 billeder pr. sekund (MP4)");
        fps.setPadding(0, 18, 0, 18);
        root.addView(fps);

        start = new Button(this); start.setText("Start timelapse"); root.addView(start);
        stop = new Button(this); stop.setText("Stop og lav video"); root.addView(stop);
        status = new TextView(this); status.setText("Klar"); status.setTextSize(16); status.setPadding(0,20,0,0); root.addView(status);
        setContentView(root);

        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
                status.setText("Giv kamera- og lageradgang, og tryk Start igen.");
                return;
            }
            int n;
            try { n = Integer.parseInt(interval.getText().toString().trim()); }
            catch (Exception e) { status.setText("Indtast et gyldigt interval."); return; }
            if (n < 10) { status.setText("Brug mindst 10 sekunder mellem billeder."); return; }
            long ms = n * (unit.getSelectedItemPosition()==1 ? 60000L : 1000L);
            Intent i = new Intent(this, CaptureService.class);
            i.setAction(CaptureService.ACTION_START);
            i.putExtra("interval_ms", ms);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            status.setText("Optagelse startet. Lås gerne skærmen for at teste.");
        });
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, CaptureService.class);
            i.setAction(CaptureService.ACTION_STOP);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            status.setText("Stop sendt. Videoen laves, når optagelsen afsluttes.");
        });
    }
}
