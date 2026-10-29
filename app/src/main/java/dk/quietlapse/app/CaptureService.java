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
    private long interval=15000;
    private int frameRate=15;
    private boolean skipNight=false;
    private int nightStartMin=1200, nightEndMin=420;
    private boolean autoSun=false;
    private double sunLatitude=0, sunLongitude=0;
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
            interval = intent.getLongExtra("interval_ms",15000);
            frameRate = intent.getIntExtra("frame_rate",15);
            skipNight = intent.getBooleanExtra("skip_night", false);
            nightStartMin = intent.getIntExtra("night_start_min", 1200);
            nightEndMin = intent.getIntExtra("night_end_min", 420);
            autoSun = intent.getBooleanExtra("auto_sun", false);
            sunLatitude = intent.getDoubleExtra("sun_lat", 0.0);
            sunLongitude = intent.getDoubleExtra("sun_lon", 0.0);
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
    // Open the camera only when a frame is due. This is intentionally conservative:
    // the existing JPEG capture path is retained, but the camera is released between frames.
    private void openCameraForFrame() throws Exception {
        if (camera != null) return;
        Camera opened = Camera.open(0);
        try {
            texture = new SurfaceTexture(10);
            opened.setPreviewTexture(texture);
            Camera.Parameters p = opened.getParameters();
            List<Camera.Size> sizes = p.getSupportedPictureSizes();
            if (sizes != null && !sizes.isEmpty()) {
                Camera.Size best = sizes.get(0);
                for (Camera.Size size : sizes) {
                    if ((long)size.width * size.height > (long)best.width * best.height) best = size;
                }
                p.setPictureSize(best.width, best.height);
            }
            List<String> focusModes = p.getSupportedFocusModes();
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(0, info);
            int rotation;
            try {
                int display = ((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                int degrees = display == android.view.Surface.ROTATION_0 ? 0 : display == android.view.Surface.ROTATION_90 ? 90 : display == android.view.Surface.ROTATION_180 ? 180 : 270;
                rotation = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                        ? (info.orientation + degrees) % 360
                        : (info.orientation - degrees + 360) % 360;
            } catch (Exception ignored) { rotation = info.orientation; }
            p.setRotation(rotation);
            opened.setParameters(p);
            opened.startPreview();
            camera = opened;
        } catch (Exception e) {
            try { opened.release(); } catch (Exception ignored) { }
            if (texture != null) { try { texture.release(); } catch (Exception ignored) { } texture = null; }
            throw e;
        }
    }

    private void releaseCameraBetweenFrames() {
        Camera old = camera;
        camera = null;
        if (old != null) {
            try { old.stopPreview(); } catch (Exception ignored) { }
            try { old.release(); } catch (Exception ignored) { }
        }
        if (texture != null) {
            try { texture.release(); } catch (Exception ignored) { }
            texture = null;
        }
    }

    private void begin() {
        try {
            File base = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM), "QuietLapse");
            if (!base.exists() && !base.mkdirs()) throw new IOException("Kan ikke oprette DCIM/QuietLapse");
            sessionDir = new File(base, new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()));
            if (!sessionDir.mkdirs()) throw new IOException("Kan ikke oprette optagelsesmappe");
            savedCount = 0;
            running = true;
            isCapturing = true;
            captureTask = new Runnable() {
                @Override public void run() {
                    if (!running) return;
                    if (skipNight && isNightTime()) {
                        releaseCameraBetweenFrames();
                        reportStatus("OPTAGER – NATPAUSE\nKamera slukket indtil næste kontrol.\nBilleder gemt: " + savedCount);
                        handler.postDelayed(this, Math.max(interval, 60000L));
                        return;
                    }
                    try {
                        openCameraForFrame();
                        final Camera current = camera;
                        current.takePicture(null, null, (data, cam) -> {
                            try (FileOutputStream out = new FileOutputStream(new File(sessionDir, String.format(Locale.US, "img_%06d.jpg", System.currentTimeMillis())))) {
                                out.write(data);
                                savedCount++;
                            } catch (Exception e) {
                                reportStatus("FEJL: kunne ikke gemme billedet. Kontrollér lageradgang.");
                            }
                            // Release hardware after each shot to avoid running the sensor between intervals.
                            releaseCameraBetweenFrames();
                            if (running) {
                                reportStatus("OPTAGER\nBilleder gemt: " + savedCount + "\nMappe: DCIM/QuietLapse/" + sessionDir.getName());
                                handler.postDelayed(this, interval);
                            }
                        });
                    } catch (Exception e) {
                        releaseCameraBetweenFrames();
                        reportStatus("KAMERAFEJL\nKunne ikke åbne kameraet. Prøver igen ved næste interval.\nBilleder gemt: " + savedCount);
                        if (running) handler.postDelayed(this, interval);
                    }
                }
            };
            reportStatus("OPTAGER\nBilleder gemmes i: DCIM/QuietLapse/" + sessionDir.getName() + "\nInterval: " + interval/1000 + " sek.");
            handler.post(captureTask);
        } catch (Exception e) {
            running = false;
            isCapturing = false;
            reportStatus("FEJL: Optagelsen kunne ikke startes. Kontrollér kameratilladelse og lageradgang.");
            stopForeground(true);
            stopSelf();
        }
    }
    private boolean isNightTime() {
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        if (autoSun) {
            double offsetHours = c.getTimeZone().getOffset(c.getTimeInMillis()) / 3600000.0;
            SolarTimes times = SolarTimes.forDate(c.get(Calendar.DAY_OF_YEAR), sunLatitude, sunLongitude, offsetHours);
            if (times != null) return now < times.sunriseMinute || now >= times.sunsetMinute;
        }
        if (nightStartMin == nightEndMin) return false;
        if (nightStartMin < nightEndMin) return now >= nightStartMin && now < nightEndMin;
        return now >= nightStartMin || now < nightEndMin;
    }

    // NOAA approximation; accurate enough for a timelapse night pause and needs no internet.
    private static class SolarTimes {
        final int sunriseMinute, sunsetMinute;
        SolarTimes(int rise, int set) { sunriseMinute=rise; sunsetMinute=set; }
        static SolarTimes forDate(int day, double lat, double lon, double tzHours) {
            double gamma = 2.0 * Math.PI / 365.0 * (day - 1);
            double eq = 229.18 * (0.000075 + 0.001868*Math.cos(gamma) - 0.032077*Math.sin(gamma) - 0.014615*Math.cos(2*gamma) - 0.040849*Math.sin(2*gamma));
            double decl = 0.006918 - 0.399912*Math.cos(gamma) + 0.070257*Math.sin(gamma) - 0.006758*Math.cos(2*gamma) + 0.000907*Math.sin(2*gamma) - 0.002697*Math.cos(3*gamma) + 0.00148*Math.sin(3*gamma);
            double cosH = Math.cos(Math.toRadians(90.833))/(Math.cos(Math.toRadians(lat))*Math.cos(decl)) - Math.tan(Math.toRadians(lat))*Math.tan(decl);
            if (cosH < -1 || cosH > 1) return null;
            double ha = Math.toDegrees(Math.acos(cosH));
            int rise = (int)Math.round(720 - 4*(lon + ha) - eq + tzHours*60);
            int set = (int)Math.round(720 - 4*(lon - ha) - eq + tzHours*60);
            return new SolarTimes((rise%1440+1440)%1440, (set%1440+1440)%1440);
        }
    }
    private void updateNotification(String s) { if (Build.VERSION.SDK_INT>=26) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification(s)); }
    private void stopCaptureAndEncode() {
        running=false; isCapturing=false;
        reportStatus("STOPPER OPTAGELSE\nForbereder video …\nBilleder gemt: " + savedCount);
        if (captureTask!=null) handler.removeCallbacks(captureTask);
        releaseCameraBetweenFrames();
        new Thread(() -> {
            try {
                if (sessionDir==null || sessionDir.listFiles()==null || sessionDir.listFiles().length==0) throw new IOException("Ingen billeder blev taget");
                File[] pics=sessionDir.listFiles((d,n)->n.toLowerCase(Locale.US).endsWith(".jpg"));
                Arrays.sort(pics, Comparator.comparing(File::getName));
                File mp4 = new File(sessionDir,"timelapse.mp4");
                encode(pics,mp4,frameRate,(done,total)->reportStatus("LAVER VIDEO …\n" + (done*100/Math.max(1,total)) + "%\nBilleder behandlet: " + done + " / " + total));
                ContentValues cv=new ContentValues(); cv.put(MediaStore.Video.Media.DISPLAY_NAME,"QuietLapse_"+sessionDir.getName()+".mp4"); cv.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4"); cv.put(MediaStore.Video.Media.DATA,mp4.getAbsolutePath());
                getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cv);
                reportStatus("FÆRDIG\nVideo gemt: DCIM/QuietLapse/" + sessionDir.getName() + "/timelapse.mp4\nBilleder: " + pics.length);
            } catch(Exception e) { reportStatus("VIDEOFEJL\nVideoen kunne ikke laves. Billederne er bevaret i DCIM/QuietLapse."); }
            stopForeground(true); stopSelf();
        }).start();
    }
    private static class EncodeState { int track=-1; boolean muxStarted=false; }
    public interface ProgressListener { void onProgress(int done, int total); }
    public static void encodeImages(File[] pics, File outFile, int frameRate) throws Exception { new CaptureService().encode(pics, outFile, frameRate, null); }
    public static void encodeImages(File[] pics, File outFile, int frameRate, ProgressListener listener) throws Exception { new CaptureService().encode(pics, outFile, frameRate, listener); }
    private void encode(File[] pics, File outFile) throws Exception { encode(pics, outFile, 15, null); }
    private void encode(File[] pics, File outFile, int frameRate) throws Exception { encode(pics, outFile, frameRate, null); }
    private void encode(File[] pics, File outFile, int frameRate, ProgressListener listener) throws Exception {
        if (pics.length==0) throw new IOException("Ingen billeder");
        // Sample at most 200 images to choose the video orientation. This avoids a full
        // metadata scan of thousands of images before encoding starts.
        int landscape=0, portrait=0, firstW=0, firstH=0;
        int samples = Math.min(pics.length, 200);
        for (int si = 0; si < samples; si++) {
            File pic = pics[(int)((long)si * pics.length / samples)];
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(pic.getAbsolutePath(), bounds);
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                int orientation = exifOrientation(pic);
                boolean swap = orientation == 5 || orientation == 6 || orientation == 7 || orientation == 8;
                int ow = swap ? bounds.outHeight : bounds.outWidth;
                int oh = swap ? bounds.outWidth : bounds.outHeight;
                if (firstW == 0) { firstW = ow; firstH = oh; }
                if (ow >= oh) landscape++; else portrait++;
            }
        }
        if (firstW <= 0 || firstH <= 0) throw new IOException("Ingen billeder kunne læses");
        boolean landscapeVideo = landscape >= portrait;
        int sourceW = landscapeVideo ? Math.max(firstW, firstH) : Math.min(firstW, firstH);
        int sourceH = landscapeVideo ? Math.min(firstW, firstH) : Math.max(firstW, firstH);
        // Cap landscape at 1920x1080 and portrait at 1080x1920 for Android 9 compatibility.
        float maxW = landscapeVideo ? 1920.0f : 1080.0f;
        float maxH = landscapeVideo ? 1080.0f : 1920.0f;
        float scale = Math.min(1.0f, Math.min(maxW/sourceW, maxH/sourceH));
        int w=Math.max(2, ((int)(sourceW*scale)) & ~1);
        int h=Math.max(2, ((int)(sourceH*scale)) & ~1);
        MediaFormat fmt=MediaFormat.createVideoFormat("video/avc",w,h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE,2500000);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE,frameRate);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
        MediaCodec codec=MediaCodec.createEncoderByType("video/avc");
        codec.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        MediaMuxer muxer=new MediaMuxer(outFile.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        EncodeState state=new EncodeState();
        long pts=0;
        try {
            int processed=0;
            int lastReportedPercent=-1;
            for (File pic:pics) {
                android.graphics.Bitmap bm=decodeOrientedBitmap(pic,w,h);
                if (bm==null) { processed++; if(listener!=null) { int pct=processed*100/pics.length; if(pct!=lastReportedPercent) { listener.onProgress(processed,pics.length); lastReportedPercent=pct; } } continue; }
                android.graphics.Bitmap scaled=fitBitmap(bm,w,h);
                int in=codec.dequeueInputBuffer(10000);
                if(in<0) { drainEncoder(codec,muxer,info,state,false); in=codec.dequeueInputBuffer(10000); }
                if(in>=0) {
                    byte[] yuv=bitmapToNV12(scaled,w,h);
                    java.nio.ByteBuffer buf=codec.getInputBuffer(in); buf.clear();
                    buf.put(yuv,0,yuv.length);
                    codec.queueInputBuffer(in,0,yuv.length,pts,0);
                    pts += 1000000L/frameRate;
                }
                if(scaled!=bm) scaled.recycle(); bm.recycle();
                drainEncoder(codec,muxer,info,state,false);
                processed++;
                if(listener!=null) { int pct=processed*100/pics.length; if(pct!=lastReportedPercent) { listener.onProgress(processed,pics.length); lastReportedPercent=pct; } }
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
    private int exifOrientation(File file) {
        try { return new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath()).getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL); }
        catch (Exception ignored) { return 1; }
    }
    private android.graphics.Bitmap decodeOrientedBitmap(File file, int targetWidth, int targetHeight) {
        // Decode near the final video resolution instead of allocating a full-resolution
        // camera bitmap (often 12 MP) for every frame. This greatly reduces memory traffic.
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int orientation = exifOrientation(file);
        boolean swap = orientation == 5 || orientation == 6 || orientation == 7 || orientation == 8;
        int orientedWidth = swap ? bounds.outHeight : bounds.outWidth;
        int orientedHeight = swap ? bounds.outWidth : bounds.outHeight;
        int sample = 1;
        while (sample <= 8 && orientedWidth / (sample * 2) >= targetWidth && orientedHeight / (sample * 2) >= targetHeight) sample *= 2;
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
        android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bm == null) return null;
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        switch (orientation) {
            case 2: matrix.setScale(-1, 1); break;
            case 3: matrix.setRotate(180); break;
            case 4: matrix.setScale(1, -1); break;
            case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case 6: matrix.setRotate(90); break;
            case 7: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
            case 8: matrix.setRotate(-90); break;
            default: return bm;
        }
        try {
            android.graphics.Bitmap rotated = android.graphics.Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
            if (rotated != bm) bm.recycle();
            return rotated;
        } catch (Exception ignored) { return bm; }
    }
    private android.graphics.Bitmap fitBitmap(android.graphics.Bitmap source, int width, int height) {
        android.graphics.Bitmap target = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(target); canvas.drawColor(android.graphics.Color.BLACK);
        // Fit without cropping; images of the minority orientation get black side/top bars.
        float scale = Math.min((float)width/source.getWidth(), (float)height/source.getHeight());
        float dw = source.getWidth()*scale, dh = source.getHeight()*scale;
        android.graphics.RectF dst = new android.graphics.RectF((width-dw)/2f,(height-dh)/2f,(width+dw)/2f,(height+dh)/2f);
        canvas.drawBitmap(source, null, dst, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        return target;
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
    @Override public void onDestroy() { running=false; isCapturing=false; if(captureTask!=null)handler.removeCallbacks(captureTask); releaseCameraBetweenFrames(); super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent i) { return null; }
}
