package com.example.test.openfaceandroid;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.res.Resources;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */

public class ActivityRecognizedService extends IntentService {

    protected static final String TAG = "ActivityRecogition";

//    static String LOG_FILE_ACT = null;

    public ActivityRecognizedService() {
        super("ActivityRecognizedService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
//        if (LOG_FILE_ACT == null)
//        {
//            LOG_FILE_ACT = intent.getStringExtra(Constants.LOG_FILE_ACT);
//        }
        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
//        Intent localIntent = new Intent(Constants.BROADCAST_ACTION);

        ArrayList<DetectedActivity> googleActivities = new ArrayList<>();
        // Get the list of the probable activities associated with the current state of the
        // device. Each activity is associated with a confidence level, which is an int between
        // 0 and 100.
        ArrayList<DetectedActivity> detectedActivities = (ArrayList) result.getProbableActivities();

        // Log each activity.
        Log.i(TAG, "activities detected");
        HashMap<Integer, Integer> detectedActivitiesMap = new HashMap<>();
        for (DetectedActivity activity : detectedActivities) {
            detectedActivitiesMap.put(activity.getType(), activity.getConfidence());
        }
        // Every time we detect new activities, we want to reset the confidence level of ALL
        // activities that we monitor. Since we cannot directly change the confidence
        // of a DetectedActivity, we use a temporary list of DetectedActivity objects. If an
        // activity was freshly detected, we use its confidence level. Otherwise, we set the
        // confidence level to zero.
        ArrayList<DetectedActivity> tempList = new ArrayList<>();
        for (int i = 0; i < Constants.MONITORED_ACTIVITIES.length; i++) {
            int confidence = detectedActivitiesMap.containsKey(Constants.MONITORED_ACTIVITIES[i]) ?
                    detectedActivitiesMap.get(Constants.MONITORED_ACTIVITIES[i]) : 0;

            tempList.add(new DetectedActivity(Constants.MONITORED_ACTIVITIES[i],
                    confidence));
        }

        // Remove all items.
        googleActivities.clear();

        // Adding the new list items notifies attached observers that the underlying data has
        // changed and views reflecting the data should refresh.
        for (DetectedActivity detectedActivity: tempList) {
            googleActivities.add(detectedActivity);
        }

        for (DetectedActivity da: detectedActivities) {
            Log.i(TAG, da.getType() + " " + da.getConfidence() + "%"
            );
        }

        String logConsole = "";
        for (DetectedActivity da: googleActivities) {
            logConsole += da.getType() + " " + da.getConfidence() + "%\n";
        }
        Log.i(TAG, logConsole);
        // Log Activities
        String logRes = "";
        for (DetectedActivity da: googleActivities)
        {
            logRes += da.getConfidence() + ";";
        }
        if (logRes.length() > 0)
            logRes = logRes.substring(0, logRes.length()-1);
        Logging.appendLog(logRes, Logging.LOG_FILE_ACT, true, true);

    }

//    public void appendLog(String text, String file, boolean append, boolean timestamp)
//    {
//        File logFile = new File(file);
//        if (!logFile.exists())
//        {
//            try
//            {
//                File folder = new File(logFile.getParent());
//                if (!folder.exists()) {
//                    folder.mkdir();
//                }
//                logFile.createNewFile();
//                BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, append));
//                buf.append("Init log " + "\n\n");
//                buf.append("Time;STILL;ON_FOOT;WALKING;RUNNING;ON_BICYCLE;IN_VEHICLE;TILTING;UNKNOWN");
//                buf.newLine();
//                buf.close();
//            }
//            catch (IOException e)
//            {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            }
//        }
//        try
//        {
//            //BufferedWriter for performance, true to set append to file flag
//            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, append));
//            if (timestamp)
//            {
//                String ts = Globals.GetCurrentTimeStamp(true);
//                buf.append(ts + "," + text);
//            }
//            else
//                buf.append(text);
//            buf.newLine();
//            buf.close();
//        }
//        catch (IOException e)
//        {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }
}
