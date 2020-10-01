package com.example.test.openfaceandroid;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import zh.wang.android.yweathergetter4a.WeatherInfo;
import zh.wang.android.yweathergetter4a.YahooWeather;
import zh.wang.android.yweathergetter4a.YahooWeatherInfoListener;

public class MenuActivity extends AppCompatActivity {

    public static Activity activity;
    public static Context context;
    public static ImageView vImageView;
    TextView txtCameraServiceStatus;
    Button btnStartService;
    Button btnStopService;
    private final static int WIDTH = 640;
    private final static int HEIGHT = 480;
    private final static int PIXELBYTES = 4;
    private final static int BYTESIZE = WIDTH * HEIGHT * PIXELBYTES;
    private int LOG_INTERVAL = 990;
    private final static int PERMISSION_REQUEST = 1;
    //    private final static Handler handler = new Handler(Looper.getMainLooper());
    private final static Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    private final static byte[][] buffers = new byte[2][BYTESIZE];

    private static boolean isPaused;
    private static boolean hasPermissions;

    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private Thread.UncaughtExceptionHandler onRuntimeError = new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(Thread thread, Throwable ex) {
            //Try starting the Activity again
            Logging.appendLog("UncaughtExceptionHandler ERROR in ACTIVITY, Thread: " + thread.getName() + "\tE:" + ex
                    + "\tEx:" + ex.toString(), Logging.DEBUG_FILE_CAM, true, true);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Logging.appendLog("UncaughtExceptionHandler ERROR RestartACTIVITY"
                            , Logging.DEBUG_FILE_CAM, true, true);
                    startService(new Intent(MenuActivity.this, CameraService.class));
                    RefreshServiceGUIInformation();
                }
            }, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        Thread.setDefaultUncaughtExceptionHandler(onRuntimeError);

        hasPermissions = false;
        this.activity = this;

        context = this.getApplicationContext();
        // don't sleep/suspend while in player activity
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        vImageView = (ImageView) findViewById(R.id.frame);
        txtCameraServiceStatus = (TextView) findViewById(R.id.txtCamServiceStatus);
        btnStartService = (Button) findViewById(R.id.btnStartService);
        btnStopService = (Button) findViewById(R.id.btnStopService);

        RefreshServiceGUIInformation();

        // Check if UserStats are enabled:
        UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time);
        if (stats == null || stats.isEmpty()) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        } else
            hasPermissions = true;


        if (!hasPermissions(MenuActivity.this, permissions)) {
            ActivityCompat.requestPermissions(MenuActivity.this, permissions, REQUEST_CAMERA_PERMISSION);
        }
    }

    String permissions[] = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    };

    public static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        RefreshServiceGUIInformation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MenuActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                //initCamera();
                finish();
            } else
                Wrapper.init(WIDTH, HEIGHT, getResources().getAssets());
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void btnStartService_onClick(View v) {

        SharedPreferences prefs = prefs = this.getSharedPreferences(
                Globals.PACKAGE_NAME, Context.MODE_PRIVATE);
        String key = Globals.PACKAGE_NAME + ".STUDY_UID";
        String uniqueID = prefs.getString(key, "");
        if (uniqueID == "")
        {
            long number = (long) Math.floor(Math.random() * 90_000_000L) + 10_000_000L;
            prefs.edit().putString(key, String.valueOf(number)).apply();
        }
        // generate unique ID
        key = Globals.PACKAGE_NAME + ".STUDY_START";
        String studyStart = prefs.getString(key, "");
        if (studyStart == "")
        {
            String timeStamp = Globals.GetCurrentTimeStamp(true);
            prefs.edit().putString(key, timeStamp).apply();
        }
        startService(new Intent(MenuActivity.this, CameraService.class));
        Toast.makeText(this, "Start", Toast.LENGTH_SHORT).show();
        RefreshServiceGUIInformation();
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                finish();
//            }
//        }, 5000);
    }

    private void RefreshServiceGUIInformation() {
        boolean camServiceOnline = isMyServiceRunning(CameraService.class);
        if (camServiceOnline) {
            txtCameraServiceStatus.setText("Online");
            btnStartService.setEnabled(false);
            btnStopService.setEnabled(true);
        } else {
            txtCameraServiceStatus.setText("Offline");
            btnStartService.setEnabled(true);
            btnStopService.setEnabled(false);
        }
        SharedPreferences prefs = prefs = this.getSharedPreferences(
                Globals.PACKAGE_NAME, Context.MODE_PRIVATE);
        String key = Globals.PACKAGE_NAME + ".STUDY_UID";
        String study_uid = prefs.getString(key, "");
        if (study_uid != "")
        {
            TextView txtView = (TextView) findViewById(R.id.txtStudyID);
            txtView.setText(study_uid);
        }

        key = Globals.PACKAGE_NAME + ".STUDY_START";
        String studyStart = prefs.getString(key, "");
        if (studyStart != "")
        {
            TextView txtView = (TextView) findViewById(R.id.txtStudyStart);
            txtView.setText(studyStart);
            if (!camServiceOnline)
            {
                btnStartService.setText("Continue Study");
            }
        }
    }

    public void btnStopService_onClick(View v) {
        // Set an EditText view to get user input
        final EditText input = new EditText(MenuActivity.this);

        new AlertDialog.Builder(MenuActivity.this)
                .setTitle("Studie Stoppen")
                .setMessage("Bitte Passwort eingeben")
                .setView(input)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable text = input.getText();
                        if (text.toString().equals("FinishStudy")) {
                            stopService(new Intent(MenuActivity.this, CameraService.class));

                            Toast.makeText(MenuActivity.this, "Stop", Toast.LENGTH_SHORT).show();
                            RefreshServiceGUIInformation();
                        } else
                            Toast.makeText(MenuActivity.this, "Falsches Passwort", Toast.LENGTH_SHORT).show();

                        // deal with the editable
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Do nothing.
                    }
                }).show();
    }
}
