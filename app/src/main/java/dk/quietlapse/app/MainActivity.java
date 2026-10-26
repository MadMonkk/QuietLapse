package dk.quietlapse.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.util.List;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    EditText interval;
    Spinner unit;
    TextView status;
    Button start, stop, play;
    SurfaceView preview;
    SurfaceHolder holder;
    Camera previewCamera;
    boolean recording = false;
    String lastVideoPath;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            recording = intent.getBooleanExtra("running", false);
            if (message != null) {
                status.setText(message);
                if (message.startsWith("FÆRDIG")) {
                    int at = message.indexOf("Video gemt: ");
                    if (at >= 0) {
                        String rel = message.substring(at + 12).split("\\n")[0].trim();
                        String root = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).getAbsolutePath();
                        lastVideoPath = root + "/QuietLapse/" + rel.substring("Pictures/QuietLapse/".length());
                    }
                    play.setVisibility(View.VISIBLE);
                }
                if (message.startsWith("VIDEOFEJL") || message.startsWith("FEJL")) {
                    start.setEnabled(true); stop.setEnabled(false);
                }
            }
            start.setEnabled(!recording);
            stop.setEnabled(recording);
            if (recording) play.setVisibility(View.GONE);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 14); root.setBackgroundColor(Color.rgb(248,248,248));
        TextView title = new TextView(this); title.setText("QuietLapse"); title.setTextSize(28); title.setTextColor(Color.BLACK); root.addView(title);
        TextView help = new TextView(this); help.setText("TIMELAPSE — se motivet i kamera-previewet, før du starter."); help.setTextSize(15); help.setPadding(0,8,0,8); root.addView(help);
        preview = new SurfaceView(this); holder = preview.getHolder(); holder.addCallback(this); holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        root.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int)(getResources().getDisplayMetrics().density*210)));
        TextView pLabel = new TextView(this); pLabel.setText("LIVE KAMERAPREVIEW · kameraet frigives under optagelsen"); pLabel.setTextSize(12); pLabel.setTextColor(Color.DKGRAY); root.addView(pLabel);
        TextView lab = new TextView(this); lab.setText("Interval mellem billeder:"); lab.setTextSize(16); root.addView(lab);
        interval = new EditText(this); interval.setSingleLine(true); interval.setInputType(2); interval.setText("60"); interval.setHint("Skriv et tal, fx 10"); root.addView(interval);
        unit = new Spinner(this); unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"sekunder", "minutter"})); unit.setSelection(1); root.addView(unit);
        start = new Button(this); start.setText("▶  START OPTAGELSE"); root.addView(start);
        stop = new Button(this); stop.setText("■  STOP OG LAV VIDEO"); stop.setEnabled(false); root.addView(stop);
        play = new Button(this); play.setText("▶  AFSPIL FÆRDIG VIDEO"); play.setVisibility(View.GONE); root.addView(play);
        TextView st = new TextView(this); st.setText("STATUS"); st.setTextSize(13); st.setTextColor(Color.DKGRAY); st.setPadding(0,8,0,3); root.addView(st);
        status = new TextView(this); status.setText("Klar til optagelse.\nBilleder og video gemmes i Pictures/QuietLapse."); status.setTextSize(15); status.setTextColor(Color.BLACK); status.setPadding(12,12,12,12); status.setBackgroundColor(Color.rgb(230,238,245)); root.addView(status);
        setContentView(root);

        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100); status.setText("Tillad kamera og lager, og tryk derefter Start igen."); return;
            }
            int n; try { n = Integer.parseInt(interval.getText().toString().trim()); } catch (Exception e) { status.setText("FEJL: Skriv et gyldigt interval."); return; }
            if (n < 10) { status.setText("FEJL: Brug mindst 10 sekunder mellem billeder."); return; }
            releasePreview();
            long ms = n * (unit.getSelectedItemPosition()==1 ? 60000L : 1000L);
            Intent i = new Intent(this, CaptureService.class); i.setAction(CaptureService.ACTION_START); i.putExtra("interval_ms", ms);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            recording = true; start.setEnabled(false); stop.setEnabled(true); play.setVisibility(View.GONE);
            status.setText("STARTER KAMERA …\nPreview slukkes, mens optagelsen bruger kameraet.");
        });
        stop.setOnClickListener(v -> { Intent i = new Intent(this, CaptureService.class); i.setAction(CaptureService.ACTION_STOP); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); stop.setEnabled(false); status.setText("STOPPER …\nVideoen bliver lavet. Vent, til der står FÆRDIG."); });
        play.setOnClickListener(v -> playVideo());
    }

    private void playVideo() {
        if (lastVideoPath == null) { Toast.makeText(this, "Videoens placering er ikke kendt endnu. Se Pictures/QuietLapse.", Toast.LENGTH_LONG).show(); return; }
        File f = new File(lastVideoPath);
        if (!f.exists()) { Toast.makeText(this, "Videoen blev ikke fundet: " + lastVideoPath, Toast.LENGTH_LONG).show(); return; }
        try {
            Uri uri = Uri.fromFile(f); Intent i = new Intent(Intent.ACTION_VIEW); i.setDataAndType(uri, "video/mp4");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(i);
        } catch (Exception e) { Toast.makeText(this, "Ingen kompatibel videoafspiller fundet.", Toast.LENGTH_LONG).show(); }
    }

    private int displayOrientation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo(); Camera.getCameraInfo(id, info);
        int r = getWindowManager().getDefaultDisplay().getRotation(); int d = r==Surface.ROTATION_0?0:r==Surface.ROTATION_90?90:r==Surface.ROTATION_180?180:270;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return (360 - ((info.orientation+d)%360))%360;
        return (info.orientation-d+360)%360;
    }
    @Override public void surfaceCreated(SurfaceHolder h) { startPreview(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int he) { startPreview(); }
    @Override public void surfaceDestroyed(SurfaceHolder h) { releasePreview(); }
    private void startPreview() {
        if (recording || holder == null || !holder.getSurface().isValid()) return;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            if (previewCamera == null) previewCamera = Camera.open(0);
            previewCamera.setDisplayOrientation(displayOrientation(0)); previewCamera.setPreviewDisplay(holder);
            Camera.Parameters p = previewCamera.getParameters(); List<String> modes = p.getSupportedFocusModes();
            if (modes != null) { if (modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE); else if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); }
            previewCamera.setParameters(p); previewCamera.startPreview();
        } catch (Exception e) { status.setText("Preview kunne ikke startes. Kontrollér at kameraet ikke bruges af en anden app."); releasePreview(); }
    }
    private void releasePreview() { if (previewCamera != null) { try { previewCamera.stopPreview(); } catch (Exception ignored) {} try { previewCamera.release(); } catch (Exception ignored) {} previewCamera = null; } }
    @Override protected void onResume() { super.onResume(); recording = CaptureService.isCapturing; start.setEnabled(!recording); stop.setEnabled(recording); IntentFilter f = new IntentFilter(CaptureService.ACTION_STATUS); if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(statusReceiver, f); if (!recording) startPreview(); }
    @Override protected void onPause() { try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {} releasePreview(); super.onPause(); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) { super.onRequestPermissionsResult(requestCode, permissions, results); if (requestCode==100) status.setText("Tilladelser behandlet. Tryk Start igen."); }
}
