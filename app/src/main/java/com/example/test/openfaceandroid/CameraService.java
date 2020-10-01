package com.example.test.openfaceandroid;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.location.Address;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import zh.wang.android.yweathergetter4a.WeatherInfo;
import zh.wang.android.yweathergetter4a.YahooWeather;
import zh.wang.android.yweathergetter4a.YahooWeatherInfoListener;

public class CameraService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, YahooWeatherInfoListener {

    public CameraService() {
    }

    private final static boolean DEBUG_LOG = true;

//    private Context context;
    private final static int WIDTH = 640;
    private final static int HEIGHT = 480;
    private final static int PIXELBYTES = 4;
    private final static int BYTESIZE = WIDTH * HEIGHT * PIXELBYTES;
    private final static int RESULTSPAN = 33;
    private final static Handler handler = new Handler(Looper.getMainLooper());
    private final static Bitmap bitmap = Bitmap.createBitmap(HEIGHT, WIDTH, Bitmap.Config.ARGB_8888); // swaped
    private final static byte[][] buffers = new byte[2][BYTESIZE];
    private static final String TAG = "CameraService";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static ImageView vImageView;
    private static boolean isPaused;
    private static boolean init;
    private static boolean initCamera;
    SharedPreferences prefs;

//    private static String LOG_PATH = "";
//
//    private static String LOG_FILE_CAM = "";
//    private static String DEBUG_FILE_CAM = "";
//
//    private static String LOG_FILE_ACT = "";

    HashMap<String, Boolean> cameraAvailableList;

    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    BroadcastReceiver mScreenStateReceiver;
    private boolean serviceOnline;
    private int LOG_INTERVAL = 330;

    private boolean SCREEN_ON = false;

    // Activity API
    public GoogleApiClient mApiClient;

    private FusedLocationProviderClient mFusedLocationClient;
    Location mLocation = null;
    private final static int TIMESPAN_LOCATIONLOG = 1000 * 60 * 15; // 1000 (ms) * 60(s) * 15(m)
    private long LAST_MS_LOCATIONLOG = 0;

    // Weather
    private YahooWeather mYahooWeather = YahooWeather.getInstance(5000, true);
    private final static int TIMESPAN_WEATHERLOG = 1000 * 60 * 60; // 1000 ms * 60s * 60m
    //    private int TIMETICK_WEATHERLOG = 1000 * 60 * 60;
    private long LAST_MS_WEATHERLOG = 0;
//    String[] lastWeatherInfo = new String[3];

    // GeoLocation for Weather
    float GeoLong = Float.NaN;
    float GeoLat = Float.NaN;

    // User Dialog Ground Truth
    private final static int TIMESPAN_USERDIALOG = 1000 * 60 * 15;
    //    private int TIMETICK_USERDIALOG = TIMESPAN_USERDIALOG;
    private long LAST_MS_USERDIALOG = 0;

