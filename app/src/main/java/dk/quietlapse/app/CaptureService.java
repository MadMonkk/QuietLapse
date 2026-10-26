package dk.quietlapse.app;

import android.app.*;
import android.content.*;
import android.hardware.Camera;
import android.os.*;
import android.graphics.SurfaceTexture;
import android.media.*;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.net.Uri;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class CaptureService extends Service {
    public static final String ACTION_START="start", ACTION_STOP="stop";
    public static volatile boolean isCapturing = false;
    private Handler handler;
    private Camera camera;
    private SurfaceTexture texture;
    private Runnable captureTask;
    private long interval=60000;
    private File sessionDir;
    private boolean running=false;
    private int savedCount=0;
    public static final String ACTION_STATUS="dk.quietlapse.app.STATUS";
    private void reportStatus(String message) {
        Intent i = new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        i.putExtra("message", message);
        i.putExtra("running", running);
        sendBroadcast(i);
        updateNotification(message);
    }

    @Override public void onCreate() {
        super.onCreate(); handler = new Handler(Looper.getMainLooper());
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("quietlapse", "QuietLapse optagelse", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            interval = intent.getLongExtra("interval_ms",60000);
            startForeground(7, notification("Starter kamera …"));
            reportStatus("Starter kamera …");
            if (!running) begin();
        } else if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopCaptureAndEncode();
        }
        return START_STICKY;
    }
    private Notification notification(String s) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this,"quietlapse") : new Notification.Builder(this);
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, 70, openApp,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
        return b.setContentTitle("QuietLapse").setContentText(s).setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(tap).setAutoCancel(false).build();
    }
    private void begin() {
        try {
            File base = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),"QuietLapse");
            if (!base.exists()) base.mkdirs();
            sessionDir = new File(base, new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date()));
            if (!sessionDir.mkdirs()) throw new IOException("Kan ikke oprette optagelsesmappe");
            camera = Camera.open(0);
            texture = new SurfaceTexture(10);
            camera.setPreviewTexture(texture);
            Camera.Parameters p = camera.getParameters();
            List<Camera.Size> sizes = p.getSupportedPictureSizes();
            if (sizes != null && !sizes.isEmpty()) {
                // Choose the highest supported resolution (the first version accidentally chose the smallest).
                Camera.Size best = sizes.get(0);
                for (Camera.Size s : sizes) if ((long)s.width*s.height > (long)best.width*best.height) best=s;
                p.setPictureSize(best.width,best.height);
            }
            List<String> focusModes = p.getSupportedFocusModes();
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }
            // Store JPEG rotation metadata for a portrait/landscape phone orientation.
            // The rear camera on many Android phones has a 90-degree sensor offset.
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(0, info);
            int rotation = 0;
            try {
                int display = ((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                int degrees = display == android.view.Surface.ROTATION_0 ? 0 : display == android.view.Surface.ROTATION_90 ? 90 : display == android.view.Surface.ROTATION_180 ? 180 : 270;
                rotation = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                        ? (info.orientation + degrees) % 360
                        : (info.orientation - degrees + 360) % 360;
            } catch (Exception ignored) { rotation = info.orientation; }
            p.setRotation(rotation);
            camera.setParameters(p);
            camera.startPreview();
            running=true;
            isCapturing=true;
            captureTask = new Runnable() {
                @Override public void run() {
                    if (!running || camera==null) return;
                    try { camera.takePicture(null,null, (data, cam) -> {
                        try (FileOutputStream out = new FileOutputStream(new File(sessionDir,String.format(Locale.US,"img_%06d.jpg",System.currentTimeMillis())))) { out.write(data); savedCount++; }
                        catch(Exception e) { reportStatus("FEJL: kunne ikke gemme billedet. Kontrollér lageradgang."); }
                        try { cam.startPreview(); } catch(Exception e) {}
                        reportStatus("OPTAGER\nBilleder gemt: " + savedCount + "\nMappe: Pictures/QuietLapse/" + sessionDir.getName());
                    }); } catch(Exception e) {}
                    handler.postDelayed(this, interval);
                }
            };
            handler.post(captureTask);
            savedCount=0;
            reportStatus("OPTAGER\nBilleder gemmes i: Pictures/QuietLapse/" + sessionDir.getName() + "\nInterval: " + interval/1000 + " sek.");
        } catch(Exception e) { running=false; isCapturing=false; reportStatus("FEJL: Kameraet kunne ikke startes. Kontrollér kameratilladelse."); stopForeground(true); stopSelf(); }
    }
    private void updateNotification(String s) { if (Build.VERSION.SDK_INT>=26) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification(s)); }
    private void stopCaptureAndEncode() {
        running=false; isCapturing=false;
        reportStatus("STOPPER OPTAGELSE\nForbereder video …\nBilleder gemt: " + savedCount);
        if (captureTask!=null) handler.removeCallbacks(captureTask);
        if (camera!=null) { try { camera.stopPreview(); camera.release(); } catch(Exception e){} camera=null; }
        if (texture!=null) { try { texture.release(); } catch(Exception e){} texture=null; }
        new Thread(() -> {
            try {
                if (sessionDir==null || sessionDir.listFiles()==null || sessionDir.listFiles().length==0) throw new IOException("Ingen billeder blev taget");
                File[] pics=sessionDir.listFiles((d,n)->n.toLowerCase(Locale.US).endsWith(".jpg"));
                Arrays.sort(pics, Comparator.comparing(File::getName));
                File mp4 = new File(sessionDir,"timelapse.mp4");
                encode(pics,mp4);
                ContentValues cv=new ContentValues(); cv.put(MediaStore.Video.Media.DISPLAY_NAME,"QuietLapse_"+sessionDir.getName()+".mp4"); cv.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4"); cv.put(MediaStore.Video.Media.DATA,mp4.getAbsolutePath());
                getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cv);
                reportStatus("FÆRDIG\nVideo gemt: Pictures/QuietLapse/" + sessionDir.getName() + "/timelapse.mp4\nBilleder: " + pics.length);
            } catch(Exception e) { reportStatus("VIDEOFEJL\nVideoen kunne ikke laves. Billederne er bevaret i Pictures/QuietLapse."); }
            stopForeground(true); stopSelf();
        }).start();
    }
    private static class EncodeState { int track=-1; boolean muxStarted=false; }
    private void encode(File[] pics, File outFile) throws Exception {
        if (pics.length==0) throw new IOException("Ingen billeder");
        android.graphics.Bitmap first=android.graphics.BitmapFactory.decodeFile(pics[0].getAbsolutePath());
        if (first==null) throw new IOException("Første billede kunne ikke læses");
        // Keep original photos at full resolution, but cap the MP4 frame size for Android 9 encoders.
        int sourceW=first.getWidth(), sourceH=first.getHeight();
        float scale=Math.min(1.0f, Math.min(1920.0f/sourceW, 1080.0f/sourceH));
        int w=Math.max(2, ((int)(sourceW*scale)) & ~1);
        int h=Math.max(2, ((int)(sourceH*scale)) & ~1);
        first.recycle();
        MediaFormat fmt=MediaFormat.createVideoFormat("video/avc",w,h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE,2500000);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE,15);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
        MediaCodec codec=MediaCodec.createEncoderByType("video/avc");
        codec.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        MediaMuxer muxer=new MediaMuxer(outFile.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        EncodeState state=new EncodeState();
        long pts=0;
        try {
            for (File pic:pics) {
                android.graphics.Bitmap bm=android.graphics.BitmapFactory.decodeFile(pic.getAbsolutePath());
                if (bm==null) continue;
                android.graphics.Bitmap scaled=android.graphics.Bitmap.createScaledBitmap(bm,w,h,true);
                int in=codec.dequeueInputBuffer(10000);
                if(in<0) { drainEncoder(codec,muxer,info,state,false); in=codec.dequeueInputBuffer(10000); }
                if(in>=0) {
                    byte[] yuv=bitmapToNV12(scaled,w,h);
                    java.nio.ByteBuffer buf=codec.getInputBuffer(in); buf.clear();
                    buf.put(yuv,0,yuv.length);
                    codec.queueInputBuffer(in,0,yuv.length,pts,0);
                    pts += 1000000L/15;
                }
                if(scaled!=bm) scaled.recycle(); bm.recycle();
                drainEncoder(codec,muxer,info,state,false);
            }
            int end=codec.dequeueInputBuffer(10000);
            if(end>=0) codec.queueInputBuffer(end,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            drainEncoder(codec,muxer,info,state,true);
        } finally {
            try { codec.stop(); } catch(Exception ignored) {}
            codec.release();
            try { if(state.muxStarted) muxer.stop(); } catch(Exception ignored) {}
            muxer.release();
        }
        if(!state.muxStarted || !outFile.exists() || outFile.length()==0) throw new IOException("MP4 blev ikke oprettet");
    }
    private void drainEncoder(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info, EncodeState state, boolean end) {
        boolean done=false;
        while(!done) {
            int ix=codec.dequeueOutputBuffer(info,end?10000:0);
            if(ix==MediaCodec.INFO_TRY_AGAIN_LATER) { if(!end) done=true; }
            else if(ix==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                state.track=muxer.addTrack(codec.getOutputFormat()); muxer.start(); state.muxStarted=true;
            } else if(ix>=0) {
                java.nio.ByteBuffer out=codec.getOutputBuffer(ix);
                if(info.size>0 && state.muxStarted && out!=null) {
                    out.position(info.offset); out.limit(info.offset+info.size);
                    muxer.writeSampleData(state.track,out,info);
                }
                boolean eos=(info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                codec.releaseOutputBuffer(ix,false);
                if(eos) done=true;
            }
        }
    }
    private byte[] bitmapToNV12(android.graphics.Bitmap b,int w,int h) {
        int[] pix=new int[w*h]; b.getPixels(pix,0,w,0,0,w,h);
        byte[] yuv=new byte[w*h*3/2]; int yIndex=0,uvIndex=w*h;
        for(int j=0;j<h;j++) for(int x=0;x<w;x++) {
            int c=pix[j*w+x], r=(c>>16)&255,g=(c>>8)&255,bl=c&255;
            yuv[yIndex++]=(byte)Math.max(0,Math.min(255,((66*r+129*g+25*bl+128)>>8)+16));
            if((j&1)==0 && (x&1)==0) {
                int u=((-38*r-74*g+112*bl+128)>>8)+128;
                int v=((112*r-94*g-18*bl+128)>>8)+128;
                yuv[uvIndex++]=(byte)Math.max(0,Math.min(255,u)); yuv[uvIndex++]=(byte)Math.max(0,Math.min(255,v));
            }
        }
        return yuv;
    }
    @Override public void onDestroy() { running=false; isCapturing=false; if(captureTask!=null)handler.removeCallbacks(captureTask); if(camera!=null)try{camera.release();}catch(Exception e){} super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent i) { return null; }
}
