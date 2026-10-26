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
import android.provider.DocumentsContract;
import android.database.Cursor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    EditText interval;
    Spinner unit;
    TextView status;
    Button start, stop, play, slideshow;
    Spinner fps;
    static final int PICK_IMAGE_FOLDER = 240;
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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 14); root.setBackgroundColor(Color.rgb(248,248,248));
        TextView title = new TextView(this); title.setText("QuietLapse"); title.setTextSize(28); title.setTextColor(Color.BLACK); root.addView(title);
        TextView help = new TextView(this); help.setText("TIMELAPSE — se motivet i kamera-previewet, før du starter."); help.setTextSize(15); help.setPadding(0,8,0,8); root.addView(help);
        preview = new AspectSurfaceView(this); ((AspectSurfaceView)preview).setAspectRatio(3, 4); holder = preview.getHolder(); holder.addCallback(this); holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
        root.addView(preview, previewParams);
        TextView pLabel = new TextView(this); pLabel.setText("LIVE KAMERAPREVIEW · kameraet frigives under optagelsen"); pLabel.setTextSize(12); pLabel.setTextColor(Color.DKGRAY); root.addView(pLabel);
        TextView lab = new TextView(this); lab.setText("Interval mellem billeder:"); lab.setTextSize(16); root.addView(lab);
        interval = new EditText(this); interval.setSingleLine(true); interval.setInputType(2); interval.setText("60"); interval.setHint("Skriv et tal, fx 10"); root.addView(interval);
        unit = new Spinner(this); unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"sekunder", "minutter"})); unit.setSelection(1); root.addView(unit);
        start = new Button(this); start.setText("▶  START OPTAGELSE"); root.addView(start);
        stop = new Button(this); stop.setText("■  STOP OG LAV VIDEO"); stop.setEnabled(false); root.addView(stop);
        play = new Button(this); play.setText("▶  AFSPIL FÆRDIG VIDEO"); play.setVisibility(View.GONE); root.addView(play);
        TextView slideLabel = new TextView(this); slideLabel.setText("Lav timelapse fra eksisterende billeder:"); slideLabel.setTextSize(16); slideLabel.setPadding(0,10,0,0); root.addView(slideLabel);
        fps = new Spinner(this); fps.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"2 billeder/sek. (langsom)", "5 billeder/sek.", "10 billeder/sek.", "15 billeder/sek.", "24 billeder/sek. (hurtig)"})); fps.setSelection(2); root.addView(fps);
        slideshow = new Button(this); slideshow.setText("VÆLG MAPPE OG LAV TIMELAPSE"); root.addView(slideshow);
        TextView st = new TextView(this); st.setText("STATUS"); st.setTextSize(13); st.setTextColor(Color.DKGRAY); st.setPadding(0,8,0,3); root.addView(st);
        status = new TextView(this); status.setText("Klar til optagelse.\nBilleder og video gemmes i Pictures/QuietLapse."); status.setTextSize(15); status.setTextColor(Color.BLACK); status.setPadding(12,12,12,12); status.setBackgroundColor(Color.rgb(230,238,245)); root.addView(status);
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

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
        slideshow.setOnClickListener(v -> { Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(pick, PICK_IMAGE_FOLDER); });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try { getContentResolver().takePersistableUriPermission(tree, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        final int frameRate = new int[]{2,5,10,15,24}[fps.getSelectedItemPosition()];
        slideshow.setEnabled(false); start.setEnabled(false); stop.setEnabled(false); play.setVisibility(View.GONE);
        status.setText("LÆSER BILLEDMAPPE …\nVent mens billederne samles til video.");
        new Thread(() -> {
            File temp = null;
            try {
                temp = new File(getCacheDir(), "quietlapse_slideshow_" + System.currentTimeMillis());
                if (!temp.mkdirs()) throw new IOException("Kunne ikke oprette midlertidig mappe");
                ArrayList<Uri> images = listImages(tree);
                if (images.isEmpty()) throw new IOException("Der blev ikke fundet JPG-, PNG- eller WebP-billeder i den valgte mappe.");
                File[] copied = new File[images.size()]; int copiedCount = 0;
                for (int n=0; n<images.size(); n++) {
                    File dest = new File(temp, String.format(Locale.US, "img_%06d.jpg", n));
                    try (InputStream in = getContentResolver().openInputStream(images.get(n)); FileOutputStream out = new FileOutputStream(dest)) {
                        if (in == null) continue; byte[] buffer = new byte[64*1024]; int len;
                        while ((len=in.read(buffer)) != -1) out.write(buffer,0,len);
                    } catch (Exception badImage) { dest.delete(); continue; }
                    copied[copiedCount++] = dest;
                }
                if (copiedCount == 0) throw new IOException("Billederne kunne ikke læses.");
                copied = java.util.Arrays.copyOf(copied, copiedCount);
                File base = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "QuietLapse");
                if (!base.exists() && !base.mkdirs()) throw new IOException("Kunne ikke oprette Pictures/QuietLapse");
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File outDir = new File(base, "Slideshow_" + stamp); if (!outDir.mkdirs()) throw new IOException("Kunne ikke oprette outputmappe");
                File mp4 = new File(outDir, "timelapse.mp4");
                CaptureService.encodeImages(copied, mp4, frameRate);
                lastVideoPath = mp4.getAbsolutePath();
                final int finalCount = copiedCount;
                runOnUiThread(() -> { status.setText("FÆRDIG\nTimelapse gemt: " + lastVideoPath + "\nBilleder: " + finalCount + "\nHastighed: " + frameRate + " billeder/sek."); play.setVisibility(View.VISIBLE); slideshow.setEnabled(true); start.setEnabled(true); });
            } catch (Exception e) {
                final String error = e.getMessage() == null ? "Ukendt fejl" : e.getMessage();
                runOnUiThread(() -> { status.setText("SLIDESHOWFEJL\n" + error); slideshow.setEnabled(true); start.setEnabled(!recording); });
            } finally { if (temp != null) deleteTree(temp); }
        }).start();
    }

    private ArrayList<Uri> listImages(Uri tree) throws IOException {
        ArrayList<Uri> out = new ArrayList<>();
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor c = getContentResolver().query(children, cols, null, null, null)) {
            if (c == null) throw new IOException("Android kunne ikke læse mappen.");
            while (c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) continue;
                String lower = name == null ? "" : name.toLowerCase(Locale.US);
                if ((mime != null && mime.startsWith("image/")) || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) out.add(DocumentsContract.buildDocumentUriUsingTree(tree, id));
            }
        }
        Collections.sort(out, Comparator.comparing(u -> { String id = DocumentsContract.getDocumentId(u); int slash = id.lastIndexOf('/'); return (slash >= 0 ? id.substring(slash+1) : id).toLowerCase(Locale.US); }));
        return out;
    }
    private void deleteTree(File f) { if (f.isDirectory()) { File[] children = f.listFiles(); if (children != null) for (File child : children) deleteTree(child); } f.delete(); }

    private void playVideo() {
        if (lastVideoPath == null) { Toast.makeText(this, "Videoens placering er ikke kendt endnu. Se Pictures/QuietLapse.", Toast.LENGTH_LONG).show(); return; }
        File f = new File(lastVideoPath);
        if (!f.exists()) { Toast.makeText(this, "Videoen blev ikke fundet: " + lastVideoPath, Toast.LENGTH_LONG).show(); return; }
        try {
            Uri uri = Uri.fromFile(f); Intent i = new Intent(Intent.ACTION_VIEW); i.setDataAndType(uri, "video/mp4");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(i);
        } catch (Exception e) { Toast.makeText(this, "Ingen kompatibel videoafspiller fundet.", Toast.LENGTH_LONG).show(); }
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

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
    private static class AspectSurfaceView extends SurfaceView {
        private int ratioWidth = 3, ratioHeight = 4;
        AspectSurfaceView(Context context) { super(context); }
        void setAspectRatio(int width, int height) { if (width > 0 && height > 0) { ratioWidth=width; ratioHeight=height; requestLayout(); } }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
            int width = availableWidth;
            int height = (int)(width * (double)ratioHeight / ratioWidth);
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            int maxHeight = MeasureSpec.getSize(heightMeasureSpec);
            // Respect a fixed/maximum height from the layout instead of growing beyond the screen.
            if (heightMode != MeasureSpec.UNSPECIFIED && height > maxHeight && maxHeight > 0) {
                height = maxHeight;
                width = (int)(height * (double)ratioWidth / ratioHeight);
            }
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) width = Math.min(width, availableWidth);
            setMeasuredDimension(width, height);
        }
    }
}