    private Thread.UncaughtExceptionHandler onRuntimeError= new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(Thread thread, Throwable ex) {
            //Try starting the Activity again
            Logging.appendLog("UncaughtExceptionHandler ERROR in SERVICE, Thread: " + thread.getName() + "\tE:" + ex
                    + "\tEx:" + ex.toString(), Logging.DEBUG_FILE_CAM, true, true);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Logging.appendLog("RestartService after UncaughtExceptionHandler"
                                    , Logging.DEBUG_FILE_CAM, true, true);
                    startService(new Intent(CameraService.this, CameraService.class));
                }
            }, 10000);
        }
    };

    @Override
    public void gotWeatherInfo(final WeatherInfo weatherInfo, YahooWeather.ErrorType errorType) {
        // TODO Auto-generated method stub
        if (weatherInfo != null) {
            Address address = weatherInfo.getAddress();
            String weatherLog =
                    weatherInfo.getCurrentText()
                    + ";" + weatherInfo.getCurrentTemp()
                            + ";" + address.getCountryCode()
                            + ";" + address.getPostalCode()
                            + ";" + address.getLocality()
                            + ";" + address.getSubLocality()
                            + ";" + address.getThoroughfare()
                            + ";" + address.getSubThoroughfare()
                            + ";" + address.getFeatureName()
                    ;
            Logging.appendLog(weatherLog, Logging.LOG_FILE_WEATHER);
            Log.i(TAG, "====== Current Weather ======" + "\n" +
                    "date: " + weatherInfo.getCurrentConditionDate() + "\n" +
                    "weather: " + weatherInfo.getCurrentText() + "\n" +
                    "temperature in ºC: " + weatherInfo.getCurrentTemp() + "\n" +
                    "wind in ºC: " + weatherInfo.getWindChill() + "\n"
            );
        } else {
            Log.d(TAG, errorType.name());
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Intent intent = new Intent(getApplicationContext(), ActivityRecognizedService.class);
//        intent.putExtra(Constants.LOG_FILE_ACT, Logging.LOG_FILE_ACT);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mApiClient, 0, pendingIntent);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection suspended");
        mApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private String printForegroundTask() {
        String currentApp = "NULL";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            //noinspection WrongConstant
            UsageStatsManager usm = (UsageStatsManager) this.getSystemService("usagestats");
            long time = System.currentTimeMillis();
            List<UsageStats> appList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 1000, time);
            if (appList != null && appList.size() > 0) {
                SortedMap<Long, UsageStats> mySortedMap = new TreeMap<Long, UsageStats>();
                for (UsageStats usageStats : appList) {
                    mySortedMap.put(usageStats.getLastTimeUsed(), usageStats);
                }
                if (mySortedMap != null && !mySortedMap.isEmpty()) {
                    currentApp = mySortedMap.get(mySortedMap.lastKey()).getPackageName();
                }
            }
        } else {
            ActivityManager am = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> tasks = am.getRunningAppProcesses();
            currentApp = tasks.get(0).processName;
        }
        return currentApp;
    }

    private final Runnable checkResult = new Runnable() {
        @Override
        public final void run() {

            // Adjust Ticks:
            long currentTimeMillis = System.currentTimeMillis();

            // Check Weather each hour and wait till mLocation != null
            if (currentTimeMillis - LAST_MS_WEATHERLOG >= TIMESPAN_WEATHERLOG && mLocation != null) {
                String key = Globals.PACKAGE_NAME + ".LAST_MS_WEATHERLOG";
                prefs.edit().putLong(key, currentTimeMillis).apply();
                LAST_MS_WEATHERLOG = currentTimeMillis;
                // Log Weather
                if (DEBUG_LOG) {
                    Logging.appendLog("Logging Weather", Logging.DEBUG_FILE_CAM, true, true);
                }
                mYahooWeather.queryYahooWeatherByLatLon(getApplicationContext(), mLocation.getLatitude(), mLocation.getLongitude(), CameraService.this);
            }

            // Check Location each hour
            if (currentTimeMillis - LAST_MS_LOCATIONLOG >= TIMESPAN_LOCATIONLOG) {
                String key = Globals.PACKAGE_NAME + ".LAST_MS_LOCATIONLOG";
                prefs.edit().putLong(key, currentTimeMillis).apply();
                LAST_MS_LOCATIONLOG = currentTimeMillis;
                if (DEBUG_LOG) {
                    Logging.appendLog("Logging LastLocation", Logging.DEBUG_FILE_CAM, true, true);
                }
                //noinspection MissingPermission
                mFusedLocationClient.getLastLocation()
                        .addOnSuccessListener(new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location location) {
                                // Got last known location. In some rare situations this can be null.
                                if (location != null) {
                                    // ...
                                    mLocation = location;
                                }
                            }
                        });
            }

            // Deal with OpenFace Result:
            if (Wrapper.dequeue() &&
                    Wrapper.result.pixeldata != null &&
                    Wrapper.result.pixeldata.length >= BYTESIZE) {
                // Display image if vImageView is still there
                if (vImageView != null)
                {
                    final ByteBuffer bytebuf = ByteBuffer.wrap(Wrapper.result.pixeldata);
                    // show last frame
                    bitmap.copyPixelsFromBuffer(bytebuf);
                    vImageView.setImageBitmap(bitmap);
                }
                // Log result if face is detected
                if (Wrapper.result.faceDetected) {
                    // Get currently open app:
                    String currentApp = printForegroundTask();
                    // Location
                    String locationString = "";
                    if (mLocation != null) {
                        locationString = mLocation.getLongitude() + ";" + mLocation.getLatitude();
                    }
//                    // Weather
//                    String weatherString = "";
//                    if (lastWeatherInfo != null) {
//                        weatherString = lastWeatherInfo[1] + "," + lastWeatherInfo[2];
//                    }
                    String logRes = Wrapper.result.emotFloatAngry
                            + ";" + Wrapper.result.emotFloatDisgusted
                            + ";" + Wrapper.result.emotFloatFeared
                            + ";" + Wrapper.result.emotFloatHappy
                            + ";" + Wrapper.result.emotFloatNeutral
                            + ";" + Wrapper.result.emotFloatSad
                            + ";" + Wrapper.result.emotFloatSurprised
                            + ";" + Wrapper.result.attention
                            + ";" + currentApp
                            + ";" + locationString
//                            + ";" + weatherString
                            ;
                    Log.d(TAG, "Anger;Disgust;Fear;Happiness;Neutral;Sad;Surprised;Attention;app;"
                            + "long;lat;Weather;Temp\n"
                            + logRes);
//                    String timestamp = GetCurrentTimeStamp(true);
                    Logging.appendLog(logRes, Logging.LOG_FILE_CAM);
                }
            }

            if (currentTimeMillis - LAST_MS_USERDIALOG >= TIMESPAN_USERDIALOG) {
                String key = Globals.PACKAGE_NAME + ".LAST_MS_USERDIALOG";
                prefs.edit().putLong(key, currentTimeMillis).apply();
                LAST_MS_USERDIALOG = currentTimeMillis;

                if (DEBUG_LOG) {
                    Logging.appendLog("User Dialog", Logging.DEBUG_FILE_CAM, true, true);
                }

                NotificationCompat.Builder builder =
                        new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.drawable.cast_ic_notification_small_icon)
                                .setContentTitle("Rate current emotions")
                                .setContentText("Please rate your current emotions")
                                .setAutoCancel(true);
                int NOTIFICATION_ID = 12345;
                Intent targetIntent = new Intent(getApplicationContext(), DialogEmotions.class);
                PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                builder.setContentIntent(contentIntent);
                NotificationManager nManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nManager.notify(NOTIFICATION_ID, builder.build());
            }

            // schedule next check
            // 33ms = ~30fps max output fps
            if (!isPaused)
                handler.postDelayed(checkResult, RESULTSPAN);
        }
    };

    /////////////// CAMERA 2 Start
    private String cameraId;
    private Size imageDimension;
    private ImageReader mImageReader;
    private File file;
    private boolean mFlashSupported;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private String mCameraId;

    private Handler mAvailabilityHandler;
    private HandlerThread mAvailabilityThread;

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            if (DEBUG_LOG)
                Logging.appendLog("onOpened", Logging.DEBUG_FILE_CAM, true, true);
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            if (DEBUG_LOG)
                Logging.appendLog("onError", Logging.DEBUG_FILE_CAM, true, true);
            Log.e(TAG, "onError");
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private CameraManager.AvailabilityCallback mAvailabilityCallback = new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(String cameraId) {
            Log.e(TAG, "onCameraAvailable: " + cameraId);
            if (DEBUG_LOG)
                Logging.appendLog("onCameraAvailable: " + cameraId, Logging.DEBUG_FILE_CAM, true, true);
            super.onCameraAvailable(cameraId);
            //   Do your work
            cameraAvailableList.put(cameraId, true);
            boolean allCamerasAvailable = AllCamerasAvailable(cameraAvailableList);
            if (allCamerasAvailable && isPaused && SCREEN_ON) {
                Log.d(TAG, "All cameras available: Try to restart in 3 seconds");
                if (DEBUG_LOG)
                    Logging.appendLog("All cameras available: Try to restart in 3 seconds", Logging.DEBUG_FILE_CAM, true, true);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        // this code will be executed after 3 seconds

                        boolean allCamerasAvailable = AllCamerasAvailable(cameraAvailableList);
                        if (allCamerasAvailable) {
                            if (isPaused && SCREEN_ON) {
                                // wenn nicht pausiert und screenon, Threads neustarten:
                                Log.e(TAG, "ReInit!");
                                if (DEBUG_LOG)
                                    Logging.appendLog("ReInit", Logging.DEBUG_FILE_CAM, true, true);
                                initCamera = true;
                                isPaused = false;
                                startBackgroundThread();
                                handler.postDelayed(checkResult, RESULTSPAN);
                                openCamera();
                            } else {
                                Log.e(TAG, "ReInit failed: isPaused=" + isPaused
                                        + ", SCREEN_ON=" + SCREEN_ON);
                                if (DEBUG_LOG)
                                    Logging.appendLog("ReInit failed: isPaused=" + isPaused
                                                    + ", SCREEN_ON=" + SCREEN_ON, Logging.DEBUG_FILE_CAM,
                                            true, true);
                            }
                        } else {
                            Log.e(TAG, "ReInit after 3 sec failed, not all cam av.");
                            if (DEBUG_LOG)
                                Logging.appendLog("ReInit after 3 sec failed, not all cam av.", Logging.DEBUG_FILE_CAM,
                                        true, true);
                        }

                    }
                }, 3000);
            }
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            super.onCameraUnavailable(cameraId);
            Log.d(TAG, "onCameraUnavailable: " + cameraId);
            if (DEBUG_LOG)
                Logging.appendLog("onCameraUnavailable", Logging.DEBUG_FILE_CAM, true, true);

            cameraAvailableList.put(cameraId, false);

            // wenn nicht im Init dann pausieren
            if (!initCamera) {
                // do the same as in onPause
                isPaused = true;
                closeCamera();
                stopBackgroundThread();
                handler.removeCallbacks(checkResult);
            }
        }
    };

    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate");
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(onRuntimeError);

        Logging.setLogPaths();

        if (DEBUG_LOG)
            Logging.appendLog("onCreate", Logging.DEBUG_FILE_CAM, true, true);

        cameraAvailableList = new HashMap<String, Boolean>();
        serviceOnline = true;
        init = true;
        initCamera = true;
        // Read in all cameraIDs and set their availability

        // REGISTER RECEIVER THAT HANDLES SCREEN ON AND SCREEN OFF LOGIC
        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mScreenStateReceiver = new ScreenReceiver();
        registerReceiver(mScreenStateReceiver, screenStateFilter);

        // GetSharedPrefs for Timers
        prefs = this.getSharedPreferences(
                Globals.PACKAGE_NAME, Context.MODE_PRIVATE);
        String key = Globals.PACKAGE_NAME + ".LAST_MS_LOCATIONLOG";
        LAST_MS_LOCATIONLOG = prefs.getLong(key, 0);
        key = Globals.PACKAGE_NAME + ".LAST_MS_WEATHERLOG";
        LAST_MS_WEATHERLOG = prefs.getLong(key, 0);
        key = Globals.PACKAGE_NAME + ".LAST_MS_USERDIALOG";
        LAST_MS_USERDIALOG = prefs.getLong(key, 0);
        Log.i(TAG, "LAST_MS_USERDIALOG=" + LAST_MS_USERDIALOG);

        //Camera stuff
        vImageView = MenuActivity.vImageView;
