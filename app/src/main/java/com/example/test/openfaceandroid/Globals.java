package com.example.test.openfaceandroid;

import android.content.Context;
import android.location.Location;
import android.preference.PreferenceManager;

import java.util.Date;
import java.util.List;

import static com.example.test.openfaceandroid.Constants.KEY_LOCATION_UPDATES_RESULT;

/**
 * Created by Robin on 26.07.2017.
 */

public class Globals {

    public final static String PACKAGE_NAME = "com.example.test.openfaceandroid";

    public static String GetCurrentTimeStamp(boolean dayTime) {
        java.util.GregorianCalendar gregorianCalendar = new java.util.GregorianCalendar();
        Date date = gregorianCalendar.getTime();

        java.text.SimpleDateFormat sdf = dayTime
                ? new java.text.SimpleDateFormat("yyyy-MM-dd HHmmss")
                : new java.text.SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(date);
    }

    static void setLocationUpdatesResult(Context context, List<Location> locations) {
        StringBuilder sb = new StringBuilder();
        for (Location location : locations) {
            sb.append("(");
            sb.append(location.getLatitude());
            sb.append(", ");
            sb.append(location.getLongitude());
            sb.append(")");
            sb.append("\n");
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KEY_LOCATION_UPDATES_RESULT,
                        sb.toString())
                .apply();
    }

    static String getLocationUpdatesResult(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KEY_LOCATION_UPDATES_RESULT, "");
    }
}
