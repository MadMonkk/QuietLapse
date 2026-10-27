package dk.quietlapse.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.net.Uri;
import androidx.core.content.FileProvider;
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
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    EditText interval;
    Spinner unit;
    TextView status;
    Button start, stop, play, slideshow, openFolder;
    Spinner fps;
    static final int PICK_IMAGE_FOLDER = 240;
    AspectTextureView preview;
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
            updateRecordingButtons(recording);
            if (recording) play.setVisibility(View.GONE);
        }
    };

    private void updateRecordingButtons(boolean active) {
        if (start == null || stop == null) return;
        start.setEnabled(!active);
        stop.setEnabled(active);
        if (active) {
            start.setText("●  OPTAGELSE KØRER");
            start.setTextColor(Color.WHITE);
            start.setBackground(rounded(Color.rgb(22, 105, 92), 14, Color.rgb(80, 220, 200), 2));
            stop.setText("■  Stop og lav video");
            stop.setAlpha(1.0f);
        } else {
            start.setText("▶  Start optagelse");
            start.setTextColor(Color.rgb(5, 25, 32));
            start.setBackground(rounded(Color.rgb(0, 190, 160), 14, Color.rgb(0, 190, 160), 0));
            stop.setAlpha(0.65f);
        }
    }

    private LinearLayout.LayoutParams spacedButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8);
        return p;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private void styleButton(Button b, int bg, int fg) {
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setPadding(dp(16), dp(13), dp(16), dp(13));
        b.setMinHeight(dp(48));
        b.setBackground(rounded(bg, 14, bg, 0));
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(dp(2));
    }

    private void styleSpinner(Spinner s) {
        s.setPadding(dp(10), dp(5), dp(10), dp(5));
        s.setBackground(rounded(Color.rgb(35, 48, 67), 12, Color.rgb(65, 83, 105), 1));
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(Color.rgb(80, 220, 200));
        t.setPadding(0, dp(12), 0, dp(5));
        return t;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setBackgroundColor(Color.rgb(12, 20, 36));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(20));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(12, 20, 36), Color.rgb(20, 31, 51)}));
        TextView title = new TextView(this); title.setText("QuietLapse"); title.setTextSize(30);
        title.setTypeface(null, android.graphics.Typeface.BOLD); title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, dp(2)); root.addView(title);
        TextView help = new TextView(this); help.setText("TIMELAPSE  •  Fang tiden i bevægelse");
        help.setTextSize(14); help.setTextColor(Color.rgb(177, 193, 212));
        help.setPadding(0, dp(4), 0, dp(12)); root.addView(help);
        // TextureView cannot have a background drawable on Android 9. Put the
        // rounded background/border on a parent FrameLayout instead.
        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackground(rounded(Color.rgb(7, 12, 22), 18, Color.rgb(55, 82, 105), 1));
        if (Build.VERSION.SDK_INT >= 21) previewFrame.setClipToOutline(true);
        preview = new AspectTextureView(this);
        preview.setAspectRatio(4, 3);
        preview.setSurfaceTextureListener(this);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
        previewParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        root.addView(previewFrame, previewParams);
        TextView pLabel = new TextView(this); pLabel.setText("● LIVE KAMERAPREVIEW  ·  frigives under optagelse");
        pLabel.setTextSize(12); pLabel.setTextColor(Color.rgb(145, 166, 190));
        pLabel.setPadding(0, dp(8), 0, dp(8)); root.addView(pLabel);
        TextView lab = sectionLabel("OPTAGELSE  /  Interval mellem billeder"); root.addView(lab);
        interval = new EditText(this); interval.setSingleLine(true); interval.setInputType(2); interval.setText("15"); interval.setHint("Skriv et tal, fx 10");
        interval.setTextColor(Color.WHITE); interval.setHintTextColor(Color.rgb(135, 151, 174));
        interval.setPadding(dp(12), dp(10), dp(12), dp(10));
        interval.setBackground(rounded(Color.rgb(35, 48, 67), 12, Color.rgb(65, 83, 105), 1)); root.addView(interval);
        unit = new Spinner(this); unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"sekunder", "minutter"})); unit.setSelection(0); styleSpinner(unit); root.addView(unit);
        start = new Button(this); start.setText("▶  Start optagelse");
        styleButton(start, Color.rgb(0, 190, 160), Color.rgb(5, 25, 32)); root.addView(start, spacedButtonParams());
        stop = new Button(this); stop.setText("■  Stop og lav video"); stop.setEnabled(false);
        styleButton(stop, Color.rgb(235, 94, 105), Color.WHITE); root.addView(stop, spacedButtonParams());
        play = new Button(this); play.setText("▶  Afspil færdig video"); play.setVisibility(View.GONE);
        styleButton(play, Color.rgb(88, 115, 240), Color.WHITE); root.addView(play, spacedButtonParams());
        TextView slideLabel = sectionLabel("VIDEOINDSTILLINGER  /  Hastighed for alle timelapse-videoer"); root.addView(slideLabel);
        fps = new Spinner(this); fps.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"2 billeder/sek. (langsom)", "5 billeder/sek.", "10 billeder/sek.", "15 billeder/sek.", "24 billeder/sek. (hurtig)"})); fps.setSelection(2); styleSpinner(fps); root.addView(fps);
        slideshow = new Button(this); slideshow.setText("▧  Vælg billedmappe og lav timelapse");
        styleButton(slideshow, Color.rgb(51, 70, 99), Color.WHITE); root.addView(slideshow, spacedButtonParams());
        openFolder = new Button(this); openFolder.setText("▣  Åbn mappen med billeder");
        styleButton(openFolder, Color.rgb(35, 48, 67), Color.WHITE); root.addView(openFolder, spacedButtonParams());
        TextView st = sectionLabel("STATUS  /  Aktivitet"); root.addView(st);
        status = new TextView(this); status.setText("Klar til optagelse.\nBilleder og video gemmes i Pictures/QuietLapse."); status.setTextSize(14); status.setTextColor(Color.rgb(225, 235, 246));
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackground(rounded(Color.rgb(29, 43, 64), 14, Color.rgb(55, 75, 100), 1)); root.addView(status);
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
            i.putExtra("frame_rate", selectedFrameRate());
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            recording = true; updateRecordingButtons(true); play.setVisibility(View.GONE);
            status.setText("STARTER KAMERA …\nPreview slukkes, mens optagelsen bruger kameraet.");
        });
        stop.setOnClickListener(v -> { Intent i = new Intent(this, CaptureService.class); i.setAction(CaptureService.ACTION_STOP); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); stop.setEnabled(false); status.setText("STOPPER …\nVideoen bliver lavet. Vent, til der står FÆRDIG."); });
        play.setOnClickListener(v -> playVideo());
        slideshow.setOnClickListener(v -> { Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(pick, PICK_IMAGE_FOLDER); });
        openFolder.setOnClickListener(v -> openPicturesFolder());
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (interval != null) out.putString("interval", interval.getText().toString());
        if (unit != null) out.putInt("unit", unit.getSelectedItemPosition());
        if (fps != null) out.putInt("fps", fps.getSelectedItemPosition());
        if (lastVideoPath != null) out.putString("lastVideoPath", lastVideoPath);
        out.putBoolean("recording", recording);
    }

    @Override protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        if (interval != null) interval.setText(state.getString("interval", "15"));
        if (unit != null) unit.setSelection(state.getInt("unit", 0));
        if (fps != null) fps.setSelection(state.getInt("fps", 2));
        lastVideoPath = state.getString("lastVideoPath", lastVideoPath);
        recording = state.getBoolean("recording", recording);
        start.setEnabled(!recording);
        stop.setEnabled(recording);
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Keep entered values and active work intact; Android will remeasure the scrollable layout.
        if (preview != null) {
            ((AspectTextureView)preview).setAspectRatio(4, 3);
            preview.post(() -> { if (!recording) { releasePreview(); startPreview(); } });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try { getContentResolver().takePersistableUriPermission(tree, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        final int frameRate = selectedFrameRate();
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
                    if (n % 25 == 0) { final int done=n, total=images.size(); runOnUiThread(() -> status.setText("KOPIERER BILLEDER …\n" + (done*100/Math.max(1,total)) + "%\nBilleder: " + done + " / " + total)); }
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
                CaptureService.encodeImages(copied, mp4, frameRate, (done,total) -> {
                    final int pct=done*100/Math.max(1,total); final int count=done, all=total;
                    runOnUiThread(() -> status.setText("LAVER VIDEO …\n" + pct + "%\nBilleder behandlet: " + count + " / " + all + "\nVent, til der står FÆRDIG."));
                });
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

    private int selectedFrameRate() {
        int[] rates = new int[]{2, 5, 10, 15, 24};
        return rates[Math.max(0, Math.min(rates.length - 1, fps.getSelectedItemPosition()))];
    }

    private void openPicturesFolder() {
        try {
            Uri initial = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Pictures/QuietLapse");
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            if (Build.VERSION.SDK_INT >= 26) i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivity(i);
        }
    }

    private void playVideo() {
        if (lastVideoPath == null) { Toast.makeText(this, "Videoens placering er ikke kendt endnu. Se Pictures/QuietLapse.", Toast.LENGTH_LONG).show(); return; }
        File f = new File(lastVideoPath);
        if (!f.exists()) { Toast.makeText(this, "Videoen blev ikke fundet: " + lastVideoPath, Toast.LENGTH_LONG).show(); return; }
        try {
            Uri uri = FileProvider.getUriForFile(this, "dk.quietlapse.app.fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "video/mp4");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Afspil timelapse-video"));
        } catch (Exception e) { Toast.makeText(this, "Kunne ikke åbne videoen: " + (e.getMessage() == null ? "ukendt fejl" : e.getMessage()), Toast.LENGTH_LONG).show(); }
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private int displayOrientation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo(); Camera.getCameraInfo(id, info);
        int r = getWindowManager().getDefaultDisplay().getRotation(); int d = r==Surface.ROTATION_0?0:r==Surface.ROTATION_90?90:r==Surface.ROTATION_180?180:270;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return (360 - ((info.orientation+d)%360))%360;
        return (info.orientation-d+360)%360;
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { startPreview(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { if (!recording) { releasePreview(); startPreview(); } }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { releasePreview(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    private void startPreview() {
        if (recording || preview == null || !preview.isAvailable() || preview.getSurfaceTexture() == null) return;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            if (previewCamera == null) previewCamera = Camera.open(0);
            previewCamera.setDisplayOrientation(displayOrientation(0)); previewCamera.setPreviewTexture(preview.getSurfaceTexture());
            Camera.Parameters p = previewCamera.getParameters(); List<String> modes = p.getSupportedFocusModes();
            if (modes != null) { if (modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE); else if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); }
            previewCamera.setParameters(p); previewCamera.startPreview();
        } catch (Exception e) { status.setText("Preview kunne ikke startes. Kontrollér at kameraet ikke bruges af en anden app."); releasePreview(); }
    }
    private void releasePreview() { if (previewCamera != null) { try { previewCamera.stopPreview(); } catch (Exception ignored) {} try { previewCamera.release(); } catch (Exception ignored) {} previewCamera = null; } }
    @Override protected void onResume() {
        super.onResume();
        recording = CaptureService.isCapturing;
        updateRecordingButtons(recording);
        IntentFilter f = new IntentFilter(CaptureService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(statusReceiver, f);
        if (!recording && preview != null) preview.post(this::startPreview);
    }
    @Override protected void onPause() { try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {} releasePreview(); super.onPause(); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode==100) {
            status.setText("Tilladelser behandlet. Tryk Start igen.");
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) preview.post(this::startPreview);
        }
    }
    private static class AspectTextureView extends TextureView {
        private int ratioWidth = 3, ratioHeight = 4;
        AspectTextureView(Context context) { super(context); setOpaque(false); }
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
