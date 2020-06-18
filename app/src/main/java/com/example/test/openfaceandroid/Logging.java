package com.example.test.openfaceandroid;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static com.example.test.openfaceandroid.Globals.GetCurrentTimeStamp;

/**
 * Created by Robin on 30.07.2017.
 */

public class Logging {

    public static String LOG_PATH = "";
    public static String LOG_FILE_CAM = "";
    public static String DEBUG_FILE_CAM = "";
    public static String LOG_FILE_ACT = "";
    public static String LOG_FILE_WEATHER = "";
    public static String LOG_FILE_USER_EMOTIONRATING = "";

    public static void setLogPaths()
    {
        // Timestamp ohne Uhrzeit => nur Datum => pro Tag max. 1 Logfile
        String timestamp = GetCurrentTimeStamp(false);
        String extStore = System.getenv("EXTERNAL_STORAGE");
        LOG_PATH = extStore + "/OpenFaceAndroid";
        LOG_FILE_CAM = LOG_PATH + "/OFA_CAM_" + timestamp + ".log";
        DEBUG_FILE_CAM = LOG_PATH + "/OFA_CAM_DEBUG_" + timestamp + ".log";
        LOG_FILE_ACT = LOG_PATH + "/OFA_ACT_" + timestamp + ".log";
        LOG_FILE_WEATHER = LOG_PATH + "/OFA_WEATHER_" + timestamp + ".log";
        LOG_FILE_USER_EMOTIONRATING = LOG_PATH + "/OFA_USEREMOTRATING_" + timestamp + ".log";
    }

    public static void appendLog(String text, String file) {
        appendLog(text, file, true, true);
    }

    public static void appendLog(String text, String file, boolean append, boolean timestamp) {
        if (file == null)
            setLogPaths();
        File logFile = new File(file);
        String ts = GetCurrentTimeStamp(true);
        if (!logFile.exists()) {
            try {
                File folder = new File(LOG_PATH);
                if (!folder.exists()) {
                    folder.mkdir();
                }
                logFile.createNewFile();
                BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, append));
                buf.append(ts + ": Init log " + "\n\n");
                if (file == LOG_FILE_CAM)
                {
                    buf.append("Time;Anger;Disgust;Fear;Happiness;Neutral;Sad;Surprised;Attention;"
                            + "app;long;lat"); // ;Weather;Temp

                }
                else if (file == LOG_FILE_ACT)
                {
                    buf.append("Time;STILL;ON_FOOT;WALKING;RUNNING;ON_BICYCLE;IN_VEHICLE;TILTING;UNKNOWN");

                }
                else if (file == LOG_FILE_USER_EMOTIONRATING)
                {
                    buf.append("Time;Anger;Disgust;Fear;Happiness;Neutral;Sad;Surprised");
                }
                else if (file == LOG_FILE_WEATHER)
                {
                    buf.append("Time;Weather;Temp;CountryCode;postalCode;locality;subLocality;thoroughfare;subThoroughfare;feature");
                }
                buf.newLine();
                buf.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, append));
            if (timestamp) {
                buf.append(ts + ";" + text);
            } else
                buf.append(text);
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
