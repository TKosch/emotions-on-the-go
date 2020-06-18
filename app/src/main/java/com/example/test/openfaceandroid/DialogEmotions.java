package com.example.test.openfaceandroid;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.RatingBar;
import android.widget.Switch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class DialogEmotions extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog_emotions);
    }

    public void btnSubmit_onClick(View v) {
//        //Globals.LOG_FILE_USER_EMOTIONRATING = System.getenv("EXTERNAL_STORAGE") + "/OpenFaceAndroid" + "/OFA_USEREMOTRATING_TEST.log";
        Switch swAngry = (Switch) findViewById(R.id.swAngry);
        Switch swDigust = (Switch) findViewById(R.id.swDisgusted);
        Switch swFeared = (Switch) findViewById(R.id.swFeared);
        Switch swHappy = (Switch) findViewById(R.id.swHappy);
        Switch swNeutral = (Switch) findViewById(R.id.swNeutral);
        Switch swSad = (Switch) findViewById(R.id.swSad);
        Switch swSurprised = (Switch) findViewById(R.id.swSurprised);

        int ratingAngry = swAngry.isChecked() ? 1 : 0;
        int ratingDisgust = swDigust.isChecked() ? 1 : 0;
        int ratingFeared = swFeared.isChecked() ? 1 : 0;
        int ratingHappy = swHappy.isChecked() ? 1 : 0;
        int ratingNeutral = swNeutral.isChecked() ? 1 : 0;
        int ratingSad = swSad.isChecked() ? 1 : 0;
        int ratingSurprised = swSurprised.isChecked() ? 1 : 0;
        String logRes = ratingAngry + ";"
                        + ratingDisgust + ";"
                        + ratingFeared + ";"
                        + ratingHappy + ";"
                        + ratingNeutral + ";"
                        + ratingSad + ";"
                        + ratingSurprised;

        Logging.appendLog(logRes, Logging.LOG_FILE_USER_EMOTIONRATING, true, true);
        finish();
//        Intent startMain = new Intent(Intent.ACTION_MAIN);
//        startMain.addCategory(Intent.CATEGORY_HOME);
//        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(startMain);
    }


//    public void appendLog(String text, String file, boolean append, boolean timestamp) {
//        File logFile = new File(file);
//        if (!logFile.exists()) {
//            try {
//                File folder = new File(logFile.getParent());
//                if (!folder.exists()) {
//                    folder.mkdir();
//                }
//                logFile.createNewFile();
//                BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, append));
//                buf.append("Init log " + "\n\n");
//                buf.append("Time;Anger;Disgust;Fear;Happiness;Neutral;Sad;Surprised");
//                buf.newLine();
//                buf.close();
//            } catch (IOException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            }
//        }
//        try {
//            //BufferedWriter for performance, true to set append to file flag
//            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, append));
//            if (timestamp) {
//                String ts = Globals.GetCurrentTimeStamp(true);
//                buf.append(ts + ";" + text);
//            } else
//                buf.append(text);
//            buf.newLine();
//            buf.close();
//        } catch (IOException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }
}