//        context = MenuActivity.context;

        startAvailabilityBackgroundThread();
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                cameraAvailableList.put(cameraId, true);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Logging.appendLog("onCreate ERROR " + e + "\t" + e.toString(), Logging.DEBUG_FILE_CAM, true, true);
        }
        manager.registerAvailabilityCallback(mAvailabilityCallback, mAvailabilityHandler);

        // requests always higher equal lollipop
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        }

        Log.i(TAG, "init openface");
        Wrapper.init(HEIGHT, WIDTH, getResources().getAssets());


        // Google Activity API
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mApiClient.connect();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        if (DEBUG_LOG)
            Logging.appendLog("onDestroy Service", Logging.DEBUG_FILE_CAM, true, true);
        super.onDestroy();
        closeCamera();
        handler.removeCallbacks(checkResult);
        stopBackgroundThread();
        stopAvailabilityBackgroundThread();
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (mAvailabilityCallback != null) {
            manager.unregisterAvailabilityCallback(mAvailabilityCallback);
            mAvailabilityCallback = null;
        }
        if (mScreenStateReceiver != null) {
            unregisterReceiver(mScreenStateReceiver);
            mScreenStateReceiver = null;
        }
        mBackgroundHandler = null;
    }

    protected void startBackgroundThread() {
        Log.e(TAG, "startBackgroundThread");
        if (DEBUG_LOG)
            Logging.appendLog("startBackgroundThread", Logging.DEBUG_FILE_CAM, true, true);
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        Log.e(TAG, "stopBackgroundThread");
        if (DEBUG_LOG)
            Logging.appendLog("stopBackgroundThread", Logging.DEBUG_FILE_CAM, true, true);
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
                Logging.appendLog("stopBackgroundThread ERROR " + e + "\t" + e.toString(), Logging.DEBUG_FILE_CAM, true, true);
            }
        }
    }

    protected void startAvailabilityBackgroundThread() {
        Log.e(TAG, "startAvailabilityBackgroundThread");
        if (DEBUG_LOG)
            Logging.appendLog("startAvailabilityBackgroundThread", Logging.DEBUG_FILE_CAM, true, true);
        mAvailabilityThread = new HandlerThread("Availability Background");
        mAvailabilityThread.start();
        mAvailabilityHandler = new Handler(mAvailabilityThread.getLooper());
    }

    protected void stopAvailabilityBackgroundThread() {
        Log.e(TAG, "stopAvailabilityBackgroundThread");
        if (DEBUG_LOG)
            Logging.appendLog("stopAvailabilityBackgroundThread", Logging.DEBUG_FILE_CAM, true, true);
        if (mAvailabilityThread != null) {
            mAvailabilityThread.quitSafely();
            try {
                mAvailabilityThread.join();
                mAvailabilityThread = null;
                mAvailabilityHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
                Logging.appendLog("startAvailabilityBackgroundThread ERROR " + e + "\t" + e.toString(), Logging.DEBUG_FILE_CAM, true, true);
            }
        }
    }

    protected void updatePreview() {
        Log.e(TAG, "updatePreview");
        if (DEBUG_LOG)
            Logging.appendLog("updatePreview", Logging.DEBUG_FILE_CAM, true, true);
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Logging.appendLog("updatePreview ERROR " + e + "\t" + e.toString(), Logging.DEBUG_FILE_CAM, true, true);
        }
    }

    private void closeCamera() {
        Log.e(TAG, "closeCamera");
        if (DEBUG_LOG)
            Logging.appendLog("closeCamera", Logging.DEBUG_FILE_CAM, true, true);
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand");
        if (DEBUG_LOG)
            Logging.appendLog("onStartCommand", Logging.DEBUG_FILE_CAM, true, true);
        SCREEN_ON = false;
        try {
            SCREEN_ON = intent.getBooleanExtra("screen_state", false);
        } catch (Exception ex) {
            Log.d(TAG, "Can't get screenon");
            if (DEBUG_LOG)
                Logging.appendLog("onStartCommand: Can't get screenon", Logging.DEBUG_FILE_CAM, true, true);
        }
        SCREEN_ON = GetScreenState();
        if (init) {
            SCREEN_ON = true;
            init = false;
        }
        if (!SCREEN_ON) {
            // OnPause
            if (DEBUG_LOG)
                Logging.appendLog("onStartCommand Screen OFF", Logging.DEBUG_FILE_CAM, true, true);
            Log.e(TAG, "onStartCommand Screen OFF");
            isPaused = true;
            stopBackgroundThread();
            stopAvailabilityBackgroundThread();
            closeCamera();
        } else {
            Log.e(TAG, "onStartCommand Screen ON");
            if (DEBUG_LOG)
                Logging.appendLog("onStartCommand Screen ON", Logging.DEBUG_FILE_CAM, true, true);
            // OnResume
            initCamera = true;
            Log.d(TAG, "Screen ON / OnResume");
//            Toast.makeText(this, "Hello", Toast.LENGTH_LONG).show();
            isPaused = false;
            startBackgroundThread();
            handler.postDelayed(checkResult, RESULTSPAN);
            openCamera();
            startAvailabilityBackgroundThread();
        }
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    private boolean GetScreenState() {
        boolean screenOn = false;
        DisplayManager dm = (DisplayManager) getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
        for (Display display : dm.getDisplays()) {
            if (display.getState() == Display.STATE_ON) {
                screenOn = true;
                break;
            }
        }
        return screenOn;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
//        throw new UnsupportedOperationException("Not yet implemented");
        return null;
    }

    protected void createCameraPreview() {
        Log.e(TAG, "createCameraPreview");
        if (DEBUG_LOG)
            Logging.appendLog("createCameraPreview", Logging.DEBUG_FILE_CAM, true, true);
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            cameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Logging.appendLog("createCameraPreview ERROR " + e + "\t" + e.toString(), Logging.DEBUG_FILE_CAM, true, true);
        }
    }

    private void openCamera() {
        initCamera = true;
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "openCamera()");
        if (DEBUG_LOG)
            Logging.appendLog("openCamera", Logging.DEBUG_FILE_CAM, true, true);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);
                // We only use a front facing camera
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (!(facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)) {
                    continue;
                }
                mImageReader = ImageReader.newInstance(WIDTH, HEIGHT,
                        ImageFormat.YUV_420_888, 1);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
                // ROBIN: In Service Permission check ?!
                // Add permission for camera and let user grant the permission
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MenuActivity.activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                    return;
                }
                manager.openCamera(cameraId, stateCallback, mAvailabilityHandler);
                mCameraId = cameraId;
                Log.e(TAG, "Using cameraID: " + mCameraId);
                initCamera = false;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Logging.appendLog("openCamera ERROR " + e + "\t" + e.toString(), Logging.DEBUG_FILE_CAM, true, true);
        } catch (NullPointerException e) {
            e.printStackTrace();
            Logging.appendLog("openCamera ERROR " + e + "\t" + e.toString(), Logging.DEBUG_FILE_CAM, true, true);
        }
    }

    private boolean AllCamerasAvailable(HashMap<String, Boolean> cameraAvailableList) {
        return !cameraAvailableList.containsValue(false);
    }

    private String HashMapToString(HashMap<String, Boolean> cameraAvailableList) {
        String result = "";
        for (Map.Entry entry : cameraAvailableList.entrySet()) {
            result += entry.getKey() + ": " + entry.getValue() + ", ";
        }
        if (result != "")
            result = result.substring(0, result.length() - 2);
        return result;
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image != null) {
                byte[] data = convertYUV420ToNV21_ALL_PLANES(image);
                byte[] data_rotated = rotateNV21_2(data, WIDTH, HEIGHT, 270);
                Wrapper.enqueue(data_rotated);
                image.close();
            }
        }
    };

    private byte[] convertYUV420ToNV21_ALL_PLANES(Image imgYUV420) {

        byte[] rez;
        //byte[] result = imgYUV420;

        ByteBuffer buffer0 = imgYUV420.getPlanes()[0].getBuffer();
        ByteBuffer buffer1 = imgYUV420.getPlanes()[1].getBuffer();
        ByteBuffer buffer2 = imgYUV420.getPlanes()[2].getBuffer();

        // actually here should be something like each second byte
        int buffer0_size = buffer0.remaining();
        int buffer1_size = 1;//buffer1.remaining();//buffer1.remaining(); // / 2 + 1;
        int buffer2_size = buffer2.remaining(); // / 2 + 1;

        byte[] buffer0_byte = new byte[buffer0_size];
        byte[] buffer1_byte = new byte[buffer1_size];
        byte[] buffer2_byte = new byte[buffer2_size];

        buffer0.get(buffer0_byte, 0, buffer0_size);
        buffer1.get(buffer1_byte, buffer1_size - 1, buffer1_size);
        buffer2.get(buffer2_byte, 0, buffer2_size);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // swap 1 and 2 as blue and red colors are swapped
            // kompletter buffer0
            outputStream.write(buffer0_byte);
//            // buffer 1 und 2 immer nur jeder 2.
//            for (int n=0;n<buffer1_size;n+=2)
//            {
//                outputStream.write((byte)buffer2_byte[n]);
//                outputStream.write((byte)buffer1_byte[n]);
//            }
            outputStream.write(buffer2_byte);
            outputStream.write(buffer1_byte);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        rez = outputStream.toByteArray();

        return rez;
    }

    public static byte[] rotateNV21_2(final byte[] yuv,
                                      final int width,
                                      final int height,
                                      final int rotation) {
        if (rotation == 0) return yuv;
        if (rotation % 90 != 0 || rotation < 0 || rotation > 270) {
            throw new IllegalArgumentException("0 <= rotation < 360, rotation % 90 == 0");
        }

        final byte[] output = new byte[yuv.length];
        final int frameSize = width * height;
        final boolean swap = rotation % 180 != 0;
        final boolean xflip = rotation % 270 != 0;
        final boolean yflip = rotation >= 180;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                final int yIn = j * width + i;
                final int uIn = frameSize + (j >> 1) * width + (i & ~1);
                final int vIn = uIn + 1;

                final int wOut = swap ? height : width;
                final int hOut = swap ? width : height;
                final int iSwapped = swap ? j : i;
                final int jSwapped = swap ? i : j;
                final int iOut = xflip ? wOut - iSwapped - 1 : iSwapped;
                final int jOut = yflip ? hOut - jSwapped - 1 : jSwapped;

                final int yOut = jOut * wOut + iOut;
                final int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
                final int vOut = uOut + 1;

                output[yOut] = (byte) (0xff & yuv[yIn]);
                output[uOut] = (byte) (0xff & yuv[uIn]);
                output[vOut] = (byte) (0xff & yuv[vIn]);
            }
        }
        return output;
    }
}
